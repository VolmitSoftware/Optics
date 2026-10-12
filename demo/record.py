import argparse
import hashlib
import importlib.util
import io
import json
import os
import re
import secrets
import shutil
import signal
import subprocess
import sys
import time
import traceback
import zipfile
from pathlib import Path


ROOT: Path = Path(__file__).resolve().parent
ACTOR: str = 'OpticsViewer'
PAPER_BUILDS: dict[str, str] = {'26.2': '133', '26.3': '169'}
TEMPLATES: dict[str, str] = {'26.2': 'AdaptDemoStudio', '26.3': 'Instance Automator - 1'}
NATIVE_CAPABILITIES: int = (1 << 12) | (1 << 16)
NATIVE_COUNTERS: tuple[str, ...] = ('preparedFrame', 'terrainFrames', 'terrainDraws', 'lastTerrainFrame',
                                    'visiblePortals', 'meshSections', 'unavailablePortals', 'lastAffineRevision')


def read_properties(text: str) -> dict[str, str]:
    return dict((key.strip(), value.strip().replace('\\:', ':'))
                for line in text.splitlines() if '=' in line and not line.lstrip().startswith('#')
                for key, value in [line.split('=', 1)])


def source_provenance(jar: Path, files: list[Path], root: Path) -> dict:
    digest: object = hashlib.sha256()
    for path in sorted(set(files)):
        if path.stat().st_mtime_ns > jar.stat().st_mtime_ns:
            raise RuntimeError('Source changed after the jar was built: ' + str(path))
        digest.update(str(path.relative_to(root)).encode() + b'\0')
        digest.update(bytes.fromhex(identity(path)['sha256']))
    return {'sha256': digest.hexdigest(), 'files': len(set(files))}


def wormholes_artifact(root: Path, version: str, loader: str) -> tuple[Path, dict]:
    properties: dict[str, str] = read_properties((root / 'gradle.properties').read_text())
    target: dict = json.loads((root / 'gradle/minecraft-versions' / (version + '.json')).read_text())
    required_loader: str = target['loaders']['fabric']
    if target['minecraft'] != version or loader != required_loader:
        raise RuntimeError('Client template must use the target Fabric loader ' + required_loader)
    build: Path = root / 'adapters/fabric/build'
    if version != properties['minecraftVersion']:
        build /= version
    jar: Path = build / 'libs' / ('Wormholes v' + properties['wormholesVersion']
                                 + ' [Fabric] ' + version + '+' + required_loader + '.jar')
    if not jar.is_file():
        raise FileNotFoundError('Build the current Wormholes Fabric artifact before recording: ' + str(jar))
    with zipfile.ZipFile(jar) as archive:
        metadata: dict = json.loads(archive.read('fabric.mod.json'))
        built_target: dict[str, str] = read_properties(archive.read('wormholes-target.properties').decode())
        built_version: dict[str, str] = read_properties(archive.read('wormholes-build.properties').decode())
        if (metadata.get('id') != 'wormholes' or metadata.get('version') != properties['wormholesVersion']
                or built_version.get('version') != properties['wormholesVersion']
                or metadata.get('depends', {}).get('minecraft') != version
                or metadata.get('depends', {}).get('fabricloader') != '>=' + required_loader
                or metadata.get('depends', {}).get('java') != '>=25'
                or any(built_target.get(key) != value for key, value in
                       {'minecraft': version, 'loader': 'fabric', 'loaderVersion': required_loader,
                        'renderRuntimeSupported': 'true'}.items())):
            raise RuntimeError('Wormholes artifact metadata does not match the requested current target')
        entrypoints: dict = metadata.get('entrypoints', {})
        if entrypoints.get('client') != ['art.arcane.wormholes.fabric.WormholesFabricClient']:
            raise RuntimeError('Wormholes artifact lacks the native client entrypoint')
        for class_name in ('fabric/WormholesFabricClient', 'modded/client/WormholesClient',
                           'modded/client/render/ClientPortalRenderer'):
            if 'art/arcane/wormholes/' + class_name + '.class' not in archive.namelist():
                raise RuntimeError('Wormholes artifact lacks native client class: ' + class_name)
        modules: dict[str, dict] = {}
        for entry in metadata.get('jars', []):
            payload: bytes = archive.read(entry['file'])
            with zipfile.ZipFile(io.BytesIO(payload)) as nested:
                module: dict = json.loads(nested.read('fabric.mod.json'))
            identifier: str = module['id']
            if identifier in modules:
                raise RuntimeError('Duplicate bundled Fabric module: ' + identifier)
            modules[identifier] = {'version': module['version'], 'depends': module.get('depends', {}),
                                   'file': entry['file'], 'sha256': hashlib.sha256(payload).hexdigest()}
        if {key: value['version'] for key, value in modules.items()} != target['fabricModules']:
            raise RuntimeError('Wormholes artifact has missing or mismatched bundled Fabric API modules')
        for declaration in [metadata, *modules.values()]:
            missing: set[str] = set(declaration.get('depends', {})) - {'minecraft', 'java', 'fabricloader'} - set(modules)
            if missing:
                raise RuntimeError('Unresolved native client runtime dependencies: ' + ', '.join(sorted(missing)))
    files: list[Path] = [root / 'gradle.properties', root / 'adapters/fabric/build.gradle',
                         root / 'gradle/minecraft-versions' / (version + '.json')]
    files.extend(path for path in (root / 'core/build.gradle', root / 'optics/build.gradle',
                                   root / 'optics/build.gradle.kts', root / 'core/gradle.properties',
                                   root / 'optics/gradle.properties') if path.is_file())
    files.extend(path for path in (root / 'gradle').glob('*.gradle'))
    for directory in ('core/src/main', 'optics/src/main', 'adapters/fabric/src/main',
                      'adapters/modded-common/src/main', 'src/main/resources/languages',
                      'src/main/resources/wormholes-pockets-pack',
                      'adapters/fabric/src/' + version + '/java', 'adapters/fabric/src/' + version + '/resources',
                      'adapters/modded-common/src/' + version + '/java',
                      'adapters/modded-common/src/' + version + '/resources'):
        files.extend(path for path in (root / directory).rglob('*') if path.is_file())
    return jar, {'source': identity(jar), 'metadata': metadata, 'target': built_target,
                 'bundledFabricModules': modules, 'sourceInputs': source_provenance(jar, files, root),
                 'externalRendererMods': []}


