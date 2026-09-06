#!/usr/bin/env python3
"""Assemble polished screen files from base64 chunks and write into the Android tree."""
from __future__ import annotations
import base64, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads((ROOT / "tools/polish-chunks/manifest.json").read_text())
for name, meta in manifest.items():
    data = "".join((ROOT / rel).read_text().strip() for rel in meta["chunks"])
    raw = base64.b64decode(data)
    digest = hashlib.sha256(raw).hexdigest()
    if digest != meta["sha256"]:
        raise SystemExit(f"sha mismatch for {name}: {digest} != {meta['sha256']}")
    dest = ROOT / meta["dest"]
    dest.write_bytes(raw)
    print(f"wrote {dest} ({len(raw)} bytes)")
