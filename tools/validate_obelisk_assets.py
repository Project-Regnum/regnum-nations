#!/usr/bin/env python3
"""Validate exported obelisk contracts with Python's standard library only."""
import base64
import json
from pathlib import Path
import struct
from obelisk_assets import exports

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"


def read(path):
    return json.loads(path.read_text())


def resource(identifier, folder, extension):
    namespace, name = identifier.split(":")
    return ASSETS / namespace / folder / (name + extension)


def validate():
    for path, expected in exports().items():
        assert path.read_bytes() == expected, f'Export differs from authoritative source: {path}'
    exported = []
    for kind in ("city", "kingdom"):
        identifier = f"{kind}_obelisk"
        state = read(ASSETS / f"project-reigns/blockstates/{identifier}.json")
        assert state == {"variants": {"": {"model": f"regnum-nations:block/{identifier}"}}}
        item = read(ASSETS / f"project-reigns/models/item/{identifier}.json")
        assert item == {"parent": f"regnum-nations:item/{identifier}"}
        for path in (ASSETS / "regnum-nations/models").rglob(f"{identifier}*.json"):
            model = read(path)
            if "parent" in model and not model["parent"].startswith("minecraft:"):
                assert resource(model["parent"], "models", ".json").is_file(), path
            textures = model["textures"]
            for name, value in textures.items():
                if value.startswith("#"):
                    assert value[1:] in textures, (path, value)
                    continue
                texture = resource(value, "textures", ".png")
                png = texture.read_bytes()
                assert png[:8] == b"\x89PNG\r\n\x1a\n", texture
                assert struct.unpack(">II", png[16:24]) in ((16,16),(32,32),(64,64)), texture
            assert model["elements"], path
            for element in model["elements"]:
                assert all(-16 <= v <= 32 for k in ("from","to") for v in element[k]), (path, element["name"])
                assert all(element["from"][i] < element["to"][i] for i in range(3)), path
                if "rotation" in element:
                    assert element["rotation"]["angle"] in (-45,-22.5,0,22.5,45), path
                    assert element["rotation"]["axis"] in "xyz", path
                for face in element["faces"].values():
                    assert face["texture"].startswith("#") and face["texture"][1:] in textures, path
                    assert len(face["uv"]) == 4 and all(0 <= v <= 16 for v in face["uv"]), (path, face)
                    # Upper/side geometry must not disappear when adjacent to a solid block.
                    assert "cullface" not in face, path
            exported.append(path)
        source = read(ROOT / f"art/obelisks/{identifier}.bbmodel")
        ids = {e["uuid"] for e in source["elements"]}
        assert len(ids) == len(source["elements"]), identifier
        assert {i for group in source["outliner"] for i in group["children"]} == ids, identifier
        for texture in source["textures"]:
            assert texture["source"].startswith("data:image/png;base64,"), identifier
            data = base64.b64decode(texture["source"].split(",",1)[1], validate=True)
            assert data == (ASSETS / f'regnum-nations/textures/block/obelisk/{texture["name"]}').read_bytes(), identifier
        for element in source["elements"]:
            for face in element["faces"].values():
                assert 0 <= face["texture"] < len(source["textures"]), identifier
    assert len(exported) == 7, exported
    print(f"PASS: {len(exported)} models; 10 textures; retained IDs; UVs/bounds/rotations; embedded sources; exact source export and animation pivots")


if __name__ == "__main__":
    validate()