def copy_artifact(source: Path, destination: Path) -> dict:
    original: dict = identity(source)
    with zipfile.ZipFile(source) as archive:
        metadata: dict = json.loads(archive.read('fabric.mod.json'))
    shutil.copy2(source, destination)
    copied: dict = identity(destination)
    if copied['sha256'] != original['sha256'] or identity(source)['sha256'] != original['sha256']:
        raise RuntimeError('Client artifact changed during deployment: ' + str(source))
    return {'source': original, 'deployed': copied, 'id': metadata['id'], 'version': metadata['version']}


def loader_modules(source: Path, version: str) -> dict:
    with zipfile.ZipFile(source) as archive:
        metadata: dict = json.loads(archive.read('fabric.mod.json'))
        if metadata['id'] != 'fabricloader' or metadata['version'] != version:
            raise RuntimeError('Cached loader artifact does not match its selected version')
        modules: dict = {}
        for entry in metadata.get('jars', []):
            payload: bytes = archive.read(entry['file'])
            with zipfile.ZipFile(io.BytesIO(payload)) as nested:
                module: dict = json.loads(nested.read('fabric.mod.json'))
            modules[module['id']] = {'version': module['version'], 'sha256': hashlib.sha256(payload).hexdigest()}
        return {'source': identity(source), 'modules': modules}


def validate_loaded_mods(loaded: str, version: str, variant: str, artifacts: dict) -> dict[str, str]:
    mods: dict[str, str] = dict(re.findall(r'(?m)^\s*[-|\\+]+\s+([a-z0-9_-]+) (\S+)\s*$', loaded))
    expected: dict[str, str] = {'minecraft': version, 'fabricloader': artifacts['loader']}
    expected.update({key: value['version'] for key, value in artifacts.get('loaderArtifact', {}).get('modules', {}).items()})
    expected.update({entry['id']: entry['version'] for entry in artifacts['sources']})
    native: dict | None = artifacts['wormholes']
    if variant == 'modded':
        if native is None:
            raise AssertionError('Missing native Wormholes artifact provenance')
        expected.update({key: value['version'] for key, value in native['bundledFabricModules'].items()})
        if 'Wormholes ClientView client ready (renderer=native,' not in loaded:
            raise AssertionError('Client log does not confirm native Wormholes initialization')
    elif variant != 'unmodded' or native is not None:
        raise AssertionError('Capture-only vanilla profile contains native artifact provenance')
    if any(mods.get(key) != value for key, value in expected.items()):
        raise AssertionError('Loaded client mods do not match artifact provenance: ' + str(mods))
    if set(mods) - set(expected) - {'java'}:
        raise AssertionError('Unexpected client mods in the comparison profile: ' + str(mods))
    return mods


def run(arguments: list[str], cwd: Path | None = None, timeout: float = 180) -> str:
    result: subprocess.CompletedProcess[str] = subprocess.run(arguments, cwd=cwd, capture_output=True,
                                                            text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(arguments[:4]) + '\n' + (result.stdout + result.stderr)[-4000:])
    return result.stdout.strip()


def write_properties(path: Path, values: dict[str, str]) -> None:
    entries: dict[str, str] = dict(line.split('=', 1) for line in path.read_text().splitlines()
                                  if '=' in line and not line.startswith('#'))
    entries.update(values)
    path.write_text('\n'.join(key + '=' + value for key, value in entries.items()) + '\n')


