import gzip, os, re, urllib.request

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DL = os.path.join(BASE, "dl", "debs")
MIRROR = "http://mirrors.aliyun.com/ubuntu-ports"
SUITES = ["noble", "noble-updates"]
COMPS = ["main", "universe"]
os.makedirs(DL, exist_ok=True)

def get(url, timeout=180):
    req = urllib.request.Request(url, headers={"User-Agent": "Debian APT-HTTP/1.3"})
    return urllib.request.urlopen(req, timeout=timeout).read()

pkgs = {}
for suite in SUITES:
    for comp in COMPS:
        url = MIRROR + "/dists/" + suite + "/" + comp + "/binary-arm64/Packages.gz"
        try:
            raw = get(url)
        except Exception as e:
            print("skip", url, type(e).__name__)
            continue
        txt = gzip.decompress(raw).decode("utf-8", "replace")
        for block in txt.split("\n\n"):
            if not block.strip():
                continue
            d = {}
            for line in block.split("\n"):
                if ":" in line and not line.startswith(" "):
                    key, val = line.split(":", 1)
                    d[key.strip()] = val.strip()
            name = d.get("Package")
            if not name:
                continue
            pkgs[name] = d

roots = ["python3.12", "python3.12-venv", "python3-pip", "libstdc++6", "libgomp1", "libgcc-s1", "libbz2-1.0", "libuuid1", "libnsl2", "libtirpc3", "libmpdec4", "libc-bin"]
seen = {}
queue = list(roots)
missing = []
while queue:
    name = queue.pop(0)
    if name in seen:
        continue
    p = pkgs.get(name)
    if not p:
        missing.append(name)
        continue
    seen[name] = p
    for dep in p.get("Depends", "").split(","):
        dep = dep.strip()
        if not dep:
            continue
        dep = dep.split("|")[0].strip()
        dep = re.sub(r"\(.*?\)", "", dep).strip()
        dep = dep.split(":")[0].strip()
        if not dep or dep.startswith("$"):
            continue
        if dep in ("libc6", "debconf", "dpkg", "perl-base", "sensible-utils", "install-info", "usrmerge", "base-files"):
            continue
        queue.append(dep)

print("resolved", len(seen), "missing", missing)
total = 0
for name, p in sorted(seen.items()):
    fn = os.path.basename(p["Filename"])
    path = os.path.join(DL, fn)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        total += os.path.getsize(path)
        continue
    try:
        data = get(MIRROR + "/" + p["Filename"])
        open(path, "wb").write(data)
        total += len(data)
        print("got", name, p["Version"], len(data))
    except Exception as e:
        print("FAIL", name, type(e).__name__, str(e)[:80])
print("total bytes", total)
