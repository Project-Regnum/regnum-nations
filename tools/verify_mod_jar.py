#!/usr/bin/env python3
"""Check the shipped mod's entrypoints, namespaces and resource references (stdlib only)."""
import json
from pathlib import Path
import re
import sys
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


def verify(path):
    with ZipFile(path) as jar:
        names = set(jar.namelist())

        for name in names:
            if name.endswith('.class'):
                assert int.from_bytes(jar.read(name)[6:8], 'big') == 65, f'Non-Java-21 class: {name}'

        def require(name):
            if name not in names:
                raise ValueError(f"Missing packaged resource: {name}")

        def read_json(name):
            require(name)
            return json.loads(jar.read(name))

        def resource(identifier, directory, suffix):
            namespace, value = identifier.split(':', 1)
            if namespace in ('regnum-nations', 'project-reigns'):
                require(f'assets/{namespace}/{directory}/{value}{suffix}')

        metadata = read_json('fabric.mod.json')
        assert metadata['id'] == 'regnum-nations' and metadata['name'] == 'Regnum Nations'
        assert 'project-reigns' in metadata['provides']
        assert '${version}' not in metadata['version']
        assert set(metadata['license']) == {'CC0-1.0', 'MIT'}
        assert set(metadata['depends']) == {'fabricloader', 'minecraft', 'java', 'fabric-api'}
        require('META-INF/licenses/diorama/LICENSE')
        assert 'Copyright (c) 2026 Dara Adedeji' in jar.read('META-INF/licenses/diorama/LICENSE').decode()
        require('THIRD_PARTY_NOTICES.md')
        require('dev/sakhno/regnum/nations/client/terrain/diorama/bake/TileBaker.class')
        assert 'dev/sakhno/regnum/nations/kingdom/AtlasClientChecks.class' not in names
        require(metadata['icon'])
        for entrypoints in metadata['entrypoints'].values():
            for entrypoint in entrypoints:
                require(entrypoint.replace('.', '/') + '.class')
        for mixin in metadata['mixins']:
            config = read_json(mixin if isinstance(mixin, str) else mixin['config'])
            for cls in config.get('mixins', []) + config.get('client', []):
                require((config['package'] + '.' + cls).replace('.', '/') + '.class')
        assert not any(n.startswith('dev/sakhno/reigns/') for n in names)
        assert not any(n.startswith('fixtures/') or n.endswith('Tests.class') for n in names)
        for texture in ('folio_tile', 'ornaments'):
            for suffix in ('.png', '.png.mcmeta'):
                require(f'assets/regnum-nations/textures/gui/atlas/{texture}{suffix}')
        languages = [read_json(f'assets/regnum-nations/lang/{locale}.json') for locale in ('en_us', 'uk_ua')]
        assert languages[0].keys() == languages[1].keys(), 'Locale key sets differ'
        for language in languages:
            for key in ('block.project-reigns.city_obelisk', 'block.project-reigns.kingdom_obelisk',
                        'key.project-reigns.map', 'category.project-reigns'):
                assert key in language, f'Missing compatibility translation: {key}'
        for source_set in ('main', 'client'):
            for source in (ROOT / 'src' / source_set / 'java').rglob('*.java'):
                if source_set == 'main':
                    assert 'net.minecraft.client.' not in source.read_text(), f'Client import in common code: {source}'
                keys = re.findall(r'"((?:message|ui|admin|city|map)\.regnum-nations\.[a-z0-9_.]+)"', source.read_text())
                for key in keys:
                    if not key.endswith('.'):
                        assert key in languages[0], f'Missing translation in {source}: {key}'
        legacy_assets = set()
        for obelisk in ('city_obelisk', 'kingdom_obelisk'):
            state = f'assets/project-reigns/blockstates/{obelisk}.json'
            item = f'assets/project-reigns/models/item/{obelisk}.json'
            legacy_assets.update((state, item))
            assert read_json(state)['variants']['']['model'] == f'regnum-nations:block/{obelisk}'
            assert read_json(item)['parent'] == f'regnum-nations:item/{obelisk}'
            require(f'assets/regnum-nations/models/item/{obelisk}.json')
            require(f'assets/regnum-nations/models/block/{obelisk}.json')
            require(f'assets/regnum-nations/models/block/{obelisk}_glow.json')
            loot = read_json(f'data/project-reigns/loot_table/blocks/{obelisk}.json')
            assert loot['pools'][0]['entries'][0]['name'] == f'project-reigns:{obelisk}'
        for kind, options in {
            'shields': ('heater', 'kite', 'round', 'french', 'bouche'),
            'charges': ('lion', 'eagle', 'wolf', 'stag', 'crown', 'sword', 'fleur_de_lis', 'tower', 'sun', 'star', 'rose')
        }.items():
            for option in options:
                shape = read_json(f'assets/regnum-nations/heraldry/{kind}/{option}.json')
                assert shape['solid'], f'Empty silhouette: {option}'
                for polygons in shape.values():
                    for polygon in polygons:
                        assert len(polygon) >= 3 and all(len(p) == 2 and all(isinstance(v, (int, float)) and 0 <= v <= 100 for v in p) for p in polygon)
                assert f'heraldry.regnum-nations.{"shield" if kind == "shields" else "charge"}.{option}' in languages[0]
        for variant in ('royal_banner', 'wall_royal_banner'):
            blockstate = read_json(f'assets/regnum-nations/blockstates/{variant}.json')
            assert set(blockstate['variants']) == {f'facing={d}' for d in ('north', 'south', 'west', 'east')}
            require(f'assets/regnum-nations/models/block/{variant}.json')
            assert f'block.regnum-nations.{variant}' in languages[0]
        assert read_json('assets/regnum-nations/models/item/royal_banner.json')['parent'] == 'minecraft:builtin/entity'
        recipe = read_json('data/regnum-nations/recipe/royal_banner.json')
        assert recipe['result'] == {'id': 'regnum-nations:royal_banner', 'count': 1}
        assert recipe['pattern'] == ['WWW', 'WWW', ' S ']
        require('dev/sakhno/regnum/nations/block/RoyalBannerBlockEntity.class')
        require('dev/sakhno/regnum/nations/client/heraldry/RoyalBannerRenderer.class')
        require('assets/regnum-nations/models/block/kingdom_obelisk_crown.json')
        require('assets/regnum-nations/obelisk_animation.json')
        assert {n for n in names if n.startswith('assets/project-reigns/') and not n.endswith('/')} == legacy_assets
        assert set(read_json('data/minecraft/tags/block/mineable/pickaxe.json')['values']) == {
            'project-reigns:city_obelisk', 'project-reigns:kingdom_obelisk'}
        for name in names:
            if name.startswith('assets/') and '/models/' in name and name.endswith('.json'):
                model = read_json(name)
                if 'parent' in model:
                    resource(model['parent'], 'models', '.json')
                for texture in model.get('textures', {}).values():
                    if not texture.startswith('#'):
                        resource(texture, 'textures', '.png')
    print(f'Verified mod identity, client isolation, licenses, dependency set, locales, Living Atlas and legacy resource adapters: {path.name}')


if __name__ == '__main__':
    paths = [Path(sys.argv[1])] if len(sys.argv) == 2 else [
        p for p in (ROOT / 'build/libs').glob('regnum-nations-*.jar') if not p.name.endswith('-sources.jar')]
    if len(paths) != 1:
        raise SystemExit('Expected one built mod JAR; run ./gradlew clean build or supply its path.')
    verify(paths[0])
