import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from machine_assets import compile_machine, compile_multiblock, render_texture, write_png


class CsgCompilerTest(unittest.TestCase):
    def test_subtract_carves_a_cavity_and_emits_boxes(self):
        spec = {
            "id": "carved",
            "materials": {"shell": {"texture": "shell"}},
            "root": {
                "type": "boolean",
                "op": "subtract",
                "left": {"type": "box", "bounds": [0, 0, 0, 4, 4, 4], "material": "shell"},
                "right": {"type": "box", "bounds": [1, 1, 0, 3, 3, 3]},
            },
        }

        result = compile_machine(spec)

        self.assertEqual(52, result.solid_voxels)
        self.assertGreater(len(result.model["elements"]), 1)
        self.assertFalse([d for d in result.diagnostics if d.severity == "error"])

    def test_xor_removes_the_shared_volume(self):
        spec = {
            "id": "xor",
            "materials": {"a": {"texture": "a"}, "b": {"texture": "b"}},
            "root": {
                "type": "boolean",
                "op": "xor",
                "children": [
                    {"type": "box", "bounds": [0, 0, 0, 3, 1, 1], "material": "a"},
                    {"type": "box", "bounds": [2, 0, 0, 5, 1, 1], "material": "b"},
                ],
            },
        }

        result = compile_machine(spec)

        self.assertEqual(4, result.solid_voxels)

    def test_unacknowledged_union_overlap_is_reported(self):
        spec = {
            "id": "overlap",
            "materials": {"a": {"texture": "a"}, "b": {"texture": "b"}},
            "root": {
                "type": "boolean",
                "op": "union",
                "children": [
                    {"type": "box", "bounds": [0, 0, 0, 3, 3, 3], "material": "a"},
                    {"type": "box", "bounds": [2, 2, 2, 4, 4, 4], "material": "b"},
                ],
            },
        }

        result = compile_machine(spec)

        self.assertIn("geometry.overlap", {d.code for d in result.diagnostics})

    def test_disconnected_solid_is_warning_unless_declared(self):
        root = {
            "type": "group",
            "children": [
                {"type": "box", "bounds": [0, 0, 0, 2, 2, 2], "material": "a"},
                {"type": "box", "bounds": [5, 5, 5, 6, 6, 6], "material": "a"},
            ],
        }
        spec = {"id": "islands", "materials": {"a": {"texture": "a"}}, "root": root}
        result = compile_machine(spec)
        self.assertIn("topology.disconnected", {d.code for d in result.diagnostics})

        root["allow_islands"] = True
        allowed = compile_machine(spec)
        self.assertNotIn("topology.disconnected", {d.code for d in allowed.diagnostics})

    def test_overlay_overlap_requires_an_explicit_allowance(self):
        spec = {
            "id": "overlay",
            "materials": {"core": {"texture": "core"}, "beam": {"texture": "beam"}},
            "root": {
                "type": "group",
                "children": [
                    {"id": "core", "type": "box", "composition": "overlay", "bounds": [4, 4, 4, 8, 8, 8], "material": "core"},
                    {"id": "beam", "type": "box", "composition": "overlay", "bounds": [0, 5, 5, 6, 7, 7], "material": "beam"},
                ],
            },
        }

        result = compile_machine(spec)
        self.assertIn("overlay.intersection", {d.code for d in result.diagnostics})

        spec["root"]["children"][1]["allow_overlap_with"] = ["core"]
        allowed = compile_machine(spec)
        self.assertNotIn("overlay.intersection", {d.code for d in allowed.diagnostics})

    def test_overlay_inside_solid_is_reported_unless_allowed(self):
        spec = {
            "id": "embedded",
            "materials": {"shell": {"texture": "shell"}, "glass": {"texture": "glass"}},
            "root": {
                "type": "group",
                "children": [
                    {"type": "box", "bounds": [0, 0, 0, 4, 4, 4], "material": "shell"},
                    {
                        "id": "glass",
                        "type": "box",
                        "composition": "overlay",
                        "bounds": [1, 1, 1, 3, 3, 3],
                        "material": "glass",
                    },
                ],
            },
        }
        result = compile_machine(spec)
        self.assertIn("overlay.solid_intersection", {d.code for d in result.diagnostics})

        spec["root"]["children"][1]["allow_overlap_with"] = ["solid"]
        allowed = compile_machine(spec)
        self.assertNotIn("overlay.solid_intersection", {d.code for d in allowed.diagnostics})
    def test_generated_faces_use_global_uv_coordinates(self):
        spec = {
            "id": "uv",
            "materials": {"shell": {"texture": "shell"}},
            "root": {"type": "box", "bounds": [2, 3, 4, 6, 7, 8], "material": "shell"},
        }
        result = compile_machine(spec)
        faces = {
            direction: face
            for element in result.model["elements"]
            for direction, face in element["faces"].items()
        }
        self.assertEqual([2, 4, 6, 8], faces["up"]["uv"])
        self.assertEqual([10, 9, 14, 13], faces["north"]["uv"])
        self.assertTrue(all(len(face["uv"]) == 4 for face in faces.values()))

        overlay_spec = {
            "id": "overlay_uv",
            "materials": {"crystal": {"texture": "crystal"}},
            "root": {
                "type": "crystal",
                "composition": "overlay",
                "bounds": [6, 4, 3, 10, 12, 7],
                "material": "crystal",
            },
        }
        overlay = compile_machine(overlay_spec).model["elements"][0]
        self.assertEqual([0, 0, 16, 16], overlay["faces"]["up"]["uv"])

        overlay_spec["root"]["uv_mode"] = "world"
        clipped_overlay = compile_machine(overlay_spec).model["elements"][0]
        self.assertEqual([6, 3, 10, 7], clipped_overlay["faces"]["up"]["uv"])
        self.assertEqual([6, 4, 10, 12], clipped_overlay["faces"]["south"]["uv"])


