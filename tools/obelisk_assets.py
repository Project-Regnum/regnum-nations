#!/usr/bin/env python3
"""Author the obelisk assets. Development only: Python 3 + Pillow, no mod dependencies.

Geometry uses model units (16 = one block). Textures are drawn with deliberate
pixel clusters, never random noise. The .bbmodel files embed every texture.
"""
import base64
import copy
import json
from pathlib import Path
import uuid

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"
ART = ROOT / "art/obelisks"
SIDES = ("north", "east", "south", "west", "up", "down")


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


PALETTES = {
    "city": dict(stone=["#626e70", "#768080", "#515d62", "#89918a"],
                 base=["#444e54", "#58646a", "#333d44", "#697375"],
                 metal=["#987448", "#bb975d", "#584b38", "#d0af71"],
                 rune=["#303d43", "#4a575b", "#a98245", "#d6b36a"],
                 core=["#bf853e", "#e0ac58", "#775330", "#ffe1a0"]),
    "kingdom": dict(stone=["#333e49", "#46515b", "#262e38", "#56616b"],
                    base=["#292f38", "#3b444e", "#1d242d", "#4b535c"],
                    metal=["#ae8645", "#d2ae63", "#685330", "#f0d28a"],
                    rune=["#202c3d", "#34465b", "#597da1", "#91b6cf"],
                    core=["#386893", "#6c9fbe", "#244261", "#a0cbd8"]),
}


def texture(kind, material):
    colors = PALETTES[kind][material]
    im = Image.new("RGB", (16, 16), colors[0])
    d = ImageDraw.Draw(im)
    if material in ("stone", "base"):
        # Large contiguous chips and bedding planes, not per-pixel dithering.
        for box in [(1, 2, 5, 3), (9, 1, 13, 2), (4, 7, 9, 8), (1, 12, 4, 13), (11, 11, 14, 12)]:
            d.rectangle(box, fill=colors[1])
        for line in [[(0, 5), (5, 5), (6, 6)], [(10, 6), (15, 6)], [(6, 13), (12, 13), (13, 14)]]:
            d.line(line, fill=colors[2])
        if material == "base":
            d.line([(0, 0), (15, 0)], fill=colors[3])
            d.line([(0, 15), (15, 15)], fill=colors[2])
            d.line([(0, 8), (15, 8)], fill=colors[2])
            d.line([(7, 1), (7, 7)], fill=colors[2])
            d.line([(3, 9), (3, 14)], fill=colors[2])
        else:
            d.line([(0, 0), (15, 0)], fill=colors[1])
            d.line([(0, 0), (0, 15)], fill=colors[1])
            d.line([(15, 0), (15, 15)], fill=colors[2])
    elif material == "metal":
        d.rectangle((0, 0, 15, 15), outline=colors[2])
        d.line([(1, 14), (1, 1), (14, 1)], fill=colors[3])
        d.line([(3, 3), (12, 3)], fill=colors[1])
        d.line([(14, 3), (14, 14), (3, 14)], fill=colors[2])
        d.rectangle((4, 6, 6, 8), fill=colors[1])
        d.rectangle((10, 10, 12, 11), fill=colors[2])
    elif material == "rune":
        d.rectangle((0, 0, 15, 15), outline=colors[1])
        # Two angular, intentionally fictional glyphs in a deep carved recess.
        strokes = [[(7, 4), (7, 7), (9, 6)], [(7, 5), (6, 4)],
                   [(6, 9), (8, 8), (9, 9), (8, 11), (6, 9)]]
        for s in strokes:
            d.line([(x+1, y+1) for x, y in s], fill="#101c27", width=2)
            d.line(s, fill=colors[2])
        d.point((7, 5), fill=colors[3]); d.point((8, 10), fill=colors[3])
    else:
        d.polygon([(0, 0), (7, 0), (7, 15), (0, 10)], fill=colors[1])
        d.polygon([(8, 0), (15, 0), (15, 15), (8, 15)], fill=colors[2])
        d.polygon([(2, 2), (6, 2), (6, 10), (4, 8)], fill=colors[3])
        d.line([(8, 1), (13, 6), (13, 11)], fill=colors[0])
        d.point((3, 12), fill=colors[3])
    path = ASSETS / f"regnum-nations/textures/block/obelisk/{kind}_{material}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    return path


