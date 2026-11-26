import os, sys

roots, out_src = sys.argv[1:-1], sys.argv[-1]
files = []
for root in roots:
    for dirpath, dirs, names in os.walk(root):
        for n in names:
            if n.endswith(".java"):
                files.append(os.path.join(dirpath, n))
with open(out_src, "w", encoding="utf-8") as f:
    for p in files:
        f.write('"' + p.replace("\\", "/") + '"\n')
print("java files:", len(files))
