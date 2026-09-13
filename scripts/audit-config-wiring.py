#!/usr/bin/env python3
"""Audit HeliumConfig booleans for runtime consumers outside config/UI code.

This is a heuristic audit, not a compiler. It intentionally reports fields with no
`*.field`, `this.field`, or direct `field` references outside config classes and UI.
Every reported candidate still requires manual review before removal.
"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/client/java/com/helium/config/HeliumConfig.java"
RUNTIME_ROOT = ROOT / "src/client/java/com/helium"
EXCLUDED_NAMES = {
    "HeliumConfig.java",
    "ExperimentalConfig.java",
    "GpuComputeConfig.java",
    "HeliumConfigScreen.java",
    "HeliumSharedOptions.java",
    "HeliumSodiumConfig.java",
    "HeliumSodiumBooleanConfig.java",
}

fields = []
for line in CONFIG.read_text(encoding="utf-8").splitlines():
    m = re.search(r"public\s+boolean\s+(\w+)\s*=", line)
    if m:
        fields.append(m.group(1))

files = [p for p in RUNTIME_ROOT.rglob("*.java") if p.name not in EXCLUDED_NAMES]

for field in fields:
    pattern = re.compile(rf"(?:\bconfig\.|\bthis\.){re.escape(field)}\b|\b{re.escape(field)}\b")
    hits = []
    for path in files:
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if pattern.search(text):
            hits.append(path.relative_to(ROOT).as_posix())
    if not hits:
        print(f"UNREFERENCED {field}")
    elif len(hits) == 1 and hits[0].endswith("HeliumClient.java"):
        print(f"CLIENT-ONLY {field} -> {hits[0]}")
