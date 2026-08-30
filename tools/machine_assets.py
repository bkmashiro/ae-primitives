#!/usr/bin/env python3
"""Small deterministic CSG and texture compiler for Minecraft block assets."""
from __future__ import annotations

import random
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any

Voxel = tuple[int, int, int]
Color = tuple[int, int, int, int]


@dataclass(frozen=True)
class Diagnostic:
    severity: str
    code: str
    path: str
    message: str


@dataclass
class CompileResult:
    model: dict[str, Any]
    diagnostics: list[Diagnostic]
    solid_voxels: int
    components: int


def _diagnostic(severity: str, code: str, path: str, message: str) -> Diagnostic:
    return Diagnostic(severity, code, path, message)


def _bounds(node: dict[str, Any], path: str, integer: bool) -> tuple[list[float], list[Diagnostic]]:
    diagnostics: list[Diagnostic] = []
    raw = node.get("bounds")
    if not isinstance(raw, list) or len(raw) != 6 or not all(isinstance(value, (int, float)) for value in raw):
        return [0, 0, 0, 0, 0, 0], [_diagnostic("error", "geometry.bounds", path, "bounds must contain six numbers")]
    values = [float(value) for value in raw]
    if any(values[index] >= values[index + 3] for index in range(3)):
        diagnostics.append(_diagnostic("error", "geometry.empty", path, "bounds must have positive volume"))
    if any(value < 0 or value > 16 for value in values):
        diagnostics.append(_diagnostic("error", "geometry.out_of_bounds", path, "bounds must stay inside 0..16"))
    if integer and any(not value.is_integer() for value in values):
        diagnostics.append(_diagnostic("error", "geometry.grid_alignment", path, "CSG solids must align to the 1/16 grid"))
    return values, diagnostics


def _box_voxels(bounds: list[float], material: str) -> dict[Voxel, str]:
    x0, y0, z0, x1, y1, z1 = (int(value) for value in bounds)
    return {
        (x, y, z): material
        for x in range(x0, x1)
        for y in range(y0, y1)
        for z in range(z0, z1)
    }


def _frame_voxels(bounds: list[float], width: int, material: str) -> dict[Voxel, str]:
    x0, y0, z0, x1, y1, z1 = (int(value) for value in bounds)
    return {
        (x, y, z): material
        for x in range(x0, x1)
        for y in range(y0, y1)
        for z in range(z0, z1)
        if x < x0 + width or x >= x1 - width or y < y0 + width or y >= y1 - width
    }


def _children(node: dict[str, Any]) -> list[dict[str, Any]]:
    if isinstance(node.get("children"), list):
        return node["children"]
    result = []
    if isinstance(node.get("left"), dict):
        result.append(node["left"])
    if isinstance(node.get("right"), dict):
        result.append(node["right"])
    return result


def _merge_union(
    solids: list[dict[Voxel, str]], node: dict[str, Any], path: str, diagnostics: list[Diagnostic]
) -> dict[Voxel, str]:
    merged: dict[Voxel, str] = {}
    overlaps = 0
    policy = node.get("material_policy", "right_wins")
    for solid in solids:
        shared = merged.keys() & solid.keys()
        overlaps += len(shared)
        if policy == "left_wins":
            for voxel, material in solid.items():
                merged.setdefault(voxel, material)
        else:
            merged.update(solid)
    if overlaps and not node.get("allow_overlap", False):
        diagnostics.append(
            _diagnostic("warning", "geometry.overlap", path, f"union contains {overlaps} overlapping voxels")
        )
    return merged


