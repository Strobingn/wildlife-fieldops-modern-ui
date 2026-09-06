#!/usr/bin/env python3
import base64, gzip, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def try_apply(patch: Path) -> bool:
    check = subprocess.run(["git", "apply", "--check", str(patch)], cwd=ROOT, capture_output=True, text=True)
    if check.returncode != 0:
        print(f"skip {patch.name}: already applied or does not match\n{check.stderr}")
        return False
    subprocess.check_call(["git", "apply", "--verbose", str(patch)], cwd=ROOT)
    return True

applied = False
mp = ROOT / "tools/polish-chunks/MapScreen.git.patch"
if mp.exists():
    applied = try_apply(mp) or applied

parts = sorted((ROOT / "tools/polish-chunks").glob("InspectionFormScreen.git.patch.gz.b64.*"))
if parts:
    b64 = "".join(p.read_text().strip() for p in parts)
    data = gzip.decompress(base64.b64decode(b64))
    patch_path = ROOT / "tools/polish-chunks/InspectionFormScreen.git.patch"
    patch_path.write_bytes(data)
    applied = try_apply(patch_path) or applied

print("polish apply done; applied_any=", applied)
