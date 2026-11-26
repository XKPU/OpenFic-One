"""把 OF-One 的「One 设置」入口注入已安装的 OpenFic 前端。"""
import glob
import os
import shutil
import sys

BRIDGE = "ofone-bridge.js"
MARK = 'ofone-bridge.js'


def find_frontend():
    pats = [
        "/opt/venv/lib/python*/site-packages/frontend",
        "/opt/venv/lib/python*/site-packages/app/frontend",
    ]
    for p in pats:
        hits = sorted(glob.glob(p))
        for h in hits:
            if os.path.isfile(os.path.join(h, "index.html")):
                return h
    return None


def patch_index(fe):
    idx = os.path.join(fe, "index.html")
    with open(idx, "r", encoding="utf-8") as f:
        html = f.read()
    if MARK in html:
        return False
    tag = '<script defer src="/' + BRIDGE + '"></script>'
    if "</body>" in html:
        html = html.replace("</body>", tag + "\n</body>", 1)
    else:
        html += tag
    with open(idx, "w", encoding="utf-8") as f:
        f.write(html)
    return True


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "/mnt/share/ofone-bridge.js"
    fe = find_frontend()
    if not fe:
        print("未找到 OpenFic 前端目录，跳过注入")
        return 1
    dst = os.path.join(fe, BRIDGE)
    if os.path.isfile(src):
        shutil.copyfile(src, dst)
    elif not os.path.isfile(dst):
        print("缺少 " + BRIDGE)
        return 1
    changed = patch_index(fe)
    print("注入完成: " + fe + (" (已更新 index.html)" if changed else " (已存在)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