def box(name, a, b, material, group, angle=None):
    e = {"name": name, "from": a, "to": b, "faces": {}, "_group": group}
    dx, dy, dz = [b[i]-a[i] for i in range(3)]
    for side in SIDES:
        w, h = (dx, dz) if side in ("up", "down") else (dz if side in ("east", "west") else dx, dy)
        # One texel per model unit, repeat only on deliberately separate courses.
        u, v = max(0, (16-w)/2), max(0, (16-h)/2)
        e["faces"][side] = {"uv": [u, v, u+w, v+h], "texture": "#"+material}
    if angle:
        axis, degrees, origin = angle
        e["rotation"] = {"angle": degrees, "axis": axis, "origin": origin, "rescale": False}
    return e


def geometry(kind):
    es = []
    def add(n, a, b, mat, group, angle=None): es.append(box(n, a, b, mat, group, angle))
    city = kind == "city"
    # The base exactly fills the preserved full-block collision footprint.
    add("Foundation / dressed lower course", [0,0,0], [16,2,16], "base", "01 Foundation")
    add("Plinth / shadow reveal", [1,2,1], [15,3,15], "stone", "01 Foundation")
    add("Plinth / bronze string course", [1,3,1], [15,4,15], "metal", "01 Foundation")
    add("Plinth / bevel step", [2,4,2], [14,6,14], "base", "01 Foundation")
    add("Plinth / upper course", [3,6,3], [13,8,13], "stone", "01 Foundation")
    if city:
        add("Shaft / lower dressed stone", [4,8,4], [12,16,12], "stone", "02 Pillar")
        add("Shaft / taper shoulder", [4.5,16,4.5], [11.5,18,11.5], "stone", "02 Pillar")
        add("Shaft / upper dressed stone", [5,18,5], [11,24,11], "stone", "02 Pillar")
        for x in (3.5,11):
            for z in (3.5,11): add(f"Bronze foot clasp {x}/{z}", [x,6,z], [x+1.5,11,z+1.5], "metal", "03 Bronze fittings")
        for side in ("north", "south", "east", "west"):
            if side in ("north", "south"):
                z = 3.875 if side == "north" else 12
                add(f"Civic inscription / {side}", [6,9,z], [10,16,z+.125], "rune", "04 Inscriptions")
            else:
                x = 3.875 if side == "west" else 12
                add(f"Civic inscription / {side}", [x,9,6], [x+.125,16,10], "rune", "04 Inscriptions")
        add("Lantern / bronze sill", [4.5,24,4.5], [11.5,25,11.5], "metal", "05 Lantern")
        for x in (5,10):
            for z in (5,10): add(f"Lantern upright {x}/{z}", [x,25,z], [x+1,29,z+1], "metal", "05 Lantern")
        add("Amber heart", [6.5,25.75,6.5], [9.5,28.75,9.5], "core", "05 Lantern", ("y",45,[8,27.25,8]))
        add("Lantern / slate lintel", [4.5,29,4.5], [11.5,30,11.5], "stone", "05 Lantern")
        add("Lantern / bronze cap", [5.5,30,5.5], [10.5,31,10.5], "metal", "05 Lantern")
        add("Lantern / finial", [7,31,7], [9,32,9], "metal", "05 Lantern")
    else:
        for lo,hi,y0,y1 in [(3.5,12.5,8,16),(4,12,16,24),(4.5,11.5,24,30)]:
            add(f"Royal shaft / course {y0}", [lo,y0,lo], [hi,y1,hi], "stone", "02 Pillar")
        for x in (3.25,11.5):
            for z in (3.25,11.5):
                add(f"Gilded lower rib {x}/{z}", [x,7,z], [x+1.25,16,z+1.25], "metal", "03 Gilded ribs")
        for x in (3.75,11):
            for z in (3.75,11):
                add(f"Gilded middle rib {x}/{z}", [x,16,z], [x+1.25,24,z+1.25], "metal", "03 Gilded ribs")
        for x in (4.25,10.5):
            for z in (4.25,10.5):
                add(f"Gilded upper rib {x}/{z}", [x,24,z], [x+1.25,29,z+1.25], "metal", "03 Gilded ribs")
        for side in ("north", "south", "east", "west"):
            if side in ("north", "south"):
                z = 3.875 if side == "north" else 12
                add(f"Royal inscription / {side}", [6,17,z], [10,24,z+.125], "rune", "04 Inscriptions")
            else:
                x = 3.875 if side == "west" else 12
                add(f"Royal inscription / {side}", [x,17,6], [x+.125,24,10], "rune", "04 Inscriptions")
        add("Capital / shadow band", [3.5,29,3.5], [12.5,30,12.5], "base", "05 Crown")
        add("Capital / wide gold collar", [2.5,30,2.5], [13.5,31.5,13.5], "metal", "05 Crown")
        add("Capital / blackstone abacus", [2,31.5,2], [14,33,14], "base", "05 Crown")
        add("Crown / gold seat", [3,33,3], [13,34,13], "metal", "05 Crown")
        # Four broad square prongs flare out above a narrower stem; the gap is
        # real geometry, not transparency. Crown frame remains inside footprint.
        for x,z in ((7,2.5),(11.5,7),(7,11.5),(2.5,7)):
            add(f"Crown / dark upright {x}/{z}", [x,34,z], [x+2,40,z+2], "stone", "05 Crown")
            add(f"Crown / gilded prong {x}/{z}", [x-.5,40,z-.5], [x+2.5,44,z+2.5], "metal", "05 Crown")
            add(f"Crown / square tooth {x}/{z}", [x,44,z], [x+2,46,z+2], "metal", "05 Crown")
        add("Core / levitation seat", [6,34,6], [10,35,10], "base", "06 Royal heart")
        add("Core / lower gold seal", [6.5,36.5,6.5], [9.5,37.25,9.5], "metal", "06 Royal heart", ("y",45,[8,37,8]))
        add("Core / floating sapphire", [6,37.25,6], [10,42.25,10], "core", "06 Royal heart", ("y",45,[8,39.75,8]))
        add("Core / upper facet", [6.75,42.25,6.75], [9.25,44,9.25], "core", "06 Royal heart", ("y",45,[8,43,8]))
        add("Core / suspended gold sigil", [7,45.25,7], [9,47.25,9], "metal", "06 Royal heart", ("y",45,[8,46.25,8]))
    return es


