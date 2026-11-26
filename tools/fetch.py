import os, sys, time, urllib.request

OUT = os.path.dirname(os.path.abspath(__file__)) + "/../dl"
os.makedirs(OUT, exist_ok=True)

ITEMS = [
    ("build-tools_r34-windows.zip", "https://dl.google.com/android/repository/build-tools_r34-windows.zip"),
    ("platform-34-ext7_r03.zip", "https://dl.google.com/android/repository/platform-34-ext7_r03.zip"),
    ("ubuntu-base-24.04.5-base-arm64.tar.gz", "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"),
    ("proot_aarch64.deb", "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.102_aarch64.deb"),
    ("libtalloc_aarch64.deb", "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb"),
    ("libandroid-shmem_aarch64.deb", "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.5_aarch64.deb"),
]

def fetch(name, url):
    dest = os.path.join(OUT, name)
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=60) as r, open(dest + ".part", "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                got = 0
                while True:
                    chunk = r.read(1 << 16)
                    if not chunk:
                        break
                    f.write(chunk)
                    got += len(chunk)
            os.replace(dest + ".part", dest)
            print("OK", name, got, "of", total, flush=True)
            return True
        except Exception as e:
            print("RETRY", name, attempt, type(e).__name__, str(e)[:100], flush=True)
            time.sleep(2)
    print("FAIL", name, flush=True)
    return False

for name, url in ITEMS:
    if os.path.exists(os.path.join(OUT, name)):
        print("SKIP", name, flush=True)
        continue
    fetch(name, url)
print("DONE", flush=True)
