#!/usr/bin/env python3
"""Small deterministic CSG and texture compiler for Minecraft block assets."""
from __future__ import annotations

import random
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

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


@dataclass
class MultiblockCompileResult:
    model: dict[str, Any]
    diagnostics: list[Diagnostic]
    solid_voxels: int
    components: int
    parts: int


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


def _face_material(
    material: str,
    face: str,
    bounds: Sequence[float],
    materials: dict[str, Any] | None,
) -> str:
    if not materials:
        return material
    definition = materials.get(material)
    if not isinstance(definition, dict):
        return material
    mapping = definition.get("face_materials")
    if not isinstance(mapping, dict):
        return material
    x1, y1, z1, x2, y2, z2 = bounds
    outer = {
        "down": y1 == 0,
        "up": y2 == 16,
        "north": z1 == 0,
        "south": z2 == 16,
        "west": x1 == 0,
        "east": x2 == 16,
    }[face]
    role = face if outer else "inner"
    return str(mapping.get(role, mapping.get("inner", material)))


def _faces(
    material: str,
    bounds: Sequence[float],
    *,
    global_uv: bool = True,
    materials: dict[str, Any] | None = None,
) -> dict[str, dict[str, Any]]:
    x1, y1, z1, x2, y2, z2 = bounds
    uvs = {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }
    if not global_uv:
        uvs = {face: [0, 0, 16, 16] for face in uvs}
    return {
        face: {"texture": f"#{_face_material(material, face, bounds, materials)}", "uv": uv}
        for face, uv in uvs.items()
    }


def _lint_materials(
    materials: dict[str, Any], used_materials: set[str], diagnostics: list[Diagnostic]
) -> None:
    for material in sorted(used_materials):
        if material not in materials:
            diagnostics.append(_diagnostic("error", "material.unknown", "materials", f"unknown material {material}"))
            continue
        definition = materials[material]
        face_materials = definition.get("face_materials") if isinstance(definition, dict) else None
        if face_materials is not None and not isinstance(face_materials, dict):
            diagnostics.append(_diagnostic(
                "error", "material.face_catalog", f"materials/{material}/face_materials",
                "face_materials must be an object",
            ))
            continue
        if not isinstance(face_materials, dict):
            continue
        for role, target in face_materials.items():
            path = f"materials/{material}/face_materials/{role}"
            if role not in {"north", "south", "east", "west", "up", "down", "inner"}:
                diagnostics.append(_diagnostic("error", "material.face_role", path, f"unknown face role {role}"))
            if not isinstance(target, str) or target not in materials:
                diagnostics.append(_diagnostic("error", "material.face_unknown", path, f"unknown face material {target}"))


def _overlay_element(node: dict[str, Any]) -> dict[str, Any]:
    bounds = node["bounds"]
    element: dict[str, Any] = {
        "from": bounds[:3],
        "to": bounds[3:],
        "aeprimitives_part": str(node.get("id", "")),
        "faces": _faces(
            str(node["material"]),
            bounds,
            global_uv=node.get("uv_mode", "fit") == "world",
        ),
    }
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


def _compile_animations(spec: dict[str, Any], diagnostics: list[Diagnostic]) -> dict[str, Any]:
    raw_animations = spec.get("animations")
    if raw_animations is None:
        return {}
    if not isinstance(raw_animations, dict):
        diagnostics.append(_diagnostic("error", "animation.catalog", "animations", "animations must be an object"))
        return {}
    supported_properties = {
        "translate_x", "translate_y", "translate_z",
        "rotate_x", "rotate_y", "rotate_z", "scale",
    }
    compiled: dict[str, Any] = {}
    for name, raw_animation in raw_animations.items():
        path = f"animations/{name}"
        if not isinstance(raw_animation, dict):
            diagnostics.append(_diagnostic("error", "animation.definition", path, "animation must be an object"))
            continue
        clock = raw_animation.get("clock", "progress")
        loop = raw_animation.get("loop", "clamp")
        duration = raw_animation.get("duration", 1)
        if clock not in {"progress", "world"}:
            diagnostics.append(_diagnostic("error", "animation.clock", path, "clock must be progress or world"))
        if loop not in {"clamp", "repeat", "pingpong"}:
            diagnostics.append(_diagnostic("error", "animation.loop", path, "loop must be clamp, repeat or pingpong"))
        if not isinstance(duration, (int, float)) or duration <= 0:
            diagnostics.append(_diagnostic("error", "animation.duration", path, "duration must be positive"))
            duration = 1
        raw_tracks = raw_animation.get("tracks")
        if not isinstance(raw_tracks, list) or not raw_tracks:
            diagnostics.append(_diagnostic("error", "animation.tracks", path, "animation needs at least one track"))
            continue
        tracks: list[dict[str, Any]] = []
        for index, raw_track in enumerate(raw_tracks):
            track_path = f"{path}/tracks/{index}"
            if not isinstance(raw_track, dict):
                diagnostics.append(_diagnostic("error", "animation.track", track_path, "track must be an object"))
                continue
            target = raw_track.get("target")
            prop = raw_track.get("property")
            easing = raw_track.get("easing", "linear")
            if not isinstance(target, str) or not target:
                diagnostics.append(_diagnostic("error", "animation.target", track_path, "track needs a target"))
            if prop not in supported_properties:
                diagnostics.append(_diagnostic("error", "animation.property", track_path, f"unsupported property {prop!r}"))
            if easing not in {"linear", "smoothstep"}:
                diagnostics.append(_diagnostic("error", "animation.easing", track_path, "easing must be linear or smoothstep"))
            raw_keyframes = raw_track.get("keyframes")
            keyframes: list[list[float]] = []
            valid_keyframes = isinstance(raw_keyframes, list) and len(raw_keyframes) >= 2
            if valid_keyframes and isinstance(raw_keyframes, list):
                valid_keyframes = all(
                    isinstance(frame, list) and len(frame) == 2
                    and all(isinstance(value, (int, float)) for value in frame)
                    for frame in raw_keyframes
                )
                if valid_keyframes:
                    keyframes = [[float(frame[0]), float(frame[1])] for frame in raw_keyframes]
                    phases = [frame[0] for frame in keyframes]
                    valid_keyframes = phases == sorted(set(phases)) and phases[0] >= 0 and phases[-1] <= 1
            if not valid_keyframes:
                diagnostics.append(_diagnostic(
                    "error", "animation.keyframes", track_path,
                    "keyframes need unique ascending [phase, value] pairs inside 0..1",
                ))
            tracks.append({
                "target": target,
                "property": prop,
                "easing": easing,
                "keyframes": keyframes,
            })
        compiled[str(name)] = {
            "clock": clock,
            "loop": loop,
            "duration": float(duration),
            "tracks": tracks,
        }
    return compiled


