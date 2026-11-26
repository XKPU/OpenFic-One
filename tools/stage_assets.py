import io, lzma, os, tarfile, gzip, zstandard, shutil
BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DL = os.path.join(BASE, "dl")
ASSETS = os.path.join(BASE, "app", "assets")
os.makedirs(ASSETS, exist_ok=True)

def deb_data(path):
    data = open(path, "rb").read()
    pos = 8
    while pos + 60 <= len(data):
        name = data[pos:pos+16].decode("ascii","replace").strip()
        size = int(data[pos+48:pos+58].decode("ascii","replace").strip() or 0)
        body = data[pos+60:pos+60+size]
        if name.startswith("data.tar"):
            if name.endswith(".zst"):
                return zstandard.ZstdDecompressor().decompress(body, max_output_size=1<<28)
            if name.endswith(".xz"):
                return lzma.decompress(body)
            if name.endswith(".gz"):
                return gzip.decompress(body)
            return body
        pos += 60 + size + (size % 2)
    raise RuntimeError("no data.tar")

want = {
    "proot_aarch64.deb": {"bin/proot": "bin/proot", "libexec/proot/loader": "bin/libexec/loader",
                          "libexec/proot/loader32": "bin/libexec/loader32"},
    "libandroid-shmem_aarch64.deb": {"lib/libandroid-shmem.so": "bin/libandroid-shmem.so"},
    "libtalloc_aarch64.deb": {"lib/libtalloc.so.2.5.0": "bin/libtalloc.so.2"},
}
for fn, mapping in want.items():
    raw = deb_data(os.path.join(DL, fn))
    with tarfile.open(fileobj=io.BytesIO(raw)) as tf:
        members = {m.name.lstrip("./").replace("data/data/com.termux/files/usr/", ""): m for m in tf.getmembers()}
        for src, dst in mapping.items():
            m = members.get(src)
            if m is None:
                print("MISS", fn, src); continue
            if m.islnk() or m.issym():
                real = members.get(m.linkname.lstrip("./").replace("data/data/com.termux/files/usr/", ""))
                m = real or m
            out = os.path.join(ASSETS, dst)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with tf.extractfile(m) as f, open(out, "wb") as o:
                shutil.copyfileobj(f, o)
            print("staged", dst, os.path.getsize(out))

for stale in ("rootfs.tar.gz", "rootfs.tar"):
    p = os.path.join(ASSETS, stale)
    if os.path.exists(p):
        os.remove(p)
shutil.copyfile(os.path.join(BASE, "out", "openfic-rootfs.tar.gz"),
                os.path.join(ASSETS, "rootfs.tar"))
print("rootfs staged", os.path.getsize(os.path.join(ASSETS, "rootfs.tar")))

share_src = os.path.join(BASE, "tools", "share")
share_dst = os.path.join(ASSETS, "share")
os.makedirs(share_dst, exist_ok=True)
for name in sorted(os.listdir(share_src)):
    src = os.path.join(share_src, name)
    if not os.path.isfile(src):
        continue
    shutil.copyfile(src, os.path.join(share_dst, name))
    print("share staged", name, os.path.getsize(os.path.join(share_dst, name)))
