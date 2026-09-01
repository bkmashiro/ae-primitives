#!/usr/bin/env python3
"""Export the generated block gallery to PNG without opening a browser window."""

from __future__ import annotations

import os
import shutil
import signal
import struct
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GALLERY = ROOT / "build/visual-gallery/index.html"
OUTPUT = ROOT / "build/visual-gallery/images"
ANIMATION_OUTPUT = ROOT / "build/visual-gallery/animations/fortune"
MODELS = (
    "fortune_chamber", "transformation_chamber", "resource_generator", "growth_chamber", "compost_chamber",
    "concrete_curing_chamber", "soil_processor", "dripstone_reservoir", "oxidation_chamber",
    "crop_cultivator", "tree_nursery", "growth_rack", "apiary_chamber", "batch_gate", "cooling_plate",
    "resonance_foundry", "me_press", "me_crusher", "me_catalyst_chamber", "me_basin_processor", "me_filling_station", "me_deployer",
    "me_saw", "me_mill", "me_polisher",
    "basic_spatial_parallel", "advanced_spatial_parallel", "ultimate_spatial_parallel",
)
CHROME_CANDIDATES = (
    Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
    Path("/Applications/Chromium.app/Contents/MacOS/Chromium"),
)


def find_chrome() -> Path:
    for candidate in CHROME_CANDIDATES:
        if candidate.is_file():
            return candidate
    for executable in ("google-chrome", "chromium", "chromium-browser"):
        resolved = shutil.which(executable)
        if resolved:
            return Path(resolved)
    raise SystemExit("Chrome or Chromium is required for PNG export")


def png_size(path: Path) -> tuple[int, int]:
    contents = path.read_bytes()
    if contents[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"Chrome did not produce a PNG: {path}")
    return struct.unpack(">II", contents[16:24])


def capture(chrome: Path, profiles: Path, output: Path, size: tuple[int, int], query: str) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    profile = profiles / output.stem
    url = f"{GALLERY.as_uri()}?export=1{query}"
    command = [
        str(chrome),
        "--headless=new",
        "--hide-scrollbars",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-background-networking",
        "--disable-component-update",
        "--disable-extensions",
        "--disable-sync",
        "--no-pings",
        f"--user-data-dir={profile}",
        f"--window-size={size[0]},{size[1]}",
        "--force-device-scale-factor=1",
        "--run-all-compositor-stages-before-draw",
        "--virtual-time-budget=1500",
        f"--screenshot={output}",
        url,
    ]
    process = subprocess.Popen(
        command,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    deadline = time.monotonic() + 30
    try:
        while time.monotonic() < deadline:
            if output.is_file() and output.stat().st_size > 24:
                actual = png_size(output)
                if actual != size:
                    raise RuntimeError(
                        f"expected {size[0]}x{size[1]}, got {actual[0]}x{actual[1]} for {output}"
                    )
                return
            if process.poll() is not None:
                raise RuntimeError(f"headless Chrome exited before exporting {output.name}")
            time.sleep(0.1)
        raise RuntimeError(f"headless Chrome timed out while exporting {output.name}")
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()


def compose_gallery(output: Path) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("ffmpeg is required to compose the gallery image")
    command = [ffmpeg, "-y"]
    for model in MODELS:
        command.extend(["-i", str(OUTPUT / f"{model}.png")])
    scaled = ";".join(
        f"[{index}:v]crop=512:440:0:0,scale=300:258,pad=300:300:0:21:color=#10161c[s{index}]"
        for index in range(len(MODELS))
    )
    inputs = "".join(f"[s{index}]" for index in range(len(MODELS)))
    command.extend([
        "-filter_complex", f"{scaled};{inputs}hstack=inputs={len(MODELS)}[out]",
        "-map", "[out]",
        str(output),
    ])
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if png_size(output) != (300 * len(MODELS), 300):
        raise RuntimeError(f"unexpected gallery dimensions for {output}")


def main() -> None:
    if not GALLERY.is_file():
        raise SystemExit("Generate the gallery first with ./gradlew visualGallery")
    chrome = find_chrome()
    with tempfile.TemporaryDirectory(prefix="ae-primitives-gallery-") as temp:
        profile = Path(temp)
        for model in MODELS:
            capture(chrome, profile, OUTPUT / f"{model}.png", (512, 470), f"&model={model}")
        for index, phase in enumerate((0, 0.25, 0.5, 0.75, 1)):
            capture(
                chrome,
                profile,
                ANIMATION_OUTPUT / f"fortune-{index:03d}.png",
                (512, 470),
                f"&model=fortune_chamber&animation=work&phase={phase}",
            )
    compose_gallery(OUTPUT / "gallery.png")
    for path in (OUTPUT / "gallery.png", *(OUTPUT / f"{model}.png" for model in MODELS)):
        print(path)
    for path in sorted(ANIMATION_OUTPUT.glob("fortune-*.png")):
        print(path)


if __name__ == "__main__":
    main()