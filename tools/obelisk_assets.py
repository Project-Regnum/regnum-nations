#!/usr/bin/env python3
"""Export authoritative Blockbench sources to Minecraft assets; never modify sources.

Standard library only. Run with --check to verify exports without writing files.
The historical procedural authoring recipe has deliberately been retired.
"""
import argparse
import base64
import copy
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets'
SOURCE = ROOT / 'art/obelisks'


def json_bytes(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def read_source(kind):
    path = SOURCE / f'{kind}_obelisk.bbmodel'
    raw = path.read_bytes()
    model = json.loads(raw)
    groups = {g['uuid']: g for g in model.get('groups', [])}
    membership = {}

    def visit(nodes, ancestors=()):
        for node in nodes:
            if isinstance(node, str):
                membership[node] = ancestors
            else:
                group = groups.get(node['uuid'], node)
                # Pivots alone do not transform geometry. Nonzero group Euler
                # rotations need flattening, so fail instead of discarding edits.
                if any(group.get('rotation', [0, 0, 0])):
                    raise ValueError(f"{path.name}: flatten rotated group {group.get('name')!r} before export")
                visit(node.get('children', []), ancestors + (group,))
    visit(model['outliner'])
    textures, images = {}, {}
    for index, texture in enumerate(model['textures']):
        name = texture['name']
        if not name.startswith(kind+'_') or not name.endswith('.png') or '/' in name:
            raise ValueError(f'Unsupported texture filename: {name}')
        data = base64.b64decode(texture['source'].split(',', 1)[1], validate=True)
        if data[:8] != b'\x89PNG\r\n\x1a\n':
            raise ValueError(f'Non-PNG texture: {name}')
        width, height = struct.unpack('>II', data[16:24])
        if width != height or width not in (16, 32, 64):
            raise ValueError(f'Unsupported texture size: {name} {width}x{height}')
        material = name[len(kind)+1:-4]
        textures[index] = (material, texture.get('uv_width', 16), texture.get('uv_height', 16))
        images[ASSETS / f'regnum-nations/textures/block/obelisk/{name}'] = data
    elements, crystals, crystal_ids = [], [], []
    for cube in model['elements']:
        ancestors = membership[cube['uuid']]
        if not cube.get('export', True) or any(not g.get('export', True) for g in ancestors):
            continue
        if cube.get('type', 'cube') != 'cube' or cube.get('box_uv', False):
            raise ValueError(f"Unsupported element type/UV mode: {cube['name']}")
        element = {'name': cube['name'], 'from': list(cube['from']), 'to': list(cube['to']), 'faces': {}}
        if cube.get('inflate', 0):
            element['from'] = [v-cube['inflate'] for v in element['from']]
            element['to'] = [v+cube['inflate'] for v in element['to']]
        angles = [(axis, angle) for axis, angle in zip('xyz', cube.get('rotation', [0, 0, 0])) if angle]
        if angles:
            if len(angles) != 1 or angles[0][1] not in (-45, -22.5, 22.5, 45):
                raise ValueError(f"Unsupported vanilla rotation: {cube['name']} {angles}")
            axis, angle = angles[0]
            element['rotation'] = {'angle': angle, 'axis': axis, 'origin': list(cube['origin']),
                                   'rescale': cube.get('rescale', False)}
        if 'shade' in cube:
            element['shade'] = cube['shade']
        materials = set()
        for side, source_face in cube['faces'].items():
            index = source_face.get('texture')
            # False disables a face; integer zero still means texture 0.
            if index is None or isinstance(index, bool):
                continue
            material, uw, uh = textures[int(index)]
            materials.add(material)
            uv = [v*16/(uw if i%2 == 0 else uh) for i, v in enumerate(source_face['uv'])]
            face = {'uv': uv, 'texture': '#'+material}
            for key in ('rotation', 'tintindex'):
                if key in source_face:
                    face[key] = source_face[key]
            element['faces'][side] = face
        if any('crystal' in g.get('name', '').lower() for g in ancestors) and materials == {'core'}:
            crystals.append(element)
            crystal_ids.append(cube['uuid'])
        else:
            elements.append(element)
    if len(crystals) != 1:
        raise ValueError(f'{kind}: expected one crystalline cube, found {len(crystals)}; socket must stay static')
    crystal_cube = next(e for e in model['elements'] if e['uuid'] == crystal_ids[0])
    pivot = crystal_cube.get('origin', [(a+b)/2 for a,b in zip(crystal_cube['from'],crystal_cube['to'])])
    return model, elements, crystals, images, list(pivot), crystal_ids, hashlib.sha256(raw).hexdigest()


def transformed(element, offset=0, scale=1):
    element = copy.deepcopy(element)
    for key in ('from', 'to'):
        element[key] = [round(v*scale, 7) for v in element[key]]
        element[key][1] += offset
    if 'rotation' in element:
        origin = [round(v*scale, 7) for v in element['rotation']['origin']]
        origin[1] += offset
        element['rotation']['origin'] = origin
    for key in ('from', 'to'):
        if not all(-16 <= v <= 32 for v in element[key]):
            raise ValueError(f"Export exceeds vanilla bounds: {element['name']}")
    return element


def exports():
    files, animation = {}, {}
    for kind in ('city', 'kingdom'):
        source, solid, crystal, images, pivot, ids, digest = read_source(kind)
        files.update(images)
        # Preserve existing item transforms unless the artist supplies new ones.
        previous = json.loads((ASSETS/f'regnum-nations/models/item/{kind}_obelisk.json').read_text())
        textures = {t['name'][len(kind)+1:-4]: f"regnum-nations:block/obelisk/{t['name'][:-4]}" for t in source['textures']}
        textures['particle'] = '#stone'
        model = {'credit': 'Exported from authoritative Blockbench source', 'parent': 'minecraft:block/block',
                 'ambientocclusion': True, 'textures': textures, 'display': source.get('display', previous['display'])}
        base = ASSETS/f'regnum-nations/models/block/{kind}_obelisk.json'
        offset = -16 if kind == 'kingdom' else 0
        lower = [e for e in solid if e['to'][1] <= 16] if kind == 'kingdom' else solid
        files[base] = json_bytes(model | {'elements': [transformed(e) for e in lower]})
        if kind == 'kingdom':
            files[base.with_name('kingdom_obelisk_crown.json')] = json_bytes(model | {
                'elements': [transformed(e, offset) for e in solid if e['to'][1] > 16]})
        files[base.with_name(f'{kind}_obelisk_glow.json')] = json_bytes(model | {
            'ambientocclusion': False, 'elements': [transformed(e, offset) | {'shade': False} for e in crystal]})
        files[ASSETS/f'regnum-nations/models/item/{kind}_obelisk.json'] = json_bytes(model | {
            'elements': [transformed(e, scale=2/3 if kind == 'kingdom' else 1) for e in solid+crystal]})
        local_pivot = list(pivot)
        local_pivot[1] += offset
        animation[kind] = {'source_sha256': digest, 'crystal_uuids': ids, 'pivot': local_pivot,
                           'rotation_ticks': 800 if kind == 'city' else 600,
                           'bob_ticks': 100 if kind == 'city' else 120,
                           'bob_amplitude': .25 if kind == 'city' else .4,
                           'brightness_min': .94 if kind == 'city' else .92}
    files[ASSETS/'regnum-nations/obelisk_animation.json'] = json_bytes(animation)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify exports against sources without writing')
    args = parser.parse_args()
    expected = exports()
    mismatches = [str(p.relative_to(ROOT)) for p,data in expected.items() if not p.exists() or p.read_bytes() != data]
    if args.check:
        if mismatches:
            raise SystemExit('Out-of-date exports:\n'+'\n'.join(mismatches))
        print('PASS: all geometry, rotations, UVs, textures and pivots match authoritative Blockbench sources')
    else:
        for path,data in expected.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        print(f'Exported {len(expected)} runtime files; editable sources were only read')


if __name__ == '__main__':
    main()
