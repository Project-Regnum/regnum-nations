#!/usr/bin/env python3
"""Two real Java-21 dedicated server processes against a disposable visual-test world copy.

Run ./gradlew runHeraldryVisualTest first. No production/run worlds are accepted.
Logs and a copied world remain under build/run/heraldryRestart-* for inspection.
"""
import gzip
import hashlib
import hmac
import json
from pathlib import Path
import queue
import shutil
import struct
import subprocess
import threading
import time
import uuid

ROOT=Path(__file__).resolve().parents[1]

class Nbt:
    """Small read-only probe for the generated test world's ordinary NBT strings."""
    def __init__(self,data): self.data=data; self.offset=0
    def take(self,n): result=self.data[self.offset:self.offset+n]; self.offset+=n; return result
    def number(self,fmt): return struct.unpack('>'+fmt,self.take(struct.calcsize(fmt)))[0]
    def string(self): return self.take(self.number('H')).decode('utf-8')
    def value(self,kind):
        if kind in {1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}: return self.number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7: return self.take(self.number('i'))
        if kind==8: return self.string()
        if kind==9:
            item=self.number('B'); return [self.value(item) for _ in range(self.number('i'))]
        if kind==10:
            result={}
            while (item:=self.number('B')):
                key=self.string(); result[key]=self.value(item)
            return result
        if kind in (11,12): return [self.number('i' if kind==11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(f'Unsupported NBT type {kind}')
    @classmethod
    def read(cls,path):
        probe=cls(gzip.decompress(path.read_bytes())); kind=probe.number('B'); probe.string(); return probe.value(kind)

def run_server(directory,init,number,realm_id,*,rename=True):
    command=['./gradlew','runServer','--no-daemon','--console=plain','--no-configuration-cache','-I',str(init)]
    process=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=queue.Queue()
    def read():
        for line in process.stdout: lines.put(line)
        lines.put(None)
    threading.Thread(target=read,daemon=True).start()
    output=[]; phase=0; deadline=time.monotonic()+180; action_at=0
    def send(command): process.stdin.write(command+'\n'); process.stdin.flush()
    try:
        while time.monotonic()<deadline:
            try: line=lines.get(timeout=1)
            except queue.Empty: line=''
            if line is None: break
            output.append(line)
            if any(word in line for word in ('Done (','KingdomId','Saved the game','BUILD SUCCESSFUL','ERROR')): print(line.strip(),flush=True)
            if phase==0 and 'Done (' in line:
                send('forceload add -4 -3 4 0'); phase=1; action_at=time.monotonic()
            if phase==1 and time.monotonic()-action_at>=3:
                send('nations admin list')
                if number==1 and rename: send(f'nations admin rename {realm_id} Restart Heraldry Verified')
                send('data get block -4 161 0'); send('data get block 0 162 -3'); phase=2; action_at=time.monotonic()
            if phase==2 and time.monotonic()-action_at>=3:
                send('save-all flush'); phase=3; action_at=time.monotonic()
            if phase==3 and 'Saved the game' in line:
                send('stop'); phase=4
        else: raise AssertionError('Dedicated server timed out')
        code=process.wait(timeout=30)
        text=''.join(output); (directory/f'process-{number}.log').write_text(text)
        assert code==0 and phase==4 and 'BUILD SUCCESSFUL' in text, f'Process {number} did not stop cleanly'
        assert text.count('KingdomId:')>=2, 'Both standing and wall entities must restore UUIDs'
        assert 'is not a block entity' not in text and 'Unknown or incomplete command' not in text, 'Queries/commands failed'
    finally:
        if process.poll() is None:
            try: send('stop'); process.wait(timeout=30)
            except Exception: process.kill(); process.wait()

def main():
    worlds=sorted((ROOT/'build/run/heraldryVisualTest/saves').glob('heraldry-review-*'),key=lambda p:p.stat().st_mtime)
    assert worlds,'Run the heraldry visual checks first'
    passed=ROOT/'build/run/heraldryVisualTest/checks/passed.txt'; assert passed.exists(),'Visual checks have not passed'
    source=worlds[-1]; directory=ROOT/'build/run'/('heraldryRestart-'+uuid.uuid4().hex[:8]); directory.mkdir(parents=True)
    shutil.copytree(source,directory/'world')
    (directory/'eula.txt').write_text('eula=true\n')
    (directory/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-players=2\n')
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
    file=directory/'world/data/project-reigns-kingdoms.dat'
    original=Nbt.read(file)['data']; assert original['Version']==8 and len(original['Kingdoms'])==1
    kingdom=original['Kingdoms'][0]; bits=b''.join(struct.pack('>i',v) for v in kingdom['Id']); realm_id=str(uuid.UUID(bytes=bits))
    signing_key=original.get('BannerSecret'); assert isinstance(signing_key,bytes) and len(signing_key)==32, 'Visual fixture must issue an authentic banner'
    captured=[]
    for player_file in (directory/'world/playerdata').glob('*.dat'):
        for item in Nbt.read(player_file).get('Inventory',[]):
            if item.get('id')=='regnum-nations:royal_banner':
                custom=item['components']['minecraft:custom_data']
                identity=str(uuid.UUID(bytes=b''.join(struct.pack('>i',v) for v in custom['KingdomId'])))
                captured.append((identity,custom['RoyalSeal']))
    assert captured, 'Visual fixture has no saved authenticated item'
    print('Disposable verification directory:',directory,flush=True)
    for number in (1,2):
        run_server(directory,init,number,realm_id)
        saved=Nbt.read(file)['data']; current=saved['Kingdoms'][0]
        assert saved['Version']==8 and current['Heraldry']==kingdom['Heraldry'] and current['Id']==kingdom['Id']
        assert saved.get('BannerSecret')==signing_key, 'Signing key changed across restart'
        for identity,seal in captured:
            assert hmac.compare_digest(hmac.new(saved['BannerSecret'],('regnum-royal-banner-v1:'+identity).encode(),hashlib.sha256).hexdigest(),seal), 'Captured item no longer authenticates'
        assert current['Name']=='Restart Heraldry Verified'
        for key in ('Settlements','Claims','Citizens','KingdomRoles','CityInvitations','Diplomacy','DeletedStateAnchors'):
            assert saved[key]==original[key],f'{key} changed across restart'
        print(f'PASS process {number}: schema 8, arms, renamed UUID, both banner entities and unrelated data',flush=True)
    (directory/'passed.txt').write_text('PASS two normal dedicated Java 21 processes; retained arms/identity/rename, standing/wall entity UUIDs, unrelated data.\n')

if __name__=='__main__': main()