def clean(e, offset=0, scale=1):
    e = copy.deepcopy(e)
    e.pop("_group")
    for key in ("from", "to"):
        e[key] = [round(v*scale,6) for v in e[key]]
        e[key][1] += offset
    if "rotation" in e:
        e["rotation"]["origin"] = [round(v*scale,6) for v in e["rotation"]["origin"]]
        e["rotation"]["origin"][1] += offset
    return e


def display(height, scale=1):
    # Explicit transforms center the entire monument, including its upper half.
    center = height/2-8
    return {
        "gui": {"rotation": [25,210,0], "translation": [0,-center*.48,0], "scale": [.48]*3},
        "ground": {"rotation": [0,0,0], "translation": [0,3,0], "scale": [.25]*3},
        "fixed": {"rotation": [0,0,0], "translation": [0,-center*.45,0], "scale": [.45]*3},
        "thirdperson_righthand": {"rotation": [75,45,0], "translation": [0,2.5,0], "scale": [.27]*3},
        "thirdperson_lefthand": {"rotation": [75,225,0], "translation": [0,2.5,0], "scale": [.27]*3},
        "firstperson_righthand": {"rotation": [0,45,0], "translation": [0,1,0], "scale": [.35]*3},
        "firstperson_lefthand": {"rotation": [0,225,0], "translation": [0,1,0], "scale": [.35]*3},
    }


