#!/usr/bin/env python3
"""Export a machine animation through Minecraft Visual Harness's real renderer."""

from __future__ import annotations

import argparse
import json
import shutil
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SESSION = ROOT / "run-client/mc-harness/client-session.json"
DEFAULT_SCENE = ROOT / "assets-src/previews/fortune-animation.json"


def call(session: dict, route: str, payload: dict) -> dict:
    request = urllib.request.Request(
        session["baseUrl"] + route,
        data=json.dumps(payload).encode(),
        method="POST",
        headers={
            "Authorization": "Bearer " + session["token"],
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            body = json.loads(response.read())
    except urllib.error.URLError as error:
        raise SystemExit(f"Visual Harness is not reachable: {error}") from error
    if not body.get("ok"):
        raise SystemExit(body.get("error", "Visual Harness request failed"))
    return body["result"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--session", type=Path, default=DEFAULT_SESSION)
    parser.add_argument("--scene", type=Path, default=DEFAULT_SCENE)
    parser.add_argument("--name", default="fortune-e2e")
    parser.add_argument("--view", default="isometric_se")
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--frame-millis", type=int, default=120)
    parser.add_argument("--phases", default="0,0.25,0.5,0.75,1")
    args = parser.parse_args()

    session = json.loads(args.session.read_text())
    scene = json.loads(args.scene.read_text())
    phases = [float(value) for value in args.phases.split(",")]
    if not phases or any(value < 0 or value > 1 for value in phases):
        raise SystemExit("phases must be comma-separated values between 0 and 1")

    captures = ROOT / "run-client/mc-harness/captures"
    captures.mkdir(parents=True, exist_ok=True)
    frames: list[dict] = []
    try:
        for index, phase in enumerate(phases):
            call(session, "/v1/client/animation-phase", {"value": phase})
            result = call(session, "/v1/client/render/blocks", {
                "blocks": scene,
                "name": f"{args.name}-{index:03d}",
                "views": [args.view],
                "size": args.size,
            })
            source = Path(result["files"][0]["path"])
            target = captures / f"{args.name}-{index:03d}.png"
            shutil.copy2(source, target)
            frames.append({"index": index, "phase": phase, "path": str(target)})
    finally:
        call(session, "/v1/client/animation-phase", {"value": None})

    animation = call(session, "/v1/client/visual/animation", {
        "images": [Path(frame["path"]).name for frame in frames],
        "name": args.name,
        "frameMillis": args.frame_millis,
        "columns": len(frames),
        "sprite": True,
        "gif": True,
    })
    manifest_path = ROOT / "run-client/mc-harness/timelines" / f"{args.name}-manifest.json"
    manifest_path.write_text(json.dumps({
        "renderer": "minecraft:block-entity",
        "scene": str(args.scene),
        "view": args.view,
        "size": args.size,
        "frames": frames,
        "animation": animation,
    }, indent=2) + "\n")
    print(json.dumps({"manifest": str(manifest_path), **animation}, indent=2))


if __name__ == "__main__":
    main()
