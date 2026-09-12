#!/usr/bin/env python3
import base64, json, pathlib
root = pathlib.Path(__file__).resolve().parent
repo = root.parent.parent
man = json.loads((root / "manifest.json").read_text())
data = "".join((root / c["name"]).read_text().strip() for c in man["chunks"])
out = repo / man["target"]
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(base64.b64decode(data))
text = out.read_text()
assert '"$" + "_".repeat(14)' in text, "missing fix"
assert '"$______________"' not in text, "bad literal still present"
print("wrote", out, "bytes", out.stat().st_size)