def blockbench(kind, es, paths):
    texture_ids = {m: str(i) for i,m in enumerate(paths)}
    textures = []
    for material, path in paths.items():
        textures.append({"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"regnum/{kind}/{material}")),
                         "name": path.name, "id": texture_ids[material], "width": 16, "height": 16,
                         "uv_width": 16, "uv_height": 16, "particle": material == "stone",
                         "mode": "bitmap", "saved": True,
                         "source": "data:image/png;base64,"+base64.b64encode(path.read_bytes()).decode()})
    elements, groups = [], {}
    for index, e in enumerate(es):
        out = clean(e)
        out["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"regnum/{kind}/element/{index}"))
        out.update(type="cube", autouv=0, color=index%8, box_uv=False, rescale=False, locked=False,
                   render_order="default", allow_mirror_modeling=True, origin=[8,0,8])
        if "rotation" in out:
            rotation = out.pop("rotation"); out["origin"] = rotation["origin"]
            out["rotation"] = [rotation["angle"] if a == rotation["axis"] else 0 for a in "xyz"]
        for face in out["faces"].values(): face["texture"] = int(texture_ids[face["texture"][1:]])
        elements.append(out); groups.setdefault(e["_group"], []).append(out["uuid"])
    save_json(ART/f"{kind}_obelisk.bbmodel", {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": f"Regnum Nations / {kind.title()} Obelisk", "model_identifier": f"{kind}_obelisk",
        "visible_box": [1,3,0], "resolution": {"width":16,"height":16},
        "elements": elements, "outliner": [{"name":n,"origin":[8,0,8],"children":ids,
            "uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,f"regnum/{kind}/{n}"))} for n,ids in groups.items()],
        "textures": textures,
    })


def generate():
    for kind in PALETTES:
        paths = {m: texture(kind,m) for m in PALETTES[kind]}
        es = geometry(kind)
        textures = {m:f"regnum-nations:block/obelisk/{kind}_{m}" for m in paths}
        textures["particle"] = "#stone"
        model = {"credit":"Regnum Nations original obelisk art", "parent":"minecraft:block/block",
                 "ambientocclusion":True, "textures":textures, "display":display(32)}
        base = ASSETS/f"regnum-nations/models/block/{kind}_obelisk.json"
        glow = [e for e in es if e['faces']['north']['texture'] == '#core']
        solid = [e for e in es if e not in glow]
        glow_model = model | {"ambientocclusion":False, "elements":[clean(e,-16 if kind == 'kingdom' else 0) | {"shade":False} for e in glow]}
        save_json(base.with_name(f"{kind}_obelisk_glow.json"), glow_model)
        if kind == "city":
            save_json(base, model | {"elements":[clean(e) for e in solid]})
            save_json(ASSETS/"regnum-nations/models/item/city_obelisk.json", model | {"elements":[clean(e) for e in es]})
            save_json(ASSETS/"project-reigns/models/item/city_obelisk.json", {"parent":"regnum-nations:item/city_obelisk"})
        else:
            # Split at the existing shaft course seam; upper JSON stays within
            # vanilla [-16,32]. Renderer moves it up exactly one block.
            lower = [e for e in solid if e["to"][1] <= 16]
            upper = [e for e in solid if e["to"][1] > 16]
            save_json(base, model | {"elements":[clean(e) for e in lower]})
            save_json(base.with_name("kingdom_obelisk_crown.json"), model | {"elements":[clean(e,-16) for e in upper]})
            # Separate static inventory model: same proportions, uniformly 2/3.
            save_json(ASSETS/"regnum-nations/models/item/kingdom_obelisk.json",
                      model | {"elements":[clean(e,scale=2/3) for e in es]})
            save_json(ASSETS/"project-reigns/models/item/kingdom_obelisk.json",
                      {"parent":"regnum-nations:item/kingdom_obelisk"})
        blockbench(kind,es,paths)
        print(f"{kind}: {len(es)} cuboids, {len(es)*6} quads maximum, height {max(e['to'][1] for e in es)/16:g} blocks")


if __name__ == "__main__":
    generate()
