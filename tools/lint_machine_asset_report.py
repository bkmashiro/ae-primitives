#!/usr/bin/env python3
"""Fail when a generated machine asset report contains diagnostics."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build/machine-assets/report.json"
report = json.loads(REPORT.read_text())
diagnostics = [item for machine in report["machines"] for item in machine["diagnostics"]]
if diagnostics:
    for item in diagnostics:
        print(f"{item['severity']}: {item['code']}: {item['path']}: {item['message']}")
    raise SystemExit(1)
print(f"Machine asset lint passed for {len(report['machines'])} machine(s)")
