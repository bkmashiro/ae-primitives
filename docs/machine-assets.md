# Machine asset pipeline

Machine models can be described as a small tree of shape primitives in `assets-src/machines/`. The compiler produces Minecraft block models, deterministic PNG textures and a lint report.

Preview-only multiblock studies live in `assets-src/multiblocks/`. They reuse the same block-local geometry trees and place them on integer block coordinates. The compiler assembles them only for the visual gallery; a study does not register blocks or imply gameplay behavior.

```bash
./gradlew generateMachineAssets lintMachineAssets exportVisualGallery
```

## Geometry tree

Leaves use `box`, `frame` or `crystal`. Internal nodes use `group` or `boolean` with `union`, `subtract`, `intersect` or `xor`.

```json
{
  "type": "boolean",
  "op": "subtract",
  "left": {"type": "box", "bounds": [0, 0, 0, 16, 16, 16], "material": "casing"},
  "right": {"type": "box", "bounds": [3, 3, 0, 13, 13, 7]}
}
```

Solid CSG uses the native 1/16 block grid and is greedily merged back into cuboids. Glass, crystals, beams and renderer-facing parts use `"composition": "overlay"` so they remain separate elements.

Overlaps must be declared locally:

```json
{
  "id": "beam",
  "type": "box",
  "composition": "overlay",
  "bounds": [5, 6, 5, 7, 7, 6],
  "material": "energy",
  "allow_overlap_with": ["core"]
}
```

## Texture layers

Materials may reference an existing texture or generate one from deterministic layers:

```json
{
  "texture": "fluix_core",
  "texture_spec": {
    "size": 16,
    "layers": [
      {"type": "vertical_gradient", "top": "#f4c6ff", "bottom": "#572f91"},
      {"type": "border", "width": 2, "color": "#9a64d8"},
      {"type": "noise", "amount": 5, "seed": 47}
    ]
  }
}
```

The initial layer set is `fill`, `vertical_gradient`, `border`, `rect` and seeded `noise`.

## Lint

The compiler reports malformed bounds, out-of-range or off-grid solids, unknown materials, ineffective or empty CSG operations, undeclared solid/overlay intersections, undeclared overlay intersections and disconnected solid regions. A machine may declare `allow_islands` when separate solid regions are intentional.

`build/machine-assets/report.json` records voxel count, connected components, generated element count and diagnostics for each machine. `lintMachineAssets` fails on any diagnostic so exceptions stay explicit in the source specification.

## Multiblock studies

A part can be repeated at several block coordinates without copying its geometry:

```json
{
  "id": "corner",
  "at": [[0, 0, 0], [2, 0, 0], [0, 0, 2], [2, 0, 2]],
  "root": {
    "type": "box",
    "bounds": [5, 0, 5, 11, 16, 11],
    "material": "casing"
  }
}
```

Each `root` stays inside one 16×16×16 block. The multiblock compiler translates the generated cuboids into an assembly preview and lints duplicate occupied cells, cross-part solid overlap and disconnected global topology. This keeps future in-game controllers free to choose their own validation and assembly rules.