def status(response: str) -> dict[str, str]:
    fields: dict[str, str] = dict(re.findall(r'(\w+)=([^\s]+)', re.sub(r'§.', '', response)))
    if 'scene' not in fields or fields.get('renderer') not in ('block-packets', 'clientview-native'):
        raise AssertionError('Unexpected demo status: ' + response)
    return fields


def validate_render_path(host: dict[str, str], client: dict, variant: str, fresh: bool = False) -> dict:
    expected: str = 'block-packets' if variant == 'unmodded' else 'clientview-native'
    if variant not in ('unmodded', 'modded') or host.get('renderer') != expected:
        raise AssertionError('Demo render path does not match the requested comparison: ' + str(host))
    if host.get('ready') not in ('true', 'false') or (fresh or variant == 'unmodded') and host['ready'] != 'true':
        raise AssertionError('Demo projection is not ready: ' + str(host))
    visible_field: str = 'visible' if variant == 'unmodded' else 'visiblePortals'
    if not re.fullmatch(r'\d+', host.get(visible_field, '')):
        raise AssertionError('Demo status is missing the expected visible portal count')
    if variant == 'unmodded':
        if any(not re.fullmatch(r'\d+', host.get(field, '')) for field in ('packetBatches', 'packetChanges')):
            raise AssertionError('Missing standard block packet delivery evidence')
        if int(host['visible']) > 0 and (int(host['packetBatches']) <= 0 or int(host['packetChanges']) <= 0):
            raise AssertionError('Visible standard projection has no delivered block packets')
        return {'renderer': expected, 'hostReady': True, 'packetBatches': int(host['packetBatches']),
                'packetChanges': int(host['packetChanges'])}
    if host.get('streamState') != 'CLIENT_VIEW' or int(host.get('streamCaps', '0')) & NATIVE_CAPABILITIES != NATIVE_CAPABILITIES:
        raise AssertionError('Native host did not negotiate affine terrain projection')
    if not re.fullmatch(r'[1-9]\d*', host.get('sampleRevision', '')):
        raise AssertionError('Native host is missing the current scene sample revision')
    proof: dict | None = client.get('wormholesProjection')
    if not isinstance(proof, dict):
        raise AssertionError('Missing client terrain composition proof; installed mods and host sends are insufficient')
    if (proof.get('renderer') != expected or proof.get('streamState') != 'CLIENT_VIEW'
            or type(proof.get('streamCaps')) is not int
            or proof['streamCaps'] & NATIVE_CAPABILITIES != NATIVE_CAPABILITIES):
        raise AssertionError('Client has no active negotiated native affine render path')
    for field in NATIVE_COUNTERS:
        if type(proof.get(field)) is not int or proof[field] < 0:
            raise AssertionError('Missing or invalid native composition counter: ' + field)
    if proof['unavailablePortals'] != 0:
        raise AssertionError('Native terrain renderer reports unavailable portals')
    if proof['lastTerrainFrame'] > proof['preparedFrame']:
        raise AssertionError('Invalid native terrain frame sequence')
    if fresh or int(host['visiblePortals']) > 0:
        if (proof['visiblePortals'] < max(1, int(host['visiblePortals']))
                or proof['meshSections'] <= 0 or proof['terrainFrames'] <= 0
                or proof['terrainDraws'] <= 0 or proof['preparedFrame'] - proof['lastTerrainFrame'] > 3):
            raise AssertionError('No fresh projected-terrain composition for the visible scene')
    if fresh and proof['lastAffineRevision'] < int(host.get('sampleRevision', '0')):
        raise AssertionError('Native terrain composition has not reached the preview sample revision')
    return dict(proof)


def validate_native_progress(previous: dict, current: dict, visible: bool) -> None:
    for field in ('preparedFrame', 'terrainFrames', 'terrainDraws', 'lastTerrainFrame', 'lastAffineRevision'):
        if current[field] < previous[field]:
            raise AssertionError('Native terrain proof counter reset during the scene: ' + field)
    if visible and (current['terrainFrames'] <= previous['terrainFrames']
                    or current['terrainDraws'] <= previous['terrainDraws']):
        raise AssertionError('GPU frames advanced without successful native terrain composition')


def await_projection(rig: object, bridge: object, rcon: object, variant: str, identifier: str) -> tuple[dict, dict, dict]:
    deadline: float = time.monotonic() + 20
    while True:
        host: dict = status(rcon.command('opticsdemo status ' + ACTOR))
        client: dict = check_client(rig, bridge)
        if host['scene'] != identifier or host['tick'] != '0':
            raise AssertionError('Scene changed while waiting for projection readiness')
        try:
            proof: dict = validate_render_path(host, client, variant, fresh=variant == 'modded')
            return host, client, proof
        except AssertionError as failure:
            if time.monotonic() >= deadline:
                raise TimeoutError('Projection readiness or genuine client terrain proof did not arrive') from failure
        time.sleep(0.25)


def check_client(rig: object, bridge: object) -> dict:
    state: dict = bridge.state(timeout=5)
    validate_state(rig, state)
    return state


