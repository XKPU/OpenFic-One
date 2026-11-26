import gzip, io, lzma, os, shutil, tarfile

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(BASE, "tmp")
DEBS = os.path.join(BASE, "dl", "debs")
OUT = os.path.join(BASE, "out")
BLOBS = os.path.join(TMP, "blobs")
os.makedirs(OUT, exist_ok=True)
if os.path.isdir(BLOBS):
    shutil.rmtree(BLOBS)
os.makedirs(BLOBS)

entries = {}
blob_n = [0]

def put_file(name, mode, mtime, data):
    blob_n[0] += 1
    p = os.path.join(BLOBS, str(blob_n[0]))
    with open(p, "wb") as f:
        f.write(data)
    entries[name] = {"t": "f", "mode": mode, "mtime": mtime, "blob": p, "size": len(data)}

def apply_tar(tf, label):
    members = tf.getmembers()
    byname = {}
    for m in members:
        byname[m.name.lstrip("./")] = m
    n = 0
    for m in members:
        name = m.name.lstrip("./")
        if not name or name == ".":
            continue
        if name.startswith("dev/") or name == "dev":
            continue
        if m.isdir():
            entries[name] = {"t": "d", "mode": m.mode & 0o7777}
        elif m.issym():
            entries[name] = {"t": "l", "link": m.linkname}
        elif m.islnk():
            tgt = byname.get(m.linkname.lstrip("./"))
            src = entries.get(m.linkname.lstrip("./"))
            if src and src["t"] == "f":
                put_file(name, m.mode & 0o7777, m.mtime, open(src["blob"], "rb").read())
            elif src and src["t"] == "l":
                entries[name] = {"t": "l", "link": src["link"]}
        elif m.isreg():
            f = tf.extractfile(m)
            if f is not None:
                put_file(name, m.mode & 0o7777, m.mtime, f.read())
        n += 1
    print(label, n)

def deb_data(path):
    data = open(path, "rb").read()
    pos = 8
    while pos + 60 <= len(data):
        name = data[pos:pos+16].decode("ascii", "replace").strip()
        size = int(data[pos+48:pos+58].decode("ascii", "replace").strip() or 0)
        body = data[pos+60:pos+60+size]
        if name.startswith("data.tar"):
            if name.endswith(".xz"):
                return lzma.decompress(body)
            if name.endswith(".gz"):
                return gzip.decompress(body)
            if name.endswith(".zst"):
                import zstandard
                return zstandard.ZstdDecompressor().decompress(body, max_output_size=1 << 31)
            return body
        pos += 60 + size + (size % 2)
    raise RuntimeError("no data.tar")

with tarfile.open(os.path.join(OUT, "base-rootfs.tar.gz"), "r:gz") as tf:
    apply_tar(tf, "base")

for fn in sorted(os.listdir(DEBS)):
    try:
        raw = deb_data(os.path.join(DEBS, fn))
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:*") as tf:
            apply_tar(tf, "deb " + fn)
    except Exception as e:
        print("FAIL", fn, type(e).__name__, str(e)[:80])

# drop docs / apt lists
drop = [k for k in entries if k.startswith(("var/lib/apt/lists", "var/cache/apt", "usr/share/doc/", "usr/share/man/"))]
for k in drop:
    del entries[k]
print("dropped", len(drop))

for name, content in [
    ("etc/resolv.conf", "nameserver 223.5.5.5\n"),
    ("etc/apt/sources.list", "deb http://mirrors.aliyun.com/ubuntu-ports noble main universe\ndeb http://mirrors.aliyun.com/ubuntu-ports noble-updates main universe\n"),
    ("etc/hostname", "openfic\n"),
]:
    put_file(name, 0o644, 0, content.encode())

entries["usr/bin/python3"] = {"t": "l", "link": "python3.12"}
entries["usr/bin/python"] = {"t": "l", "link": "python3.12"}

certs = sorted(k for k in entries
               if k.startswith("usr/share/ca-certificates/mozilla/") and k.endswith(".crt"))
bundle = bytearray()
for k in certs:
    e = entries[k]
    if e["t"] != "f":
        continue
    with open(e["blob"], "rb") as f:
        bundle += f.read()
    if not bundle.endswith(b"\n"):
        bundle += b"\n"
entries["etc/ssl/certs"] = {"t": "d", "mode": 0o755}
put_file("etc/ssl/certs/ca-certificates.crt", 0o644, 0, bytes(bundle))
print("ca bundle", len(bundle), "bytes from", len(certs), "certs")

out_path = os.path.join(OUT, "openfic-rootfs.tar.gz")
with tarfile.open(out_path, "w:gz", compresslevel=6) as tf:
    for name in sorted(entries):
        e = entries[name]
        ti = tarfile.TarInfo(name)
        if e["t"] == "d":
            ti.type = tarfile.DIRTYPE
            ti.mode = e["mode"]
            tf.addfile(ti)
        elif e["t"] == "l":
            ti.type = tarfile.SYMTYPE
            ti.linkname = e["link"]
            ti.mode = 0o777
            tf.addfile(ti)
        else:
            ti.size = e["size"]
            ti.mode = e["mode"]
            ti.mtime = e.get("mtime") or 0
            with open(e["blob"], "rb") as f:
                tf.addfile(ti, f)
print("built", out_path, os.path.getsize(out_path), "entries", len(entries))
