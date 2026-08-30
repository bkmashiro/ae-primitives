#!/usr/bin/env python3
"""Generate a dependency-free browser gallery for block models."""

from __future__ import annotations

import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/aeprimitives"
MODELS = ASSETS / "models/block"
TEXTURES = ASSETS / "textures"
OUTPUT = ROOT / "build/visual-gallery/index.html"
MODEL_IDS = ("fortune_chamber", "transformation_chamber", "resource_generator")

FACE_VERTICES = {
    "north": lambda a, b: [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
    "south": lambda a, b: [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
    "west": lambda a, b: [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
    "east": lambda a, b: [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
    "up": lambda a, b: [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
    "down": lambda a, b: [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
}
NORMALS = {
    "north": [0, 0, -1], "south": [0, 0, 1], "west": [-1, 0, 0],
    "east": [1, 0, 0], "up": [0, 1, 0], "down": [0, -1, 0],
}

def png_data(path: Path) -> str:
    raw = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/png;base64,{raw}"


def texture_path(resource: str) -> Path:
    namespace, name = resource.split(":", 1)
    if namespace != "aeprimitives":
        raise ValueError(f"preview cannot resolve external texture {resource}")
    return ASSETS / "textures" / f"{name}.png"


def cube_faces(textures: dict[str, str]) -> list[dict]:
    faces = {}
    for face in FACE_VERTICES:
        ref = textures.get(face, textures.get("all", ""))
        faces[face] = {"texture": f"#{face}" if face in textures else "#all", "resolved": ref}
    return [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]


def load_model(model_id: str) -> tuple[dict, dict[str, str]]:
    model = json.loads((MODELS / f"{model_id}.json").read_text())
    textures = model.get("textures", {})
    if model.get("parent") == "minecraft:block/cube":
        elements = cube_faces(textures)
    else:
        elements = model.get("elements", [])

    used: dict[str, str] = {}
    normalized = []
    for element in elements:
        faces = []
        for direction, face in element.get("faces", {}).items():
            ref = face.get("resolved") or face.get("texture", "")
            while ref.startswith("#"):
                ref = textures[ref[1:]]
            path = texture_path(ref)
            key = str(path.relative_to(ASSETS))
            used[key] = png_data(path)
            faces.append({
                "direction": direction,
                "vertices": FACE_VERTICES[direction](element["from"], element["to"]),
                "normal": NORMALS[direction],
                "texture": key,
            })
        normalized.append({"faces": faces})
    return {"id": model_id, "label": model_id.replace("_", " ").title(), "elements": normalized}, used


def main() -> None:
    models = []
    images: dict[str, str] = {}
    for model_id in MODEL_IDS:
        model, used = load_model(model_id)
        models.append(model)
        images.update(used)

    gui_path = TEXTURES / "gui/primitive_machine.png"
    payload = json.dumps({"models": models, "images": images, "gui": png_data(gui_path)})
    html = TEMPLATE.replace("__PAYLOAD__", payload)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(html)
    print(OUTPUT)


TEMPLATE = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AE Primitives Visual Gallery</title>
<style>
:root{color-scheme:dark;--bg:#0d1116;--panel:#151b22;--line:#2a3642;--text:#e8eef4;--muted:#8fa1b2;--accent:#58c7d4}
*{box-sizing:border-box} body{margin:0;background:radial-gradient(circle at 50% -20%,#243443 0,#0d1116 52%);font:14px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--text)}
header{max-width:1240px;margin:0 auto;padding:28px 24px 12px;display:flex;justify-content:space-between;gap:24px;align-items:end}h1{margin:0;font-size:22px;letter-spacing:.04em}header p{margin:5px 0 0;color:var(--muted)}
.controls{display:flex;gap:16px;color:var(--muted);white-space:nowrap}.controls label{display:grid;gap:4px}input{accent-color:var(--accent)}
main{max-width:1240px;margin:auto;padding:16px 24px 32px}.grid{display:grid;grid-template-columns:repeat(3,minmax(260px,1fr));gap:16px}.card,.gui{background:linear-gradient(180deg,#182029,#12181f);border:1px solid var(--line);border-radius:12px;overflow:hidden;box-shadow:0 18px 50px #0005}.card h2{font-size:13px;text-transform:uppercase;letter-spacing:.12em;margin:0;padding:14px 16px;border-bottom:1px solid var(--line);color:#c5d4df}.card canvas{display:block;width:100%;height:auto;background:linear-gradient(#141c24,#10161c)}
.gui{margin-top:16px;padding:18px;display:flex;gap:22px;align-items:center}.gui img{width:512px;height:512px;max-width:50%;object-fit:contain;image-rendering:pixelated;background:#0b0e12}.gui h2{font-size:14px;margin:0 0 8px}.gui p{color:var(--muted);max-width:460px}.key{color:var(--accent)}
@media(max-width:860px){.grid{grid-template-columns:1fr}.controls{display:none}.gui{display:block}.gui img{max-width:100%;width:100%;height:auto}}
</style></head>
<body><header><div><h1>AE PRIMITIVES / VISUAL GALLERY</h1><p>Generated directly from shipped model JSON and PNG textures.</p></div><div class="controls"><label>Yaw<input id="yaw" type="range" min="-70" max="30" value="-35"></label><label>Pitch<input id="pitch" type="range" min="-45" max="5" value="-25"></label></div></header>
<main><section id="grid" class="grid"></section><section class="gui"><img id="gui"><div><h2>Machine interface</h2><p>Nearest-neighbour preview at 2×. Block cards use the same source textures and cuboid coordinates as the game resources.</p><p><span class="key">Drag the sliders</span> to check silhouette and window layering. Minecraft remains the authority for lighting, mipmaps, translucent sorting and the dynamic product renderer.</p></div></section></main>
<script>
const DATA=__PAYLOAD__; const loaded={};
function loadImages(){return Promise.all(Object.entries(DATA.images).map(([k,v])=>new Promise(r=>{const i=new Image;i.onload=()=>{loaded[k]=i;r()};i.src=v}))) }
function rot(p,yaw,pitch){let x=p[0]-8,y=p[1]-8,z=p[2]-8;const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);const rx=x*cy-z*sy,rz=x*sy+z*cy;return [rx,y*cp-rz*sp,y*sp+rz*cp]}
function rotN(n,yaw,pitch){const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);const rx=n[0]*cy-n[2]*sy,rz=n[0]*sy+n[2]*cy;return [rx,n[1]*cp-rz*sp,n[1]*sp+rz*cp]}
function render(canvas,model){const ctx=canvas.getContext('2d');const dpr=devicePixelRatio||1,w=canvas.clientWidth||380,h=w*.82;canvas.width=w*dpr;canvas.height=h*dpr;ctx.scale(dpr,dpr);ctx.clearRect(0,0,w,h);const yaw=+document.querySelector('#yaw').value*Math.PI/180,pitch=+document.querySelector('#pitch').value*Math.PI/180,scale=Math.min(w,h)*.044,cx=w*.5,cy=h*.52;let faces=[];
for(const el of model.elements)for(const f of el.faces){const n=rotN(f.normal,yaw,pitch);if(n[2]>=-.015)continue;const pts=f.vertices.map(p=>rot(p,yaw,pitch));faces.push({...f,pts,depth:pts.reduce((a,p)=>a+p[2],0)/4,shade:f.direction==='up'?.02:(f.direction==='east'||f.direction==='west')?.18:.08})}faces.sort((a,b)=>b.depth-a.depth);
ctx.imageSmoothingEnabled=false;for(const f of faces){const p=f.pts.map(q=>[cx+q[0]*scale,cy-q[1]*scale]),img=loaded[f.texture];ctx.save();ctx.beginPath();ctx.moveTo(...p[0]);for(let i=1;i<4;i++)ctx.lineTo(...p[i]);ctx.closePath();ctx.clip();ctx.transform((p[1][0]-p[0][0])/img.width,(p[1][1]-p[0][1])/img.width,(p[3][0]-p[0][0])/img.height,(p[3][1]-p[0][1])/img.height,p[0][0],p[0][1]);ctx.drawImage(img,0,0);ctx.restore();if(f.shade){ctx.save();ctx.fillStyle=`rgba(0,0,0,${f.shade})`;ctx.beginPath();ctx.moveTo(...p[0]);for(let i=1;i<4;i++)ctx.lineTo(...p[i]);ctx.closePath();ctx.fill();ctx.restore()}}
ctx.strokeStyle='#ffffff20';ctx.strokeRect(cx-8*scale,cy-8*scale,16*scale,16*scale)}
function renderAll(){document.querySelectorAll('canvas[data-i]').forEach(c=>render(c,DATA.models[+c.dataset.i]))}
loadImages().then(()=>{const grid=document.querySelector('#grid');DATA.models.forEach((m,i)=>{const card=document.createElement('article');card.className='card';card.innerHTML=`<h2>${m.label}</h2><canvas data-i="${i}"></canvas>`;grid.appendChild(card)});document.querySelector('#gui').src=DATA.gui;renderAll();document.querySelectorAll('input').forEach(x=>x.addEventListener('input',renderAll));addEventListener('resize',renderAll)})
</script></body></html>'''

if __name__ == "__main__":
    main()