def validate_state(rig: object, state: dict) -> None:
    rig.verify_hidden_renderer(state)
    if state.get('firstPerson') is not True:
        raise AssertionError('Recording requires the first-person camera')
    if not state.get('connected') or state.get('screen'):
        raise AssertionError('Recording client is not in uninterrupted gameplay: ' + str(state.get('screen')))
    if state.get('hudVisible') is not False:
        raise AssertionError('Recording requires the HUD to remain hidden')


def identity(path: Path) -> dict:
    with path.open('rb') as source:
        digest: str = hashlib.file_digest(source, 'sha256').hexdigest()
    return {'path': str(path), 'sha256': digest, 'bytes': path.stat().st_size}


def owned_processes(profile: Path) -> list[int]:
    listing: str = run(['/bin/ps', '-axo', 'pid=,args='])
    found: list[int] = []
    for line in listing.splitlines():
        fields: list[str] = line.strip().split(None, 1)
        if len(fields) == 2 and str(profile) in fields[1] and 'org.prismlauncher.EntryPoint' in fields[1]:
            found.append(int(fields[0]))
    return found


def stop_owned_processes(profile: Path) -> None:
    deadline: float = time.monotonic() + 15
    while owned_processes(profile) and time.monotonic() < deadline:
        time.sleep(0.25)
    for chosen_signal in (signal.SIGTERM, signal.SIGKILL):
        remaining: list[int] = owned_processes(profile)
        if not remaining:
            return
        for pid in remaining:
            try:
                os.kill(pid, chosen_signal)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 5
        while owned_processes(profile) and time.monotonic() < deadline:
            time.sleep(0.25)
    if owned_processes(profile):
        raise RuntimeError('Owned client survived cleanup: ' + profile.name)


def cleanup_attempt(failures: list[str], label: str, operation: object) -> bool:
    try:
        operation()
        return True
    except Exception:
        failures.append(label)
        traceback.print_exc()
        return False


def validate_capture(rig: object, state: dict) -> dict:
    validate_state(rig, state)
    evidence: dict = rig.capture_evidence(state)
    if state.get('captureMaxGapFrames', 0) > 3 or state.get('captureHolds'):
        raise AssertionError('Continuous demo capture contains a visible repeated-frame freeze')
    return evidence


def review_time(second: float, observations: list[dict], duration: float) -> float:
    target: float = second * 20
    for index, observed in enumerate(observations):
        tick: int = int(observed['host']['tick'])
        current: float = float(observed['captureSeconds'])
        if tick >= target:
            if index == 0:
                return min(duration - 0.2, current + 0.2)
            previous: dict = observations[index - 1]
            previous_tick: int = int(previous['host']['tick'])
            fraction: float = (target - previous_tick) / max(1, tick - previous_tick)
            mapped: float = float(previous['captureSeconds']) + fraction * (current - float(previous['captureSeconds']))
            return min(duration - 0.2, mapped + 0.2)
    return duration - 0.2


def export(rig: object, raw: Path, destination: Path) -> dict:
    destination.parent.mkdir(parents=True, exist_ok=True)
    run([str(rig.FFMPEG), '-y', '-v', 'error', '-i', str(raw), '-an', '-c:v', 'libvpx-vp9',
         '-crf', '34', '-b:v', '0', '-row-mt', '1', '-cpu-used', '4', '-threads', '6', str(destination)], timeout=300)
    run([str(rig.FFMPEG), '-v', 'error', '-i', str(destination), '-f', 'null', '-'], timeout=120)
    probe: dict = json.loads(run([str(rig.FFPROBE), '-v', 'error', '-show_streams', '-show_format',
                                 '-of', 'json', str(destination)]))
    stream: dict = next(item for item in probe['streams'] if item['codec_type'] == 'video')
    if (stream['width'], stream['height'], stream['r_frame_rate']) != (1920, 1080, '30/1'):
        raise AssertionError('Export changed the native capture format')
    if destination.stat().st_size > 25 * 1024 * 1024:
        raise AssertionError('Wiki media exceeds 25 MB')
    return {'path': str(destination), 'bytes': destination.stat().st_size,
            'seconds': float(probe['format']['duration']), 'decode': 'passed'}


