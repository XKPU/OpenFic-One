import gzip, os, shutil, sys, tarfile

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, "dl", "ubuntu-base-24.04.5-base-arm64.tar.gz")
TMP = os.path.join(BASE, "tmp")
OUT = os.path.join(BASE, "out")
os.makedirs(TMP, exist_ok=True)
os.makedirs(OUT, exist_ok=True)
flat = os.path.join(TMP, "base.tar")

if not os.path.exists(flat):
    with gzip.open(SRC, "rb") as fi, open(flat, "wb") as fo:
        shutil.copyfileobj(fi, fo, 1 << 20)
    print("decompressed", os.path.getsize(flat))

SKIP_TOP = ("dev/",)

def emit(tf_in, tf_out, index, seen):
    count = 0
    for m in tf_in:
        name = m.name.lstrip("./")
        if not name:
            continue
        if any(name == s.rstrip("/") or name.startswith(s) for s in SKIP_TOP):
            continue
        if m.isdir():
            ti = tarfile.TarInfo(name)
            ti.type = tarfile.DIRTYPE
            ti.mode = m.mode
            tf_out.addfile(ti)
            count += 1
            continue
        if m.issym():
            ti = tarfile.TarInfo(name)
            ti.type = tarfile.SYMTYPE
            ti.linkname = m.linkname
            ti.mode = m.mode
            tf_out.addfile(ti)
            count += 1
            continue
        if m.islnk():
            tgt = index.get(m.linkname.lstrip("./"))
            if tgt is None:
                print("missing hardlink target", m.name, "->", m.linkname)
                continue
            src = tf_in.extractfile(tgt)
            if src is None:
                continue
            ti = tarfile.TarInfo(name)
            ti.size = tgt.size
            ti.mode = m.mode or tgt.mode
            ti.mtime = tgt.mtime
            tf_out.addfile(ti, src)
            count += 1
            continue
        if not m.isreg():
            continue
        f = tf_in.extractfile(m)
        if f is None:
            continue
        ti = tarfile.TarInfo(name)
        ti.size = m.size
        ti.mode = m.mode
        ti.mtime = m.mtime
        tf_out.addfile(ti, f)
        count += 1
    return count

index = {}
with tarfile.open(flat, "r:") as tf:
    for m in tf.getmembers():
        index[m.name.lstrip("./")] = m
print("index", len(index))

out_path = os.path.join(OUT, "base-rootfs.tar.gz")
with tarfile.open(out_path, "w:gz", compresslevel=6) as tf_out:
    with tarfile.open(flat, "r:") as tf_in:
        n = emit(tf_in, tf_out, index, set())
print("written", n, "entries", os.path.getsize(out_path))
