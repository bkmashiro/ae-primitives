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

## Simple animations

Machine specs may define small transform tracks for renderer-owned parts. Tracks support `translate_x/y/z`, `rotate_x/y/z` and uniform `scale`, with `linear` or `smoothstep` interpolation and `clamp`, `repeat` or `pingpong` clocks.

```json
{
  "animations": {
    "work": {
      "clock": "progress",
      "loop": "clamp",
      "duration": 1,
      "tracks": [{
        "target": "runtime:tool",
        "property": "translate_y",
        "easing": "smoothstep",
        "keyframes": [[0, 0], [0.5, -0.16], [1, 0]]
      }]
    }
  }
}
```

The compiler validates the animation and writes `assets/aeprimitives/animations/<machine>.json`. `SimpleMachineAnimations` samples the generated track from a block-entity renderer. `exportVisualGallery` also exports fixed-phase PNGs without opening a browser window; Minecraft Visual Harness can combine the same frames into a sprite sheet or GIF. When the Harness sets `mcvisualharness.animationPhase`, the in-game sampler uses that exact 0–1 phase for deterministic captures.

## Lint

The compiler reports malformed bounds, out-of-range or off-grid solids, unknown materials, ineffective or empty CSG operations, undeclared solid/overlay intersections, undeclared overlay intersections and disconnected solid regions. A machine may declare `allow_islands` when separate solid regions are intentional.

`build/machine-assets/report.json` records voxel count, connected components, generated element and animation counts, and diagnostics for each machine. `lintMachineAssets` fails on any diagnostic so exceptions stay explicit in the source specification.

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