def make_profile(rig: object, version: str, variant: str, output: Path, port: int, token: str,
                 profile_name: str) -> Path:
    profile: Path = rig.PRISM / 'instances' / profile_name
    profile.mkdir()
    (profile / '.optics-demo-owned').write_text(profile_name + '\n')
    template: Path = rig.PRISM / 'instances' / TEMPLATES[version]
    pack: dict = json.loads((template / 'mmc-pack.json').read_text())
    game: dict = next(component for component in pack['components'] if component['uid'] == 'net.minecraft')
    if game['version'] != version:
        raise AssertionError('Cached client template has the wrong Minecraft version')
    loader: dict = next(component for component in pack['components'] if component['uid'] == 'net.fabricmc.fabric-loader')
    (profile / 'mmc-pack.json').write_text(json.dumps(pack, indent=2) + '\n')
    java: Path = rig.PRISM / 'java/java-runtime-epsilon/bin/java'
    arguments: str = ' '.join(['--enable-native-access=ALL-UNNAMED', '-Dautomator.hidden=true',
                              '-Dautomator.muted=true', '-Dautomator.frameWidth=1920',
                              '-Dautomator.frameHeight=1080', '-Dautomator.port=' + str(port),
                              '-Dautomator.token=' + token, '-Dautomator.output=' + str(output)])
    configuration: dict[str, str] = {'ConfigVersion': '1.3', 'InstanceType': 'OneSix', 'name': profile_name,
                                     'JavaPath': str(java), 'JvmArgs': '"' + arguments + '"',
                                     'OverrideJavaArgs': 'true', 'OverrideJavaLocation': 'true',
                                     'OverrideMemory': 'true', 'OverrideMiscellaneous': 'true',
                                     'OverrideWindow': 'true', 'MinMemAlloc': '512', 'MaxMemAlloc': '3072',
                                     'MinecraftWinWidth': '1920', 'MinecraftWinHeight': '1080',
                                     'ShowConsole': 'false', 'ShowConsoleOnError': 'false'}
    (profile / 'instance.cfg').write_text('[General]\n' + '\n'.join(k + '=' + v for k, v in configuration.items()) + '\n')
    os.chmod(profile / 'instance.cfg', 0o600)
    minecraft: Path = profile / '.minecraft'
    mods: Path = minecraft / 'mods'
    mods.mkdir(parents=True)
    bridge_jar: Path = rig.ROOT / 'build/client-automator'
    if version != '26.3':
        bridge_jar /= version
    sources: list[dict] = [copy_artifact(bridge_jar / 'InstanceAutomator.jar', mods / 'InstanceAutomator.jar')]
    native: dict | None = None
    if variant == 'modded':
        wormholes: Path
        wormholes, native = wormholes_artifact(rig.ROOT, version, loader['version'])
        sources.append(copy_artifact(wormholes, mods / 'Wormholes.jar'))
        if sources[-1]['source']['sha256'] != native['source']['sha256']:
            raise RuntimeError('Validated Wormholes jar changed before deployment')
        configuration_directory: Path = minecraft / 'config'
        configuration_directory.mkdir()
        (configuration_directory / 'wormholes-client.toml').write_text('renderer = "native"\nshowDebugOverlay = false\n')
    elif variant != 'unmodded':
        raise ValueError('Unknown recording variant: ' + variant)
    loader_jar: Path = rig.PRISM / 'libraries/net/fabricmc/fabric-loader' / loader['version'] / ('fabric-loader-' + loader['version'] + '.jar')
    (output / 'client-artifacts.json').write_text(json.dumps({'sources': sources, 'wormholes': native,
                                                            'loaderArtifact': loader_modules(loader_jar, loader['version']),
                                                            'minecraft': version, 'loader': loader['version']}, indent=2) + '\n')
    options: dict[str, str] = {'renderDistance': '8', 'simulationDistance': '5', 'maxFps': '60',
                               'enableVsync': 'false', 'pauseOnLostFocus': 'false', 'fullscreen': 'false',
                               'bobView': 'false', 'fov': '0.0', 'guiScale': '3', 'chatOpacity': '0.0',
                               'soundCategory_master': '0.0', 'soundCategory_music': '0.0',
                               'resourcePacks': '[]', 'incompatibleResourcePacks': '[]',
                               'tutorialStep': 'none', 'onboardAccessibility': 'false', 'startedCleanly': 'true'}
    (minecraft / 'options.txt').write_text('\n'.join(k + ':' + v for k, v in options.items()) + '\n')
    return profile



def await_removed(rig: object, bridge: object, rcon: object, variant: str) -> dict:
    deadline: float = time.monotonic() + 20
    while True:
        host: dict = dict(re.findall(r'(\w+)=([^\s]+)', rcon.command('opticsdemo status ' + ACTOR)))
        client: dict = check_client(rig, bridge)
        proof: dict = client.get('wormholesProjection') or {}
        removed: bool = variant == 'unmodded' or proof.get('visiblePortals') == 0 and proof.get('meshSections') == 0
        if host.get('scene') == 'stopped' and host.get('displays') == '0' and removed:
            return {'host': host, 'client': client}
        if time.monotonic() >= deadline:
            raise TimeoutError('Demo scene survived stop or server reload')
        time.sleep(0.25)