def _evaluate(
    node: dict[str, Any], path: str, diagnostics: list[Diagnostic]
) -> tuple[dict[Voxel, str], list[dict[str, Any]]]:
    node_type = node.get("type")
    if node_type in {"box", "frame", "crystal"}:
        composition = node.get("composition", "solid")
        bounds, bound_diagnostics = _bounds(node, path, composition != "overlay")
        diagnostics.extend(bound_diagnostics)
        material = node.get("material")
        if composition == "overlay" and not isinstance(material, str):
            diagnostics.append(_diagnostic("error", "material.missing", path, "overlay primitives need a material"))
            material = "missing"
        elif not isinstance(material, str):
            material = "__cutout__"
        if composition == "overlay" or node_type == "crystal":
            overlay = dict(node)
            overlay["bounds"] = bounds
            overlay.setdefault("id", path.rsplit("/", 1)[-1])
            return {}, [overlay]
        if any(item.severity == "error" for item in bound_diagnostics):
            return {}, []
        if node_type == "frame":
            width = int(node.get("width", 1))
            if width <= 0:
                diagnostics.append(_diagnostic("error", "geometry.frame_width", path, "frame width must be positive"))
                return {}, []
            return _frame_voxels(bounds, width, str(material)), []
        return _box_voxels(bounds, str(material or "cutout")), []

    children = _children(node)
    if node_type not in {"group", "boolean"}:
        diagnostics.append(_diagnostic("error", "dsl.node_type", path, f"unsupported node type {node_type!r}"))
        return {}, []
    if not children:
        diagnostics.append(_diagnostic("error", "dsl.children", path, "node needs at least one child"))
        return {}, []

    evaluated = [_evaluate(child, f"{path}/{index}", diagnostics) for index, child in enumerate(children)]
    solids = [item[0] for item in evaluated]
    overlays = [overlay for item in evaluated for overlay in item[1]]
    if node_type == "group":
        solid = _merge_union(solids, node, path, diagnostics)
        return solid, overlays

    op = node.get("op")
    if op in {"union", "or"}:
        return _merge_union(solids, node, path, diagnostics), overlays
    if len(solids) < 2:
        diagnostics.append(_diagnostic("error", "csg.arity", path, f"{op} needs at least two operands"))
        return solids[0] if solids else {}, overlays
    if op in {"subtract", "difference"}:
        result = dict(solids[0])
        removed = 0
        for cutter in solids[1:]:
            shared = result.keys() & cutter.keys()
            removed += len(shared)
            for voxel in shared:
                del result[voxel]
        if not removed:
            diagnostics.append(_diagnostic("warning", "csg.no_effect", path, "subtract does not touch its source"))
        return result, overlays
    if op in {"intersect", "and"}:
        shared = set(solids[0])
        for solid in solids[1:]:
            shared &= solid.keys()
        if not shared:
            diagnostics.append(_diagnostic("warning", "csg.empty", path, "intersection is empty"))
        source = solids[-1]
        return {voxel: source.get(voxel, solids[0][voxel]) for voxel in shared}, overlays
    if op == "xor":
        result: dict[Voxel, str] = {}
        counts: dict[Voxel, int] = {}
        material_by_voxel: dict[Voxel, str] = {}
        for solid in solids:
            for voxel, value in solid.items():
                counts[voxel] = counts.get(voxel, 0) + 1
                material_by_voxel[voxel] = value
        for voxel, count in counts.items():
            if count % 2 == 1:
                result[voxel] = material_by_voxel[voxel]
        return result, overlays
    diagnostics.append(_diagnostic("error", "csg.operation", path, f"unsupported boolean operation {op!r}"))
    return {}, overlays


def _components(solid: dict[Voxel, str]) -> int:
    remaining = set(solid)
    count = 0
    while remaining:
        count += 1
        frontier = [remaining.pop()]
        while frontier:
            x, y, z = frontier.pop()
            for neighbor in ((x - 1, y, z), (x + 1, y, z), (x, y - 1, z), (x, y + 1, z), (x, y, z - 1), (x, y, z + 1)):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    frontier.append(neighbor)
    return count


def _overlap_volume(left: list[float], right: list[float]) -> float:
    spans = [max(0.0, min(left[index + 3], right[index + 3]) - max(left[index], right[index])) for index in range(3)]
    return spans[0] * spans[1] * spans[2]


def _lint_overlays(
    solid: dict[Voxel, str], overlays: list[dict[str, Any]], diagnostics: list[Diagnostic]
) -> None:
    for overlay in overlays:
        if "solid" in overlay.get("allow_overlap_with", []):
            continue
        intersections = sum(
            1
            for x, y, z in solid
            if _overlap_volume(overlay["bounds"], [x, y, z, x + 1, y + 1, z + 1])
        )
        if intersections:
            diagnostics.append(
                _diagnostic(
                    "warning",
                    "overlay.solid_intersection",
                    f"overlay/{overlay.get('id')}",
                    f"intersects {intersections} solid voxels without an explicit allowance",
                )
            )
    for index, left in enumerate(overlays):
        for right in overlays[index + 1:]:
            if not _overlap_volume(left["bounds"], right["bounds"]):
                continue
            left_id = str(left.get("id"))
            right_id = str(right.get("id"))
            left_allows = right_id in left.get("allow_overlap_with", [])
            right_allows = left_id in right.get("allow_overlap_with", [])
            if not left_allows and not right_allows:
                diagnostics.append(
                    _diagnostic(
                        "warning",
                        "overlay.intersection",
                        f"overlay/{left_id}",
                        f"overlaps overlay {right_id} without an explicit allowance",
                    )
                )


def _greedy_boxes(solid: dict[Voxel, str]) -> list[tuple[list[int], str]]:
    remaining = dict(solid)
    boxes: list[tuple[list[int], str]] = []
    while remaining:
        x0, y0, z0 = min(remaining, key=lambda voxel: (voxel[2], voxel[1], voxel[0]))
        material = remaining[(x0, y0, z0)]
        x1 = x0 + 1
        while remaining.get((x1, y0, z0)) == material:
            x1 += 1
        y1 = y0 + 1
        while all(remaining.get((x, y1, z0)) == material for x in range(x0, x1)):
            y1 += 1
        z1 = z0 + 1
        while all(
            remaining.get((x, y, z1)) == material
            for x in range(x0, x1)
            for y in range(y0, y1)
        ):
            z1 += 1
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    remaining.pop((x, y, z), None)
        boxes.append(([x0, y0, z0, x1, y1, z1], material))
    return boxes


