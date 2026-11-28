"""把 OF-One 的「One 设置」入口与系统通知兼容层注入已安装的 OpenFic 前端。"""
import glob
import hashlib
import os
import re
import shutil
import sys

BRIDGE = "ofone-bridge.js"
NOTIFY = "ofone-notify.js"
MARK = "ofone-bridge.js"


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


def _ver(path):
    # 用内容摘要做版本号，文件一变 URL 就变，绕开前端 Service Worker 的 cache-first 缓存。
    try:
        with open(path, "rb") as f:
            return hashlib.md5(f.read()).hexdigest()[:10]
    except OSError:
        return "0"


def patch_index(fe):
    idx = os.path.join(fe, "index.html")
    with open(idx, "r", encoding="utf-8") as f:
        html = f.read()
    notify_tag = '<script src="/%s?v=%s"></script>' % (NOTIFY, _ver(os.path.join(fe, NOTIFY)))
    bridge_tag = '<script defer src="/%s?v=%s"></script>' % (BRIDGE, _ver(os.path.join(fe, BRIDGE)))
    # 先移除旧的注入标签（含旧的 ?v= 查询串），再插入当前版本。
    html = re.sub(r'\s*<script[^>]*src="/ofone-(?:bridge|notify)\.js[^"]*"[^>]*></script>', '', html)
    if "</head>" in html:
        html = html.replace("</head>", notify_tag + "\n</head>", 1)
    else:
        html = notify_tag + "\n" + html
    if "</body>" in html:
        html = html.replace("</body>", bridge_tag + "\n</body>", 1)
    else:
        html = html + bridge_tag + "\n"
    with open(idx, "w", encoding="utf-8") as f:
        f.write(html)
    return True


def copy_one(src, fe, name):
    dst = os.path.join(fe, name)
    if os.path.isfile(src):
        shutil.copyfile(src, dst)
    return os.path.isfile(dst)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "/mnt/share/ofone-bridge.js"
    fe = find_frontend()
    if not fe:
        print("未找到 OpenFic 前端目录，跳过注入")
        return 1
    if not copy_one(src, fe, BRIDGE):
        print("缺少 " + BRIDGE)
        return 1
    # 通知兼容层与本脚本同目录（均为 /mnt/share）。
    copy_one(os.path.join(os.path.dirname(src), NOTIFY), fe, NOTIFY)
    patch_index(fe)
    print("注入完成: " + fe)
    return 0


if __name__ == "__main__":
    sys.exit(main())