class TextureCompilerTest(unittest.TestCase):
    def test_texture_layers_are_deterministic_and_writable(self):
        spec = {
            "size": 16,
            "layers": [
                {"type": "fill", "color": "#182028"},
                {"type": "border", "width": 2, "color": "#6bdde8"},
                {"type": "noise", "amount": 8, "seed": 7},
            ],
        }
        first = render_texture(spec)
        second = render_texture(spec)
        self.assertEqual(first, second)
        self.assertNotEqual(first[0][0], first[8][8])

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "texture.png"
            write_png(output, first)
            contents = output.read_bytes()
            self.assertTrue(contents.startswith(b"\x89PNG\r\n\x1a\n"))
            self.assertEqual((16, 16), tuple(int.from_bytes(contents[i:i + 4], "big") for i in (16, 20)))


class MultiblockCompilerTest(unittest.TestCase):
    def test_repeated_parts_are_translated_into_one_preview_scene(self):
        spec = {
            "id": "array",
            "materials": {"frame": {"texture": "frame"}},
            "parts": [
                {
                    "id": "pillars",
                    "at": [[0, 0, 0], [2, 0, 0]],
                    "root": {"type": "box", "bounds": [0, 0, 0, 4, 16, 4], "material": "frame"},
                }
            ],
        }

        result = compile_multiblock(spec)

        self.assertEqual(2, result.parts)
        self.assertEqual([0, 0, 0], result.model["elements"][0]["from"])
        self.assertEqual([32, 0, 0], result.model["elements"][1]["from"])
        self.assertEqual([24, 8, 8], result.model["center"])
        self.assertIn("topology.disconnected", {item.code for item in result.diagnostics})

    def test_duplicate_part_cell_is_reported(self):
        spec = {
            "id": "duplicate",
            "materials": {"frame": {"texture": "frame"}},
            "parts": [
                {
                    "id": "blocks",
                    "at": [[0, 0, 0], [0, 0, 0]],
                    "root": {"type": "box", "bounds": [0, 0, 0, 16, 16, 16], "material": "frame"},
                }
            ],
        }

        result = compile_multiblock(spec)

        self.assertIn("multiblock.duplicate_cell", {item.code for item in result.diagnostics})


if __name__ == "__main__":
    unittest.main()
