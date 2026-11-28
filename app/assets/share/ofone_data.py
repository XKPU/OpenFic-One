"""导出 / 导入 OpenFic 数据目录（支持 zip / tar.gz / tgz）。"""
import os
import shutil
import sys
import zipfile
import tarfile

DATA = "/root/.openfic"
CACHE = ("lancedb", "fastembed_cache", "runtime")
DBS = ("openfic.db", "checkpoints.db")
TMP = "/tmp/ofone-data"


def copy_db(src, dst):
    try:
        import sqlite3
        con = sqlite3.connect(src)
        try:
            out = sqlite3.connect(dst)
            try:
                con.backup(out)
            finally:
                out.close()
        finally:
            con.close()
        return
    except Exception as exc:
        print("sqlite backup 失败，改为直接复制: %s" % exc)
    for suffix in ("", "-wal", "-shm"):
        p = src + suffix
        if os.path.isfile(p):
            shutil.copy2(p, dst + suffix)


def is_db_file(name):
    return name in DBS or name.startswith("openfic.db-") or name.startswith("checkpoints.db-")


def _is_tar(path):
    low = path.lower()
    return low.endswith(".tar.gz") or low.endswith(".tgz") or low.endswith(".tar")


def _collect():
    """返回 [(绝对路径, 归档内相对名)]，DBS 用 TMP 下的备份副本。"""
    entries = []
    for name in DBS:
        p = os.path.join(TMP, name)
        if os.path.isfile(p):
            entries.append((p, name))
    for root, dirs, files in os.walk(DATA):
        rel = os.path.relpath(root, DATA)
        if rel == ".":
            dirs[:] = [d for d in dirs if d not in CACHE]
        for f in files:
            if rel == "." and is_db_file(f):
                continue
            arc = os.path.normpath(os.path.join(rel, f))
            entries.append((os.path.join(root, f), arc))
    return entries


def do_export(target):
    if not os.path.isdir(DATA):
        print("OFONE_EXPORT_ERROR=数据目录不存在: " + DATA)
        return 2
    shutil.rmtree(TMP, ignore_errors=True)
    os.makedirs(TMP)
    for name in DBS:
        p = os.path.join(DATA, name)
        if os.path.isfile(p):
            copy_db(p, os.path.join(TMP, name))
    parent = os.path.dirname(target)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent)
    if os.path.exists(target):
        os.remove(target)
    entries = _collect()
    items = 0
    if _is_tar(target):
        with tarfile.open(target, "w:gz") as t:
            for full, arc in entries:
                t.add(full, arcname=arc)
                items += 1
    else:
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
            for full, arc in entries:
                z.write(full, arc)
                items += 1
    shutil.rmtree(TMP, ignore_errors=True)
    print("OFONE_EXPORT_ITEMS=%d" % items)
    print("OFONE_EXPORT_SIZE=%d" % os.path.getsize(target))
    print("OFONE_EXPORT=%s" % target)
    return 0


def do_import(src):
    if not os.path.isfile(src):
        print("OFONE_IMPORT_ERROR=找不到备份文件")
        return 2
    try:
        if _is_tar(src):
            handle = tarfile.open(src, "r:gz" if src.lower().endswith((".tar.gz", ".tgz")) else "r:")
            names = [m.name for m in handle.getmembers() if m.isfile()]
            opener = handle.extractfile
        else:
            handle = zipfile.ZipFile(src)
            names = [n for n in handle.namelist() if not n.endswith("/")]
            opener = handle.open
        try:
            prefix = ""
            if "openfic.db" not in names:
                roots = set(n.split("/")[0] for n in names if "/" in n)
                if len(roots) == 1:
                    cand = roots.pop() + "/"
                    if cand + "openfic.db" in names:
                        prefix = cand
            if prefix + "openfic.db" not in names:
                print("OFONE_IMPORT_ERROR=压缩包里没有 openfic.db，不是有效备份")
                return 3
            bak = DATA + ".bak"
            if os.path.exists(bak):
                shutil.rmtree(bak, ignore_errors=True)
            if os.path.isdir(DATA):
                shutil.move(DATA, bak)
            os.makedirs(DATA, exist_ok=True)
            # runtime 不在备份内，导入后从旧目录恢复，否则服务无法启动
            old_runtime = os.path.join(bak, "runtime")
            if os.path.isdir(old_runtime):
                shutil.move(old_runtime, os.path.join(DATA, "runtime"))
            count = 0
            for n in names:
                if prefix and not n.startswith(prefix):
                    continue
                rel = n[len(prefix):]
                if not rel or rel.startswith("/") or ".." in rel.split("/"):
                    continue
                dst = os.path.join(DATA, rel)
                parent = os.path.dirname(dst)
                if parent and not os.path.isdir(parent):
                    os.makedirs(parent)
                stream = opener(n)
                if stream is None:
                    continue
                with stream as s, open(dst, "wb") as o:
                    shutil.copyfileobj(s, o)
                count += 1
        finally:
            try:
                handle.close()
            except Exception:
                pass
    except (zipfile.BadZipFile, tarfile.TarError) as exc:
        print("OFONE_IMPORT_ERROR=无法读取压缩包: %s" % exc)
        return 4
    print("OFONE_IMPORT_FILES=%d" % count)
    print("OFONE_IMPORT_OK=1")
    return 0


def main():
    if len(sys.argv) < 3:
        print("用法: ofone_data.py export|import <archive>")
        return 2
    if sys.argv[1] == "export":
        return do_export(sys.argv[2])
    if sys.argv[1] == "import":
        return do_import(sys.argv[2])
    print("未知操作: " + sys.argv[1])
    return 2


if __name__ == "__main__":
    sys.exit(main())