def compile_machine(spec: dict[str, Any]) -> CompileResult:
    diagnostics: list[Diagnostic] = []
    materials = spec.get("materials", {})
    if not isinstance(materials, dict) or not materials:
        diagnostics.append(_diagnostic("error", "material.catalog", "materials", "machine needs a material catalog"))
        materials = {}
    solid, overlays = _evaluate(spec.get("root", {}), "root", diagnostics)
    used_materials = set(solid.values()) | {str(node.get("material")) for node in overlays}
    _lint_materials(materials, used_materials, diagnostics)
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
        {"from": bounds[:3], "to": bounds[3:], "faces": _faces(material, bounds, materials=materials)}
        for bounds, material in _greedy_boxes(solid)
    ]
    elements.extend(_overlay_element(node) for node in overlays)
    model: dict[str, Any] = {"textures": textures, "elements": elements}
    animations = _compile_animations(spec, diagnostics)
    if animations:
        model["aeprimitives_animations"] = animations
    if spec.get("render_type"):
        model["render_type"] = spec["render_type"]
    return CompileResult(model, diagnostics, len(solid), component_count)


def _translate_element(element: dict[str, Any], offset: Sequence[int]) -> dict[str, Any]:
    translated = dict(element)
    translated["from"] = [value + offset[index] for index, value in enumerate(element["from"])]
    translated["to"] = [value + offset[index] for index, value in enumerate(element["to"])]
    if isinstance(element.get("rotation"), dict):
        rotation = dict(element["rotation"])
        rotation["origin"] = [
            value + offset[index]
            for index, value in enumerate(element["rotation"]["origin"])
        ]
        translated["rotation"] = rotation
    return translated


