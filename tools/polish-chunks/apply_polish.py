#!/usr/bin/env python3
import base64, gzip, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
mp = ROOT / "tools/polish-chunks/MapScreen.git.patch"
if mp.exists():
    subprocess.check_call(["git", "apply", "--verbose", str(mp)], cwd=ROOT)
parts = sorted((ROOT / "tools/polish-chunks").glob("InspectionFormScreen.git.patch.gz.b64.*"))
if parts:
    b64 = "".join(p.read_text().strip() for p in parts)
    data = gzip.decompress(base64.b64decode(b64))
    patch_path = ROOT / "tools/polish-chunks/InspectionFormScreen.git.patch"
    patch_path.write_bytes(data)
    subprocess.check_call(["git", "apply", "--verbose", str(patch_path)], cwd=ROOT)
print("polish apply done")
