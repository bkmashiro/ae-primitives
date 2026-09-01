#!/usr/bin/env python3
"""Compile declarative machine assets into Minecraft models and textures."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from machine_assets import CompileResult, MultiblockCompileResult, compile_machine, compile_multiblock, render_texture, write_png

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets-src/machines"
MULTIBLOCK_SOURCE = ROOT / "assets-src/multiblocks"
TEXTURE_SOURCE = ROOT / "assets-src/textures"
MODEL_OUTPUT = ROOT / "src/main/resources/assets/aeprimitives/models/block"
ANIMATION_OUTPUT = ROOT / "src/main/resources/assets/aeprimitives/animations"
TEXTURE_OUTPUT = ROOT / "src/main/resources/assets/aeprimitives/textures/block"
MULTIBLOCK_OUTPUT = ROOT / "build/machine-assets/multiblocks"
REPORT_OUTPUT = ROOT / "build/machine-assets/report.json"


def compile_file(path: Path) -> CompileResult:
    spec = json.loads(path.read_text())
    result = compile_machine(spec)
    machine_id = spec["id"]
    MODEL_OUTPUT.mkdir(parents=True, exist_ok=True)
    model = dict(result.model)
    animations = model.pop("aeprimitives_animations", None)
    (MODEL_OUTPUT / f"{machine_id}.json").write_text(json.dumps(model, indent=2) + "\n")
    animation_path = ANIMATION_OUTPUT / f"{machine_id}.json"
    if animations:
        ANIMATION_OUTPUT.mkdir(parents=True, exist_ok=True)
        animation_path.write_text(json.dumps({"schemaVersion": 1, "animations": animations}, indent=2) + "\n")
    elif animation_path.exists():
        animation_path.unlink()
    for material in spec["materials"].values():
        texture_spec = material.get("texture_spec")
        if texture_spec:
            write_png(TEXTURE_OUTPUT / f"{material['texture']}.png", render_texture(texture_spec))
    return result


def compile_texture_file(path: Path) -> int:
    spec = json.loads(path.read_text())
    textures = spec.get("textures")
    if not isinstance(textures, dict):
        raise ValueError(f"{path}: textures must be an object")
    for name, texture_spec in textures.items():
        if not isinstance(name, str) or not isinstance(texture_spec, dict):
            raise ValueError(f"{path}: invalid texture entry {name!r}")
        write_png(TEXTURE_OUTPUT / f"{name}.png", render_texture(texture_spec))
    return len(textures)


def compile_multiblock_file(path: Path) -> MultiblockCompileResult:
    spec = json.loads(path.read_text())
    result = compile_multiblock(spec)
    MULTIBLOCK_OUTPUT.mkdir(parents=True, exist_ok=True)
    payload = dict(result.model)
    payload["id"] = spec["id"]
    payload["label"] = spec.get("label", spec["id"].replace("_", " ").title())
    (MULTIBLOCK_OUTPUT / f"{spec['id']}.json").write_text(json.dumps(payload, indent=2) + "\n")
    for material in spec["materials"].values():
        texture_spec = material.get("texture_spec")
        if texture_spec:
            write_png(TEXTURE_OUTPUT / f"{material['texture']}.png", render_texture(texture_spec))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="Treat warnings as failures")
    arguments = parser.parse_args()
    reports = []
    failed = False
    shared_texture_count = 0
    for path in sorted(TEXTURE_SOURCE.glob("*.json")):
        shared_texture_count += compile_texture_file(path)
    for path in sorted(SOURCE.glob("*.json")):
        result = compile_file(path)
        diagnostics = [
            {"severity": item.severity, "code": item.code, "path": item.path, "message": item.message}
            for item in result.diagnostics
        ]
        reports.append(
            {
                "machine": path.stem,
                "solid_voxels": result.solid_voxels,
                "components": result.components,
                "elements": len(result.model["elements"]),
                "animations": len(result.model.get("aeprimitives_animations", {})),
                "diagnostics": diagnostics,
            }
        )
        for item in result.diagnostics:
            print(f"{path.name}:{item.path}: {item.severity}: {item.code}: {item.message}")
            if item.severity == "error" or arguments.strict:
                failed = True
    for path in sorted(MULTIBLOCK_SOURCE.glob("*.json")):
        result = compile_multiblock_file(path)
        diagnostics = [
            {"severity": item.severity, "code": item.code, "path": item.path, "message": item.message}
            for item in result.diagnostics
        ]
        reports.append(
            {
                "machine": path.stem,
                "kind": "multiblock_preview",
                "parts": result.parts,
                "solid_voxels": result.solid_voxels,
                "components": result.components,
                "elements": len(result.model["elements"]),
                "diagnostics": diagnostics,
            }
        )
        for item in result.diagnostics:
            print(f"{path.name}:{item.path}: {item.severity}: {item.code}: {item.message}")
            if item.severity == "error" or arguments.strict:
                failed = True
    REPORT_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    REPORT_OUTPUT.write_text(json.dumps({"machines": reports}, indent=2) + "\n")
    print(f"{REPORT_OUTPUT} ({shared_texture_count} shared texture(s))")
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