def compile_multiblock(spec: dict[str, Any]) -> MultiblockCompileResult:
    """Compile block-local CSG parts into one preview-only assembly."""
    diagnostics: list[Diagnostic] = []
    materials = spec.get("materials", {})
    if not isinstance(materials, dict) or not materials:
        diagnostics.append(_diagnostic("error", "material.catalog", "materials", "multiblock needs a material catalog"))
        materials = {}

    placements: list[tuple[str, tuple[int, int, int], dict[str, Any]]] = []
    occupied_cells: set[tuple[int, int, int]] = set()
    for part_index, part in enumerate(spec.get("parts", [])):
        part_path = f"parts/{part_index}"
        locations = part.get("at", [])
        if isinstance(locations, list) and len(locations) == 3 and all(isinstance(value, int) for value in locations):
            locations = [locations]
        if not isinstance(locations, list) or not locations:
            diagnostics.append(_diagnostic("error", "multiblock.placement", part_path, "part needs one or more integer cell coordinates"))
            continue
        root = part.get("root")
        if not isinstance(root, dict):
            diagnostics.append(_diagnostic("error", "multiblock.root", part_path, "part needs a local geometry root"))
            continue
        for location_index, raw_location in enumerate(locations):
            if not isinstance(raw_location, list) or len(raw_location) != 3 or not all(isinstance(value, int) for value in raw_location):
                diagnostics.append(_diagnostic("error", "multiblock.placement", f"{part_path}/at/{location_index}", "cell coordinate must contain three integers"))
                continue
            location = tuple(raw_location)
            if location in occupied_cells:
                diagnostics.append(_diagnostic("error", "multiblock.duplicate_cell", f"{part_path}/at/{location_index}", f"cell {list(location)} is already occupied"))
            occupied_cells.add(location)
            placements.append((str(part.get("id", part_index)), location, root))

    global_solid: dict[Voxel, str] = {}
    global_overlays: list[dict[str, Any]] = []
    elements: list[dict[str, Any]] = []
    used_materials: set[str] = set()
    for placement_index, (part_id, cell, root) in enumerate(placements):
        local_diagnostics: list[Diagnostic] = []
        solid, overlays = _evaluate(root, f"parts/{part_id}/{placement_index}", local_diagnostics)
        diagnostics.extend(local_diagnostics)
        offset = tuple(value * 16 for value in cell)
        for voxel, material in solid.items():
            translated_voxel: Voxel = (
                voxel[0] + offset[0],
                voxel[1] + offset[1],
                voxel[2] + offset[2],
            )
            if translated_voxel in global_solid:
                diagnostics.append(_diagnostic("error", "multiblock.geometry_overlap", f"parts/{part_id}/{placement_index}", f"geometry overlaps at {translated_voxel}"))
            global_solid[translated_voxel] = material
            used_materials.add(material)
        for overlay in overlays:
            translated = dict(overlay)
            translated["id"] = f"{part_id}.{placement_index}.{overlay.get('id')}"
            translated["bounds"] = [
                value + offset[index % 3]
                for index, value in enumerate(overlay["bounds"])
            ]
            global_overlays.append(translated)
            used_materials.add(str(overlay.get("material")))
        elements.extend(
            _translate_element(
                {"from": bounds[:3], "to": bounds[3:], "faces": _faces(material, bounds, materials=materials)},
                offset,
            )
            for bounds, material in _greedy_boxes(solid)
        )
        elements.extend(_translate_element(_overlay_element(overlay), offset) for overlay in overlays)

    _lint_materials(materials, used_materials, diagnostics)
    component_count = _components(global_solid)
    if component_count > 1 and not spec.get("allow_islands", False):
        diagnostics.append(_diagnostic("warning", "topology.disconnected", "parts", f"assembly contains {component_count} disconnected solid regions"))

    namespace = spec.get("namespace", "aeprimitives")
    textures: dict[str, str] = {}
    for name, material in materials.items():
        texture = material.get("texture", name)
        textures[name] = texture if ":" in texture else f"{namespace}:block/{texture}"
    particle = str(spec.get("particle", next(iter(textures), "minecraft:block/stone")))
    textures["particle"] = textures.get(particle, particle)

    if occupied_cells:
        minima = [min(cell[index] for cell in occupied_cells) * 16 for index in range(3)]
        maxima = [(max(cell[index] for cell in occupied_cells) + 1) * 16 for index in range(3)]
    else:
        minima, maxima = [0, 0, 0], [16, 16, 16]
    model: dict[str, Any] = {
        "textures": textures,
        "elements": elements,
        "center": [(minima[index] + maxima[index]) / 2 for index in range(3)],
        "span": [maxima[index] - minima[index] for index in range(3)],
        "preview_only": True,
    }
    if isinstance(spec.get("view"), dict):
        model["view"] = spec["view"]
    if spec.get("render_type"):
        model["render_type"] = spec["render_type"]
    return MultiblockCompileResult(model, diagnostics, len(global_solid), component_count, len(placements))


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
        elif layer_type == "bevel":
            width = max(1, int(layer.get("width", 1)))
            top = _color(layer["top"])
            left = _color(layer["left"])
            bottom = _color(layer["bottom"])
            right = _color(layer["right"])
            for y in range(size):
                for x in range(size):
                    if y < width:
                        pixels[y][x] = top
                    elif y >= size - width:
                        pixels[y][x] = bottom
                    elif x < width:
                        pixels[y][x] = left
                    elif x >= size - width:
                        pixels[y][x] = right
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
        elif layer_type == "clustered_noise":
            generator = random.Random(int(layer.get("seed", 0)))
            amount = max(0, int(layer.get("amount", 4)))
            scale = max(1, int(layer.get("scale", 4)))
            steps = max(0, int(layer.get("steps", 0)))
            grid_size = (size - 1) // scale + 2
            grid = [
                [generator.randint(-amount, amount) for _ in range(grid_size)]
                for _ in range(grid_size)
            ]
            for y in range(size):
                gy, fy = divmod(y, scale)
                ty = fy / scale
                for x in range(size):
                    gx, fx = divmod(x, scale)
                    tx = fx / scale
                    top = grid[gy][gx] * (1 - tx) + grid[gy][gx + 1] * tx
                    bottom = grid[gy + 1][gx] * (1 - tx) + grid[gy + 1][gx + 1] * tx
                    delta = top * (1 - ty) + bottom * ty
                    if steps > 1 and amount:
                        interval = 2 * amount / (steps - 1)
                        delta = round((delta + amount) / interval) * interval - amount
                    red, green, blue, alpha = pixels[y][x]
                    pixels[y][x] = (
                        max(0, min(255, round(red + delta))),
                        max(0, min(255, round(green + delta))),
                        max(0, min(255, round(blue + delta))),
                        alpha,
                    )
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
