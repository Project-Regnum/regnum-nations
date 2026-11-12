#!/usr/bin/env python3
"""Two real Java 21 client processes against a disposable offline loopback dedicated server.

Run build and runHeraldryVisualTest first. Requires a working desktop/OpenGL session.
Never opens player worlds; no account credentials, published server or external hosts.
"""
import json
import os
from pathlib import Path
import queue
import shutil
import signal
import subprocess
import threading
import time
import uuid
from verify_heraldry_restart import ROOT


def main():
    fixture=ROOT/'build/run/gameTest/release-migrations/multiplayer.dat'; assert fixture.exists(), 'Run build first'
    worlds=sorted((ROOT/'build/run/heraldryVisualTest/saves').glob('heraldry-review-*'),key=lambda p:p.stat().st_mtime)
    assert worlds, 'Run runHeraldryVisualTest first'
    output=ROOT/'build/run/multiplayerAudit'; output.mkdir(parents=True,exist_ok=True)
    for marker in output.glob('*.txt'): marker.unlink()
    directory=ROOT/'build/run'/('multiplayerServer-'+uuid.uuid4().hex[:8]); directory.mkdir()
    shutil.copytree(worlds[-1],directory/'world'); shutil.copyfile(fixture,directory/'world/data/project-reigns-kingdoms.dat')
    (directory/'eula.txt').write_text('eula=true\n')
    (directory/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25590\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=2\n')
    init=directory/'multiplayer.init.gradle'
    init.write_text('''allprojects {
    afterEvaluate {
        loom.runs.server.runDir = '''+json.dumps(str(directory))+'''
        tasks.named('runServer', JavaExec) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            standardInput = System.in
            maxHeapSize = '512m'
        }
        ['One', 'Two'].eachWithIndex { suffix, index ->
            tasks.named('runMultiplayerAudit'+suffix, JavaExec) {
                maxHeapSize = '640m'
                doFirst {
                    // Pinned Loom 1.19 prepares these fields inside exec(), after doFirst.
                    def runTask = delegate
                    def type = runTask.class
                    while (type.name != 'net.fabricmc.loom.task.AbstractRunTask') type = type.superclass
                    def internal = { name ->
                        def method = type.getDeclaredMethod(name)
                        method.accessible = true
                        method.invoke(runTask)
                    }
                    if (internal('getUseArgFile').get()) internal('writeArgFile')
                    def directory = internal('getInternalRunDir').get().asFile
                    directory.mkdirs()
                    def command = [javaLauncher.get().executablePath.asFile.absolutePath]
                    command.addAll((allJvmArgs + jvmArgumentProviders.collectMany { it.asArguments() }).unique())
                    command.addAll(['-cp', internal('getInternalClasspath').asPath, mainClass.get()])
                    command.addAll(args + argumentProviders.collectMany { it.asArguments() })
                    file('''+json.dumps(str(output))+'''+'/launch-'+(index+1)+'.json').text = groovy.json.JsonOutput.toJson([command: command, cwd: directory.absolutePath, env: internal('getInternalEnvironmentVars').get()])
                    throw new org.gradle.api.tasks.StopExecutionException('Prepared isolated client launch')
                }
            }
        }
    }
}
''')
    common=['--no-daemon','--console=plain','--no-configuration-cache','-I',str(init)]
    # Prepare both launch commands once, then run the Java processes independently.
    # Two concurrent Gradle runs in one checkout can contend on project cache locks.
    with (output/'prepare.log').open('w') as log:
        subprocess.run(['./gradlew','runMultiplayerAuditOne','runMultiplayerAuditTwo',*common],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True)
    server=subprocess.Popen(['./gradlew','runServer',*common],cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,start_new_session=True)
    lines=queue.Queue(); clients=[]; logs=[]
    def read_server():
        with (directory/'server.log').open('w') as log:
            for line in server.stdout:
                log.write(line); log.flush(); lines.put(line)
            lines.put(None)
    threading.Thread(target=read_server,daemon=True).start()
    def send(command): server.stdin.write(command+'\n'); server.stdin.flush()
    deadline=time.monotonic()+300
    try:
        while time.monotonic()<deadline:
            line=lines.get(timeout=60)
            assert line is not None, 'Server exited during startup'
            if 'Done (' in line: break
        else: raise AssertionError('Server startup timed out')
        print('Dedicated Java 21 loopback server ready:',directory,flush=True)
        for index,name in enumerate(('One','Two'),1):
            log=(output/f'client-{index}.log').open('w'); logs.append(log)
            launch=json.loads((output/f'launch-{index}.json').read_text())
            clients.append(subprocess.Popen(launch['command'],cwd=launch['cwd'],env=os.environ|launch['env'],stdout=log,stderr=subprocess.STDOUT,text=True,start_new_session=True))
        changed_dimension=False; observed=set()
        while time.monotonic()<deadline:
            failures=list(output.glob('failure-*.txt')); assert not failures, '; '.join(p.read_text() for p in failures)
            for marker in output.glob('*.txt'):
                if marker.name not in observed: observed.add(marker.name); print(marker.name,flush=True)
            if (output/'dimension-ready-2.txt').exists() and not changed_dimension:
                send('execute in minecraft:the_nether run tp AuditTwo 0 80 0'); changed_dimension=True
            if all((output/f'passed-{i}.txt').exists() for i in (1,2)): break
            for i,client in enumerate(clients,1):
                assert client.poll() is None or (output/f'passed-{i}.txt').exists(), f'Client {i} exited early; see {output}/client-{i}.log'
            time.sleep(.2)
        else: raise AssertionError('Two-client verification timed out; see '+str(output))
        for client in clients: assert client.wait(timeout=30)==0, 'Client Gradle run failed'
        send('save-all flush'); send('stop'); assert server.wait(timeout=60)==0, 'Server failed to shut down'
        (output/'passed.json').write_text(json.dumps({'clients':2,'runtime':'Java 21','kingdoms':129,'server':str(directory),'coverage':['multi-batch login','concurrent stale save','two-client convergence','dimension change','real disconnect cache cleanup','reconnect']},indent=2)+'\n')
        print('PASS two independent offline loopback clients; remote latency and human gameplay remain manual.',flush=True)
    finally:
        for process in clients+[server]:
            if process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
        for log in logs: log.close()


if __name__=='__main__':
    main()