def _faces(material: str) -> dict[str, dict[str, str]]:
    return {face: {"texture": f"#{material}"} for face in ("down", "up", "north", "south", "west", "east")}


def _overlay_element(node: dict[str, Any]) -> dict[str, Any]:
    bounds = node["bounds"]
    element: dict[str, Any] = {"from": bounds[:3], "to": bounds[3:], "faces": _faces(str(node["material"]))}
    if node.get("type") == "crystal":
        x0, y0, z0, x1, y1, z1 = bounds
        element["rotation"] = {
            "origin": [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2],
            "axis": node.get("axis", "z"),
            "angle": node.get("angle", 45),
            "rescale": True,
        }
    elif isinstance(node.get("rotation"), dict):
        element["rotation"] = node["rotation"]
    return element


def compile_machine(spec: dict[str, Any]) -> CompileResult:
    diagnostics: list[Diagnostic] = []
    materials = spec.get("materials", {})
    if not isinstance(materials, dict) or not materials:
        diagnostics.append(_diagnostic("error", "material.catalog", "materials", "machine needs a material catalog"))
        materials = {}
    solid, overlays = _evaluate(spec.get("root", {}), "root", diagnostics)
    used_materials = set(solid.values()) | {str(node.get("material")) for node in overlays}
    for material in sorted(used_materials):
        if material not in materials:
            diagnostics.append(_diagnostic("error", "material.unknown", "materials", f"unknown material {material}"))
    component_count = _components(solid)
    root = spec.get("root", {})
    if component_count > 1 and not root.get("allow_islands", False):
        diagnostics.append(
            _diagnostic("warning", "topology.disconnected", "root", f"solid contains {component_count} disconnected regions")
        )
    _lint_overlays(solid, overlays, diagnostics)

    textures: dict[str, str] = {}
    namespace = spec.get("namespace", "aeprimitives")
    for name, material in materials.items():
        texture = material.get("texture", name)
        textures[name] = texture if ":" in texture else f"{namespace}:block/{texture}"
    particle = str(spec.get("particle", next(iter(textures), "minecraft:block/stone")))
    textures["particle"] = textures.get(particle, particle)

    elements = [
        {"from": bounds[:3], "to": bounds[3:], "faces": _faces(material)}
        for bounds, material in _greedy_boxes(solid)
    ]
    elements.extend(_overlay_element(node) for node in overlays)
    model: dict[str, Any] = {"textures": textures, "elements": elements}
    if spec.get("render_type"):
        model["render_type"] = spec["render_type"]
    return CompileResult(model, diagnostics, len(solid), component_count)


def _color(value: str | list[int]) -> Color:
    if isinstance(value, list):
        channels = list(value)
        if len(channels) == 3:
            channels.append(255)
        return tuple(int(channel) for channel in channels[:4])  # type: ignore[return-value]
    value = value.lstrip("#")
    if len(value) == 6:
        value += "ff"
    return tuple(int(value[index:index + 2], 16) for index in range(0, 8, 2))  # type: ignore[return-value]


def render_texture(spec: dict[str, Any]) -> list[list[Color]]:
    size = int(spec.get("size", 16))
    pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
    for layer in spec.get("layers", []):
        layer_type = layer.get("type")
        if layer_type == "fill":
            color = _color(layer["color"])
            pixels = [[color for _ in range(size)] for _ in range(size)]
        elif layer_type == "border":
            color = _color(layer["color"])
            width = int(layer.get("width", 1))
            for y in range(size):
                for x in range(size):
                    if x < width or y < width or x >= size - width or y >= size - width:
                        pixels[y][x] = color
        elif layer_type == "rect":
            color = _color(layer["color"])
            x0, y0, x1, y1 = (int(value) for value in layer["bounds"])
            for y in range(max(0, y0), min(size, y1)):
                for x in range(max(0, x0), min(size, x1)):
                    pixels[y][x] = color
        elif layer_type == "vertical_gradient":
            top, bottom = _color(layer["top"]), _color(layer["bottom"])
            for y in range(size):
                ratio = y / max(1, size - 1)
                color = tuple(round(top[index] * (1 - ratio) + bottom[index] * ratio) for index in range(4))
                for x in range(size):
                    pixels[y][x] = color  # type: ignore[assignment]
        elif layer_type == "noise":
            generator = random.Random(int(layer.get("seed", 0)))
            amount = int(layer.get("amount", 4))
            for y in range(size):
                for x in range(size):
                    delta = generator.randint(-amount, amount)
                    red, green, blue, alpha = pixels[y][x]
                    pixels[y][x] = (
                        max(0, min(255, red + delta)),
                        max(0, min(255, green + delta)),
                        max(0, min(255, blue + delta)),
                        alpha,
                    )
        else:
            raise ValueError(f"unsupported texture layer {layer_type!r}")
    return pixels


def write_png(path: Path, pixels: list[list[Color]]) -> None:
    height = len(pixels)
    width = len(pixels[0]) if pixels else 0
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        checksum = zlib.crc32(kind + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", checksum)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
