#!/usr/bin/env python3
"""Upgrade ten disposable schema fixtures through two normal Java 21 server processes each.

Prerequisites: build (exports raw and canonical fixtures) and runHeraldryVisualTest.
Only copies directories beneath build/run; never accepts player-world paths.
"""
import json
from pathlib import Path
import shutil
import uuid
from verify_heraldry_restart import ROOT, Nbt, run_server


def main():
    fixtures=ROOT/'build/run/gameTest/release-migrations'
    inputs=sorted(fixtures.glob('*-input.dat'))
    assert len(inputs)==10, 'Run the full GameTests to export all ten compatibility fixtures'
    worlds=sorted((ROOT/'build/run/heraldryVisualTest/saves').glob('heraldry-review-*'),key=lambda p:p.stat().st_mtime)
    assert worlds and (ROOT/'build/run/heraldryVisualTest/checks/passed.txt').exists(), 'Run the heraldry visual checks first'
    output=ROOT/'build/run'/('releaseRestart-'+uuid.uuid4().hex[:8]); output.mkdir(parents=True)
    passed=[]
    for source in inputs:
        name=source.name.removesuffix('-input.dat'); directory=output/name; directory.mkdir()
        shutil.copytree(worlds[-1],directory/'world')
        saved_file=directory/'world/data/project-reigns-kingdoms.dat'; shutil.copyfile(source,saved_file)
        expected=Nbt.read(fixtures/(name+'-expected.dat'))['data']
        (directory/'eula.txt').write_text('eula=true\n')
        (directory/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=2\n')
        init=directory/'server.init.gradle'
        init.write_text('''allprojects {
    afterEvaluate {
        loom.runs.server.runDir = '''+json.dumps(str(directory))+'''
        tasks.named('runServer', JavaExec) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            standardInput = System.in
            maxHeapSize = '1G'
        }
    }
}
''')
        for number in (1,2):
            print(f'Verifying {name}, dedicated process {number}',flush=True)
            run_server(directory,init,number,None,rename=False)
            actual=Nbt.read(saved_file)['data']
            assert actual==expected, f'{name} process {number}: canonical saved data changed (see {directory})'
        passed.append(name)
        print(f'PASS {name}: upgrade, shutdown, fresh restart, exact canonical NBT including extensions',flush=True)
    (output/'passed.json').write_text(json.dumps({'fixtures':passed,'processes':20},indent=2)+'\n')
    print('PASS all compatibility fixtures:',output,flush=True)


if __name__=='__main__':
    main()
