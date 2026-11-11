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
TEXTURE_SIZE = 64


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


PALETTES = {
    "city": dict(stone=["#707273", "#828481", "#5f6265", "#9a9b91"],
                 base=["#515258", "#64656a", "#41434a", "#7a7b7b"],
                 metal=["#9b723d", "#bb8d48", "#70512e", "#d5ac64"],
                 rune=["#3f424a", "#54575e", "#c88f35", "#e7b753"],
                 core=["#d68a18", "#f9b638", "#a86013", "#ffe39b"]),
    "kingdom": dict(stone=["#38353e", "#48434e", "#2c2932", "#5b5560"],
                    base=["#2d2b34", "#3c3944", "#24232b", "#504b56"],
                    metal=["#b28a42", "#d5ad5b", "#806130", "#e9c87a"],
                    rune=["#292832", "#3e3b48", "#3b76c7", "#73a6df"],
                    core=["#2e65c5", "#549bea", "#193c91", "#9dcdfa"]),
}


def texture(kind, material):
    colors = PALETTES[kind][material]
    im = Image.new("RGB", (TEXTURE_SIZE, TEXTURE_SIZE), colors[0])
    d = ImageDraw.Draw(im)
    if material in ("stone", "base"):
        # Hand-structured stone courses with connected chips. Small clusters
        # sit inside larger slabs rather than independent noisy pixels.
        for row in range(8):
            y = row*8
            offset = 6 if row%2 else 0
            for column in range(-1,6):
                x = column*12+offset
                d.rectangle((x+1,y+1,x+10,y+6), fill=colors[0 if (row+column)%3 else 2])
                d.line([(x+2,y+1),(x+8,y+1)], fill=colors[1])
                d.rectangle((x+3,y+3,x+5,y+4), fill=colors[1])
                d.line([(x+8,y+5),(x+10,y+5)], fill=colors[2])
                if material == "base":
                    d.line([(x,y+7),(x+11,y+7)], fill=colors[2])
                    d.line([(x,y+2),(x,y+6)], fill=colors[2])
        for x,y in [(8,13),(23,28),(34,38),(47,21),(19,46),(41,52)]:
            d.line([(x,y),(x+3,y),(x+4,y+1)], fill=colors[3])
    elif material == "metal":
        for y in range(2,64,7):
            for x in range((y//7%2)*4,64,9):
                d.rectangle((x,y,x+2,y+1), fill=colors[1])
                d.line([(x+4,y+3),(x+5,y+3)], fill=colors[2])
        # Central ridges remain visible on narrow trim UVs as well as caps.
        for line in ([(30,0),(30,63)],[(0,30),(63,30)]): d.line(line,fill=colors[1])
        for line in ([(33,0),(33,63)],[(0,33),(63,33)]): d.line(line,fill=colors[2])
        d.point((31,31),fill=colors[3])
    elif material == "rune":
        d.line([(27,0),(27,63)],fill=colors[1])
        d.line([(37,0),(37,63)],fill=colors[1])
        # A long chevron / diamond inscription, matching the reference's rhythm.
        strokes = [[(32,18),(32,23)],[(29,24),(32,21),(35,24)],
                   [(29,28),(32,25),(35,28)],
                   [(28,33),(32,29),(36,33),(32,37),(28,33)],
                   [(30,33),(32,31),(34,33),(32,35),(30,33)],
                   [(29,40),(32,37),(35,40)],[(32,40),(32,47)]]
        for s in strokes:
            d.line([(x+1, y+1) for x, y in s], fill="#17171e", width=2)
            d.line(s, fill=colors[2])
        d.point((32,29), fill=colors[3]); d.point((32,37), fill=colors[3])
    else:
        d.polygon([(0,0),(31,0),(31,63),(0,48)],fill=colors[1])
        d.polygon([(32,0),(63,0),(63,63),(32,63)],fill=colors[2])
        d.polygon([(22,21),(30,25),(30,36),(26,32)],fill=colors[3])
        d.polygon([(33,25),(43,34),(33,41)],fill=colors[0])
        d.line([(24,38),(28,42),(31,39)],fill=colors[1])
        d.line([(26,25),(29,26),(29,30)],fill=colors[3])
    path = ASSETS / f"regnum-nations/textures/block/obelisk/{kind}_{material}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    return path


def box(name, a, b, material, group, angle=None):
    e = {"name": name, "from": a, "to": b, "faces": {}, "_group": group}
    dx, dy, dz = [b[i]-a[i] for i in range(3)]
    for side in SIDES:
        w, h = (dx, dz) if side in ("up", "down") else (dz if side in ("east", "west") else dx, dy)
        # Two pixels per model unit, including wide plinths (64px / 16 UV units).
        w, h = w*.5, h*.5
        u, v = (16-w)/2, (16-h)/2
        e["faces"][side] = {"uv": [u, v, u+w, v+h], "texture": "#"+material}
    if angle:
        axis, degrees, origin = angle
        e["rotation"] = {"angle": degrees, "axis": axis, "origin": origin, "rescale": False}
    return e


def geometry(kind):
    es = []
    def add(n, a, b, mat, group, angle=None):
        es.append(box(n, a, b, mat, group, angle))
    city = kind == 'city'
    # Broad decorative plinths overhang the unchanged one-block collision box.
    courses = ([(-2,18,0,2.5,'base'),(-1,17,2.5,4,'stone'),
                (0,16,4,5.5,'base'),(-.5,16.5,5.5,6.25,'stone'),
                (1,15,6.25,7.5,'stone'),(2,14,7.5,9,'base')]
               if city else
               [(-4,20,0,3,'base'),(-3,19,3,4.5,'stone'),
                (-2,18,4.5,6.5,'base'),(-1,17,6.5,8.5,'stone'),
                (-2,18,8.5,9.5,'metal'),(-1,17,9.5,11,'base'),
                (0,16,11,12,'stone')])
    for index,(lo,hi,y0,y1,mat) in enumerate(courses):
        add(f'Foundation / course {index+1}',[lo,y0,lo],[hi,y1,hi],mat,'01 Foundation')
    for x in ((-1,14) if city else (-3,16)):
        for z in ((-1,14) if city else (-3,16)):
            add(f'Foundation / metal corner boss {x}/{z}',[x,2.5,z],[x+3,4.5,z+3],
                'metal','01 Foundation')
    # Each course retreats visibly. Buttresses reinforce that taper in depth.
    shaft = ([(3,13,9,13),(3.75,12.25,13,17),(4.5,11.5,17,21)] if city else
             [(1.5,14.5,12,18),(2.5,13.5,18,24),(3.5,12.5,24,30)])
    for lo,hi,y0,y1 in shaft:
        add(f'Shaft / tapered course {y0}',[lo,y0,lo],[hi,y1,hi],'stone','02 Tapered body')
    tiers = ([(1.75,12.25,8,11.5),(2.5,11.5,11.5,15),(3.25,10.75,15,18.5),
              (3.75,10.25,18.5,21.5)] if city else
             [(-.25,14.25,11,15),(1,13,15,20),(2,12,20,25),(3,11,25,30)])
    for left,right,y0,y1 in tiers:
        for x in (left,right):
            for z in (left,right):
                add(f'Buttress / stone tier {y0} at {x}/{z}',[x,y0,z],[x+2,y1,z+2],
                    'stone','03 Corner buttresses')
                if not city and y0>=15:
                    # Metal wraps project beyond the stone instead of hiding in it.
                    add(f'Buttress / gilded edge {y0} at {x}/{z}',
                        [x-.125,y0,z-.125],[x+.625,y1,z+2.125],
                        'metal','04 Metal fittings')
    for x in ((3.5,10.25) if city else (3,10.5)):
        for z in ((3.5,10.25) if city else (3,10.5)):
            y0,y1 = (20,22) if city else (27,29)
            add(f'Shoulder / architectural clasp {x}/{z}',[x-.25,y0,z-.25],
                [x+2.25,y1,z+2.25],'metal','04 Metal fittings')
    # Recessed long inscriptions follow the taper instead of floating on a flat
    # vertical decal. Continuous UVs preserve the chevron/diamond rhythm.
    total_bottom,total_top = (9,21) if city else (12,30)
    for lo,hi,y0,y1 in shaft:
        for side in ('north','east','south','west'):
            if side in ('north','south'):
                z = lo-.1875 if side=='north' else hi
                a,b = [5.75,y0,z],[10.25,y1,z+.1875]
            else:
                x = lo-.1875 if side=='west' else hi
                a,b = [x,y0,5.75],[x+.1875,y1,10.25]
            e=box(f'Carved inscription / {side} course {y0}',a,b,'rune','05 Inscriptions')
            # Fit the complete 24/36-pixel inscription across its three courses.
            v0=8-(total_top-total_bottom)*.25+(total_top-y1)*.5
            v1=v0+(y1-y0)*.5
            e['faces'][side]['uv']=[6.875,v0,9.125,v1]
            for face in SIDES:
                if face!=side: e['faces'][face]['texture']='#stone'
            es.append(e)
    if city:
        add('Shoulder / carved stone capital',[3.25,21,3.25],[12.75,22.5,12.75],
            'stone','06 Amber lantern')
        add('Lantern / bronze sill',[2,22.5,2],[14,23.5,14],'metal','06 Amber lantern')
        # Thin corner posts leave a generous opening around the large heart.
        for x in (2.5,12.5):
            for z in (2.5,12.5):
                add(f'Lantern / bronze upright {x}/{z}',[x,23.5,z],[x+1,30.5,z+1],
                    'metal','06 Amber lantern')
        add('Lantern / bronze roof',[2,30.5,2],[14,31.25,14],'metal','06 Amber lantern')
        add('Lantern / weathered roof cap',[3,31.25,3],[13,31.75,13],
            'stone','06 Amber lantern')
        add('Lantern / bronze finial',[5,31.75,5],[11,32,11],'metal','06 Amber lantern')
        add('Amber heart / suspended diamond',[5.75,25,5.75],[10.25,29.5,10.25],
            'core','07 Crystal',('z',45,[8,27.25,8]))
    else:
        add('Capital / shadow reveal',[2.5,29.5,2.5],[13.5,30.5,13.5],
            'base','06 Royal crown')
        add('Capital / projecting gold cornice',[0,30.5,0],[16,31.5,16],
            'metal','06 Royal crown')
        add('Capital / blackstone tray',[-.5,31.5,-.5],[16.5,32.5,16.5],
            'base','06 Royal crown')
        add('Capital / upper gold rim',[0,32.5,0],[16,33.25,16],
            'metal','06 Royal crown')
        def arm(side,name,r0,r1,y0,y1,width,material,angle=None):
            if side=='north': a,b=[8-width/2,y0,8-r1],[8+width/2,y1,8-r0]
            elif side=='south': a,b=[8-width/2,y0,8+r0],[8+width/2,y1,8+r1]
            elif side=='east': a,b=[8+r0,y0,8-width/2],[8+r1,y1,8+width/2]
            else: a,b=[8-r1,y0,8-width/2],[8-r0,y1,8+width/2]
            add(f'Crown / {side} {name}',a,b,material,'06 Royal crown',angle)
        for side in ('north','east','south','west'):
            dx,dz={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}[side]
            axis='x' if dz else 'z'
            sign=-1 if side in ('north','east') else 1
            knee=(axis,45*sign,[8+dx*6.5,35.5,8+dz*6.5])
            tip=(axis,-45*sign,[8+dx*9.5,45.25,8+dz*9.5])
            arm(side,'root',4.75,6.75,33.25,35.5,2.25,'metal')
            arm(side,'sloping outward knee',5.5,7.5,35.5,40,2.25,'metal',knee)
            arm(side,'open upright',8.5,10.5,38,45.5,2.25,'metal')
            arm(side,'sloping inward tip',8.5,10.5,45.25,48,2.25,'metal',tip)
            # Dark inlays on both radial faces leave an actual raised gold border.
            arm(side,'outer stone inlay',10.49,10.625,38.75,44.75,1.5,'stone')
            arm(side,'inner stone inlay',8.375,8.51,38.75,44.75,1.5,'stone')
        add('Core / levitation socket',[5.5,33.25,5.5],[10.5,34,10.5],
            'base','07 Crystal')
        add('Sapphire / suspended diamond',[4.75,37.5,4.75],[11.25,44,11.25],
            'core','07 Crystal',('z',45,[8,40.75,8]))
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
                         "name": path.name, "id": texture_ids[material], "width": TEXTURE_SIZE, "height": TEXTURE_SIZE,
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
        "visible_box": [2,3,0], "resolution": {"width":16,"height":16},
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