def verify_reload(rig: object, bridge: object, rcon: object, variant: str, output: Path) -> None:
    status(rcon.command('opticsdemo preview continuous-spin ' + ACTOR))
    before_host, before_client, before_proof = await_projection(rig, bridge, rcon, variant, 'continuous-spin')
    response: str = rcon.command('bukkit:reload confirm')
    removed: dict = await_removed(rig, bridge, rcon, variant)
    status(rcon.command('opticsdemo preview continuous-spin ' + ACTOR))
    after_host, after_client, after_proof = await_projection(rig, bridge, rcon, variant, 'continuous-spin')
    rcon.command('opticsdemo stop ' + ACTOR)
    stopped: dict = await_removed(rig, bridge, rcon, variant)
    if after_client['frames'] <= before_client['frames']:
        raise AssertionError('Hidden renderer stopped advancing across the server reload')
    report: dict = {'status': 'passed', 'reloadResponse': response,
                    'before': {'host': before_host, 'proof': before_proof}, 'removed': removed,
                    'reopened': {'host': after_host, 'proof': after_proof}, 'stopped': stopped}
    (output / 'reload.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'reload': 'passed', 'variant': variant}), flush=True)


def record_variant(rig: object, args: argparse.Namespace, version: str, variant: str,
                   server: Path, server_port: int, scenes: list[dict], output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    bridge_port: int = rig.free_port()
    bridge: object = rig.Bridge(bridge_port, secrets.token_hex(24))
    profile_name: str = 'OpticsDemo-' + version + '-' + variant + '-' + secrets.token_hex(3)
    profile: Path = rig.PRISM / 'instances' / profile_name
    log: object | None = None
    launcher: subprocess.Popen | None = None
    rcon: object | None = None
    pid: int | None = None
    try:
        make_profile(rig, version, variant, output, bridge_port, bridge.token, profile_name)
        time.sleep(1)
        log = (output / 'launcher.log').open('w')
        launcher = subprocess.Popen([str(rig.LAUNCHER), '--launch', profile_name, '--offline', ACTOR,
                                     '--server', '127.0.0.1:' + str(server_port)], stdout=log, stderr=subprocess.STDOUT)
        deadline: float = time.monotonic() + 150
        while True:
            try:
                initial: dict = bridge.state(timeout=2)
                rig.verify_hidden_renderer(initial)
                pid = initial.get('processId')
                if initial.get('connected') and not initial.get('screen'):
                    break
            except (OSError, TimeoutError):
                pass
            if time.monotonic() > deadline:
                raise TimeoutError('Owned hidden client failed to connect; inspect ' + str(output / 'launcher.log'))
            time.sleep(0.5)
        rig.fit_hidden_renderer(bridge)
        bridge.command('hud', visible=False)
        bridge.command('release')
        rcon = rig.Rcon(server)
        client_log: Path = profile / '.minecraft/logs/latest.log'
        loaded: str = client_log.read_text(errors='replace')
        artifacts: dict = json.loads((output / 'client-artifacts.json').read_text())
        loaded_mods: dict[str, str] = validate_loaded_mods(loaded, version, variant, artifacts)
        provenance: dict = {'sourceDemo': identity(ROOT / 'build/libs/OpticsDemo-0.1.0.jar'),
                            'deployedDemo': identity(server / 'plugins/OpticsDemo.jar'),
                            'clientArtifacts': artifacts,
                            'clientMods': [identity(path) for path in sorted((profile / '.minecraft/mods').glob('*.jar'))],
                            'loadedMods': loaded_mods}
        if provenance['sourceDemo']['sha256'] != provenance['deployedDemo']['sha256']:
            raise AssertionError('Demo deployment does not match the current source artifact')
        status(rcon.command('opticsdemo setup ' + ACTOR))
        time.sleep(3)
        for scene in scenes:
            identifier: str = scene['id']
            take: Path = output / identifier
            take.mkdir(exist_ok=True)
            prepared: dict = status(rcon.command('opticsdemo preview ' + identifier + ' ' + ACTOR))
            if prepared['scene'] != identifier or prepared['tick'] != '0':
                raise AssertionError('Scene preview did not reset')
            bridge.command('clear-chat')
            time.sleep(2)
            ready_host: dict
            start: dict
            start_proof: dict
            ready_host, start, start_proof = await_projection(rig, bridge, rcon, variant, identifier)
            time.sleep(0.2)
            if check_client(rig, bridge)['frames'] <= start['frames']:
                raise AssertionError('Hidden GPU frames are not advancing')
            raw: Path = take / 'first-person.mp4'
            bridge.command('capture', action='start', path=str(raw), ffmpeg=str(rig.FFMPEG),
                           width=1920, height=1080, fps=30)
            time.sleep(1.5)
            status(rcon.command('opticsdemo play ' + identifier + ' ' + ACTOR))
            states: list[dict] = []
            previous_proof: dict = start_proof
            previous_host: dict = ready_host
            deadline = time.monotonic() + scene['durationSeconds'] * 2 + 15
            while True:
                observed: dict = status(rcon.command('opticsdemo status ' + ACTOR))
                client: dict = check_client(rig, bridge)
                proof: dict = validate_render_path(observed, client, variant)
                if variant == 'modded':
                    validate_native_progress(previous_proof, proof, int(observed['visiblePortals']) > 0)
                    if (int(observed['visiblePortals']) > 0 and int(previous_host['visiblePortals']) > 0
                            and proof['lastAffineRevision'] < int(previous_host['sampleRevision'])):
                        raise AssertionError('Native terrain composition is stale against the host animation')
                previous_proof = proof
                previous_host = observed
                if abs(client['yaw'] - start['yaw']) > 0.25 or abs(client['pitch'] - start['pitch']) > 0.25:
                    raise AssertionError('First-person recording orientation changed unexpectedly')
                states.append({'host': observed, 'frames': client['frames'], 'ticks': client['ticks'],
                               'monotonicSeconds': time.monotonic(), 'captureSeconds': client.get('captureSeconds'),
                               'projection': proof})
                if observed['scene'] != identifier or float(observed['cameraError']) > 0.01:
                    raise AssertionError('Scene or first-person camera changed unexpectedly')
                if observed['completed'] == 'true':
                    break
                if time.monotonic() > deadline:
                    raise TimeoutError('Scene failed to complete')
                time.sleep(0.75)
            for _ in range(6):
                tail_host: dict = status(rcon.command('opticsdemo status ' + ACTOR))
                tail: dict = check_client(rig, bridge)
                tail_proof: dict = validate_render_path(tail_host, tail, variant)
                if variant == 'modded':
                    validate_native_progress(previous_proof, tail_proof, int(tail_host['visiblePortals']) > 0)
                previous_proof = tail_proof
                time.sleep(0.25)
            stopped: dict = bridge.command('capture', action='stop')
            metrics: dict = validate_capture(rig, stopped)
            if variant == 'modded':
                validate_native_progress(start_proof, previous_proof, True)
                if previous_proof['lastAffineRevision'] <= start_proof['lastAffineRevision']:
                    raise AssertionError('Native terrain composition did not consume the animated affine samples')
            manifest: dict = {'minecraft': version, 'renderer': 'vanilla' if variant == 'unmodded' else 'Wormholes ClientView native',
                              'renderPath': ready_host['renderer'], 'projectionReady': ready_host,
                              'projectionProof': {'start': start_proof, 'end': previous_proof},
                              'captureInstrumentation': 'InstanceAutomator', 'perspective': 'first-person',
                              'scene': identifier, 'capture': metrics, 'observations': states,
                              'visualReview': 'pending', 'raw': str(raw), 'provenance': provenance,
                              'firstPerson': stopped['firstPerson'], 'hudVisible': stopped['hudVisible']}
            (take / 'evidence.json').write_text(json.dumps(manifest, indent=2) + '\n')
            for index, second in enumerate(scene['reviewSeconds']):
                at: float = review_time(second, states, float(stopped['captureSeconds']))
                run([str(rig.FFMPEG), '-v', 'error', '-ss', str(at), '-i', str(raw),
                     '-frames:v', '1', '-y', str(take / ('review-' + str(index) + '.png'))])
            run([str(rig.FFMPEG), '-v', 'error', '-ss', str(float(stopped['captureSeconds']) - 0.25), '-i', str(raw),
                 '-frames:v', '1', '-y', str(take / 'review-final.png')])
            destination: Path = args.media / version / (identifier + '-' + variant + '-pov.webm')
            manifest['export'] = export(rig, raw, destination)
            (take / 'evidence.json').write_text(json.dumps(manifest, indent=2) + '\n')
            print(json.dumps({'recorded': identifier, 'version': version, 'variant': variant,
                              'export': str(destination), 'visualReview': 'pending'}), flush=True)
        if args.verify_reload:
            verify_reload(rig, bridge, rcon, variant, output)
        rcon.command('opticsdemo stop ' + ACTOR)
        (output / 'rcon.json').write_text(json.dumps(rcon.history, indent=2) + '\n')
    finally:
        primary_failure: bool = sys.exc_info()[0] is not None
        failures: list[str] = []
        if rcon is not None:
            cleanup_attempt(failures, 'stop scene', lambda: rcon.command('opticsdemo stop ' + ACTOR))
            cleanup_attempt(failures, 'save RCON history', lambda: (output / 'rcon.json').write_text(json.dumps(rcon.history, indent=2) + '\n'))
            cleanup_attempt(failures, 'close RCON socket', rcon.socket.close)
        try:
            bridge.command('quit', request_timeout=3)
        except (OSError, TimeoutError):
            pass
        except Exception:
            failures.append('request client quit')
            traceback.print_exc()
        exited: bool = cleanup_attempt(failures, 'verify owned client exit', lambda: stop_owned_processes(profile))
        if launcher is not None:
            cleanup_attempt(failures, 'reap launcher request', lambda: launcher.wait(timeout=10))
        if log is not None:
            cleanup_attempt(failures, 'close launcher log', log.close)
        (output / 'profile.json').write_text(json.dumps({'name': profile_name, 'path': str(profile), 'processId': pid}) + '\n')
        client_log = profile / '.minecraft/logs/latest.log'
        if client_log.is_file():
            cleanup_attempt(failures, 'retain client log', lambda: shutil.copy2(client_log, output / 'client.log'))
        if exited and (profile / '.optics-demo-owned').is_file() and (profile / '.optics-demo-owned').read_text().strip() == profile_name:
            cleanup_attempt(failures, 'remove owned client profile', lambda: shutil.rmtree(profile))
        (output / 'cleanup.json').write_text(json.dumps({'clientExited': exited, 'failures': failures}) + '\n')
        if failures and not primary_failure:
            raise RuntimeError('Owned recording cleanup failed: ' + ', '.join(failures))


def main() -> None:
    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    parser.add_argument('--versions', nargs='+', choices=list(PAPER_BUILDS), default=list(PAPER_BUILDS))
    parser.add_argument('--variants', nargs='+', choices=['unmodded', 'modded'], default=['unmodded', 'modded'])
    parser.add_argument('--only', action='append', default=[])
    parser.add_argument('--verify-reload', action='store_true')
    parser.add_argument('--rig-root', type=Path, default=ROOT.parents[1])
    parser.add_argument('--media', type=Path, default=ROOT / 'build/media')
    args: argparse.Namespace = parser.parse_args()
    spec: object = importlib.util.spec_from_file_location('optics_capture_rig', args.rig_root / 'src/test/demo/studio.py')
    rig: object = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rig)
    sheet: dict = json.loads((ROOT / 'shots/scenes.json').read_text())
    scenes: list[dict] = [scene for scene in sheet['scenes'] if not args.only or scene['id'] in args.only]
    if not scenes or set(args.only) - {scene['id'] for scene in scenes}:
        raise ValueError('Unknown or empty scene selection')
    jar: Path = ROOT / 'build/libs/OpticsDemo-0.1.0.jar'
    if not jar.is_file():
        raise FileNotFoundError('Build the standalone demo jar before recording')
    if any(path.stat().st_mtime_ns > jar.stat().st_mtime_ns for path in (ROOT / 'src').rglob('*') if path.is_file()):
        raise RuntimeError('Demo source changed after the jar was built; run the build gate again')
    with rig.claim_studio(ROOT / 'build/recordings.lock'):
        for version in args.versions:
            name: str = 'optics-demo-' + version.replace('.', '-') + '-' + secrets.token_hex(3)
            output: Path = ROOT / 'build/recordings' / name
            output.mkdir(parents=True)
            server: Path | None = None
            created: bool = False
            try:
                paper: Path = rig.MUX / 'consumers/plugin-consumers/builds/paper' / ('paper-' + version + '-' + PAPER_BUILDS[version] + '.jar')
                rig.mux('server', 'create', name, '--jar', str(paper), '--type', 'paper', '--isolated')
                created = True
                server = Path(rig.mux('instance', 'path', name))
                server_port: int = rig.free_port()
                rig.mux('instance', 'port', name, str(server_port))
                rig.mux('gameplay', 'prepare', name)
                write_properties(server / 'server.properties', {'enable-rcon': 'true', 'rcon.port': str(rig.free_port()),
                                 'rcon.password': secrets.token_hex(24), 'view-distance': '8', 'simulation-distance': '5',
                                 'level-type': 'minecraft:flat', 'level-seed': '9438107', 'generate-structures': 'false'})
                (server / 'plugins').mkdir(exist_ok=True)
                shutil.copy2(jar, server / 'plugins/OpticsDemo.jar')
                rig.mux('runtime', 'start', name, '--no-console')
                deadline: float = time.monotonic() + 150
                while True:
                    log_path: Path = server / 'logs/latest.log'
                    log_text: str = log_path.read_text(errors='replace') if log_path.is_file() else ''
                    if 'Done (' in log_text and 'Optics demo ready:' in log_text:
                        break
                    if time.monotonic() > deadline:
                        raise TimeoutError('Owned demo server did not become ready')
                    time.sleep(0.5)
                for variant in args.variants:
                    record_variant(rig, args, version, variant, server, server_port, scenes, output / variant)
            finally:
                if created:
                    primary_failure: bool = sys.exc_info()[0] is not None
                    failures: list[str] = []
                    stopped: bool = cleanup_attempt(failures, 'stop owned server', lambda: rig.mux('runtime', 'stop', name, '--graceful'))
                    if server is not None and (server / 'logs/latest.log').is_file():
                        cleanup_attempt(failures, 'retain server log', lambda: shutil.copy2(server / 'logs/latest.log', output / 'server.log'))
                    if stopped:
                        cleanup_attempt(failures, 'delete stopped owned instance', lambda: rig.mux('instance', 'delete', name))
                    (output / 'cleanup.json').write_text(json.dumps({'serverStopped': stopped, 'failures': failures}) + '\n')
                    if failures and not primary_failure:
                        raise RuntimeError('Owned server cleanup failed: ' + ', '.join(failures))


if __name__ == '__main__':
    main()
