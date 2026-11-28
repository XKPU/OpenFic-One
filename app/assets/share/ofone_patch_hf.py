# -*- coding: utf-8 -*-
"""把 OpenFic 内核 fastembed 的 HuggingFace 预检地址改为可配置，使中文界面走国内镜像。

fastembed_embeddings.py 里 _HF_URL 硬编码为 https://huggingface.co，不读取
HF_ENDPOINT/HF_HUB_ENDPOINT，导致网络环境屏蔽 HF 时索引预检（_check_hf_reachable）
5 秒超时快速失败。此处把该行替换为读取环境变量，幂等。
"""
import glob
import os
import re
import sys

MARK = "# ofone-hf-mirror-patched"

PATS = [
    "/opt/venv/lib/python*/site-packages/app/models/clients/fastembed_embeddings.py",
]


def find_fed():
    for p in PATS:
        for h in sorted(glob.glob(p)):
            if os.path.isfile(h):
                return h
    return None


def main():
    path = find_fed()
    if not path:
        print("OFONE_HF_PATCH=skip")
        return 0
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    if MARK in src:
        print("OFONE_HF_PATCH=already")
        return 0
    new_line = (
        '_HF_URL = (os.environ.get("HF_ENDPOINT") or os.environ.get("HF_HUB_ENDPOINT") '
        'or "https://huggingface.co").rstrip("/")  ' + MARK
    )
    pat = re.compile(r'^_HF_URL\s*=\s*"https://huggingface\.co"\s*$', re.M)
    new_src, n = pat.subn(new_line, src)
    if n == 0:
        print("OFONE_HF_PATCH=nomatch")
        return 0
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_src)
    print("OFONE_HF_PATCH=ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
