import argparse
import importlib.util
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import record


class RecordingTests(unittest.TestCase):
    def setUp(self) -> None:
        spec: object = importlib.util.spec_from_file_location('capture_rig', record.ROOT.parents[1] / 'src/test/demo/studio.py')
        self.rig: object = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.rig)

    def state(self) -> dict:
        return {**self.rig.HIDDEN_RENDERER_STATE, 'firstPerson': True, 'connected': True,
                'hudVisible': False, 'screen': None, 'captureFrames': 600, 'captureEncodedFrames': 600,
                'captureRepeatedFrames': 0, 'captureDroppedFrames': 0, 'captureSeconds': 20,
                'captureSource': '1920x1080', 'captureMaxGapFrames': 0, 'captureHolds': []}

    def test_long_freeze_fails_even_when_average_rate_passes(self) -> None:
        state: dict = {**self.state(), 'captureFrames': 570, 'captureRepeatedFrames': 30,
                       'captureMaxGapFrames': 30, 'captureHolds': [{'startFrame': 100, 'endFrame': 130}]}
        self.rig.capture_evidence(state)
        with self.assertRaisesRegex(AssertionError, 'freeze'):
            record.validate_capture(self.rig, state)
        record.validate_capture(self.rig, self.state())

    def test_capture_rejects_hud_menu_and_unsafe_native_state(self) -> None:
        for changed in ({'hudVisible': True}, {'screen': 'DisconnectedScreen'}, {'firstPerson': False},
                        {'windowVisible': True}, {'mouseGrabbed': True}):
            with self.subTest(changed=changed), self.assertRaises(AssertionError if 'windowVisible' not in changed and 'mouseGrabbed' not in changed else RuntimeError):
                record.validate_capture(self.rig, {**self.state(), **changed})

    def test_partial_profile_setup_is_removed_after_proven_exit(self) -> None:
        self.assert_partial_cleanup(True)

    def test_review_frames_follow_observed_host_progress(self) -> None:
        observations: list[dict] = [{'host': {'tick': tick}, 'captureSeconds': second}
                                    for tick, second in ((0, 1.5), (20, 4.5), (40, 7.5))]
        self.assertAlmostEqual(6.2, record.review_time(1.5, observations, 9.0))
        self.assertAlmostEqual(7.7, record.review_time(2.0, observations, 9.0))
        self.assertAlmostEqual(8.8, record.review_time(3.0, observations, 9.0))

    def test_partial_profile_is_retained_when_exit_cannot_be_proved(self) -> None:
        self.assert_partial_cleanup(False)

    def artifact(self, root: Path, version: str, **changes: object) -> Path:
        (root / 'gradle/minecraft-versions').mkdir(parents=True, exist_ok=True)
        (root / 'adapters/fabric').mkdir(parents=True, exist_ok=True)
        (root / 'gradle.properties').write_text('minecraftVersion=26.3\nwormholesVersion=3.0.0\n')
        (root / 'adapters/fabric/build.gradle').write_text('')
        (root / 'gradle/minecraft-versions' / (version + '.json')).write_text(json.dumps({
            'minecraft': version, 'loaders': {'fabric': '0.19.5'}, 'fabricModules': {'fabric-api-base': '2.0.0'}}))
        build: Path = root / 'adapters/fabric/build'
        if version != '26.3':
            build /= version
        (build / 'libs').mkdir(parents=True, exist_ok=True)
        jar: Path = build / 'libs' / ('Wormholes v3.0.0 [Fabric] ' + version + '+0.19.5.jar')
        metadata: dict = {'id': 'wormholes', 'version': '3.0.0',
                          'depends': {'minecraft': version, 'fabricloader': '>=0.19.5', 'java': '>=25'},
                          'entrypoints': {'client': ['art.arcane.wormholes.fabric.WormholesFabricClient']},
                          'jars': [{'file': 'META-INF/jars/fabric-api-base.jar'}]}
        metadata.update(changes)
        payload: io.BytesIO = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as nested:
            nested.writestr('fabric.mod.json', json.dumps({'id': 'fabric-api-base', 'version': '2.0.0',
                                                          'depends': {'fabricloader': '>=0.19.5'}}))
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps(metadata))
            archive.writestr('wormholes-target.properties', 'minecraft=' + version + '\nloader=fabric\n'
                            'loaderVersion=0.19.5\nrenderRuntimeSupported=true\n')
            archive.writestr('wormholes-build.properties', 'version=3.0.0\n')
            archive.writestr('META-INF/jars/fabric-api-base.jar', payload.getvalue())
            for name in ('fabric/WormholesFabricClient', 'modded/client/WormholesClient',
                         'modded/client/render/ClientPortalRenderer'):
                archive.writestr('art/arcane/wormholes/' + name + '.class', b'synthetic')
        return jar

    def test_artifact_selects_current_version_in_target_build_only(self) -> None:
        for version in ('26.2', '26.3'):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root: Path = Path(directory)
                jar: Path = self.artifact(root, version)
                (jar.parent / 'Wormholes v2.2.1 [Fabric] 26.3+0.19.5.jar').write_bytes(b'old')
                chosen: Path
                provenance: dict
                chosen, provenance = record.wormholes_artifact(root, version, '0.19.5')
                self.assertEqual(jar, chosen)
                self.assertEqual(record.identity(jar), provenance['source'])
                self.assertEqual([], provenance['externalRendererMods'])
                jar.unlink()
                with self.assertRaises(FileNotFoundError):
                    record.wormholes_artifact(root, version, '0.19.5')

    def test_artifact_rejects_wrong_target_missing_api_and_new_dependency(self) -> None:
        for changes in ({'depends': {'minecraft': '26.2'}}, {'jars': []},
                        {'depends': {'minecraft': '26.3', 'fabricloader': '>=0.19.5', 'java': '>=25',
                                     'required-renderer': '*'}}):
            with self.subTest(changes=changes), tempfile.TemporaryDirectory() as directory:
                root: Path = Path(directory)
                self.artifact(root, '26.3', **changes)
                with self.assertRaises(RuntimeError):
                    record.wormholes_artifact(root, '26.3', '0.19.5')

    def test_artifact_rejects_old_loader_and_source_newer_than_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root: Path = Path(directory)
            jar: Path = self.artifact(root, '26.2')
            with self.assertRaisesRegex(RuntimeError, 'Fabric loader'):
                record.wormholes_artifact(root, '26.2', '0.19.3')
            os.utime(root / 'adapters/fabric/build.gradle', ns=(jar.stat().st_mtime_ns + 1000000000,) * 2)
            with self.assertRaisesRegex(RuntimeError, 'Source changed'):
                record.wormholes_artifact(root, '26.2', '0.19.5')

    def test_artifact_freshness_covers_platform_version_source_overrides(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root: Path = Path(directory)
            jar: Path = self.artifact(root, '26.2')
            source: Path = root / 'adapters/modded-common/src/26.2/java/NativeRenderer.java'
            source.parent.mkdir(parents=True)
            source.write_text('synthetic')
            os.utime(source, ns=(jar.stat().st_mtime_ns + 1000000000,) * 2)
            with self.assertRaisesRegex(RuntimeError, 'NativeRenderer'):
                record.wormholes_artifact(root, '26.2', '0.19.5')

    def test_loaded_mod_gate_matches_deployed_version_and_rejects_sodium_substitution(self) -> None:
        artifacts: dict = {'loader': '0.19.5', 'sources': [{'id': 'instance-automator', 'version': '1.0.0'}],
                           'wormholes': None}
        log: str = '\n- minecraft 26.3\n- fabricloader 0.19.5\n- java 25\n- instance-automator 1.0.0\n'
        record.validate_loaded_mods(log, '26.3', 'unmodded', artifacts)
        with self.assertRaisesRegex(AssertionError, 'Unexpected'):
            record.validate_loaded_mods(log + '- sodium 0.9.2\n', '26.3', 'unmodded', artifacts)
        artifacts['sources'].append({'id': 'wormholes', 'version': '3.0.0'})
        artifacts['wormholes'] = {'bundledFabricModules': {'fabric-api-base': {'version': '2.0.0'}}}
        native: str = log + '- wormholes 3.0.0\n   \\-- fabric-api-base 2.0.0\n'
        with self.assertRaisesRegex(AssertionError, 'initialization'):
            record.validate_loaded_mods(native, '26.3', 'modded', artifacts)
        native += 'Wormholes ClientView client ready (renderer=native, plate budget 256 MiB, bulk writes false)\n'
        record.validate_loaded_mods(native, '26.3', 'modded', artifacts)
        with self.assertRaisesRegex(AssertionError, 'provenance'):
            record.validate_loaded_mods(native.replace('wormholes 3.0.0', 'wormholes 2.2.1'),
                                        '26.3', 'modded', artifacts)

    def test_loader_bundled_modules_require_matching_artifact_versions(self) -> None:
        artifacts: dict = {'loader': '0.19.5', 'sources': [], 'wormholes': None,
                           'loaderArtifact': {'modules': {'mixinextras': {'version': '0.5.5'}}}}
        loaded: str = '- minecraft 26.2\n- fabricloader 0.19.5\n- java 25\n- mixinextras 0.5.5\n'
        record.validate_loaded_mods(loaded, '26.2', 'unmodded', artifacts)
        with self.assertRaisesRegex(AssertionError, 'provenance'):
            record.validate_loaded_mods(loaded.replace('mixinextras 0.5.5', 'mixinextras 0.5.4'),
                                        '26.2', 'unmodded', artifacts)

    def native_host(self) -> dict:
        return {'scene': 'spin', 'tick': '0', 'renderer': 'clientview-native', 'ready': 'true',
                'streamState': 'CLIENT_VIEW', 'streamCaps': str(record.NATIVE_CAPABILITIES),
                'visible': '0', 'visiblePortals': '1', 'sampleRevision': '12'}

    def native_proof(self) -> dict:
        return {'renderer': 'clientview-native', 'streamState': 'CLIENT_VIEW',
                'streamCaps': record.NATIVE_CAPABILITIES, 'preparedFrame': 100, 'terrainFrames': 75,
                'terrainDraws': 150, 'lastTerrainFrame': 100, 'visiblePortals': 1, 'meshSections': 8,
                'unavailablePortals': 0, 'lastAffineRevision': 12}

    def test_render_gate_rejects_display_fallback_and_host_only_proof(self) -> None:
        with self.assertRaisesRegex(AssertionError, 'Unexpected'):
            record.status('scene=spin renderer=sampled-virtual-scene')
        for variant, renderer in (('unmodded', 'clientview-native'), ('modded', 'block-packets')):
            with self.subTest(variant=variant), self.assertRaisesRegex(AssertionError, 'render path'):
                record.validate_render_path({**self.native_host(), 'renderer': renderer}, {}, variant)
        with self.assertRaisesRegex(AssertionError, 'installed mods'):
            record.validate_render_path(self.native_host(), {}, 'modded')
        record.validate_render_path({'renderer': 'block-packets', 'ready': 'true', 'visible': '1',
                                     'packetBatches': '2', 'packetChanges': '64'}, {}, 'unmodded')

    def test_standard_packet_gate_requires_actual_delivery_counters(self) -> None:
        host: dict = {'renderer': 'block-packets', 'ready': 'true', 'visible': '1',
                       'packetBatches': '2', 'packetChanges': '64'}
        for changes in ({'packetBatches': ''}, {'packetChanges': '0'}, {'packetBatches': '0'}):
            with self.subTest(changes=changes), self.assertRaises(AssertionError):
                record.validate_render_path({**host, **changes}, {}, 'unmodded')

    def test_profiles_preserve_hidden_launch_and_install_only_the_intended_mods(self) -> None:
        for version in ('26.2', '26.3'):
            for variant in ('unmodded', 'modded'):
                with self.subTest(version=version, variant=variant), tempfile.TemporaryDirectory() as directory:
                    root: Path = Path(directory)
                    self.artifact(root, version)
                    prism: Path = root / 'prism'
                    loader: Path = prism / 'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar'
                    loader.parent.mkdir(parents=True)
                    with zipfile.ZipFile(loader, 'w') as archive:
                        archive.writestr('fabric.mod.json', json.dumps({'id': 'fabricloader', 'version': '0.19.5'}))
                    template: Path = prism / 'instances' / record.TEMPLATES[version]
                    template.mkdir(parents=True)
                    (template / 'mmc-pack.json').write_text(json.dumps({'components': [
                        {'uid': 'net.minecraft', 'version': version},
                        {'uid': 'net.fabricmc.fabric-loader', 'version': '0.19.5'}]}))
                    bridge: Path = root / 'build/client-automator'
                    if version != '26.3':
                        bridge /= version
                    bridge.mkdir(parents=True)
                    with zipfile.ZipFile(bridge / 'InstanceAutomator.jar', 'w') as archive:
                        archive.writestr('fabric.mod.json', json.dumps({'id': 'instance-automator', 'version': '1.0.0'}))
                    output: Path = root / 'evidence'
                    output.mkdir()
                    profile: Path = record.make_profile(SimpleNamespace(PRISM=prism, ROOT=root), version,
                                                        variant, output, 32100, 'synthetic', 'synthetic-owned')
                    self.assertIn('-Dautomator.hidden=true', (profile / 'instance.cfg').read_text())
                    self.assertIn('-Dautomator.muted=true', (profile / 'instance.cfg').read_text())
                    self.assertEqual({'InstanceAutomator.jar'} | ({'Wormholes.jar'} if variant == 'modded' else set()),
                                     {path.name for path in (profile / '.minecraft/mods').iterdir()})
                    evidence: dict = json.loads((output / 'client-artifacts.json').read_text())
                    self.assertEqual(2 if variant == 'modded' else 1, len(evidence['sources']))
                    for deployed in evidence['sources']:
                        self.assertEqual(deployed['source']['sha256'], deployed['deployed']['sha256'])

    def test_native_gate_requires_negotiated_affine_mesh_and_actual_fresh_composition(self) -> None:
        record.validate_render_path(self.native_host(), {'wormholesProjection': self.native_proof()},
                                    'modded', fresh=True)
        for changes in ({'streamCaps': 1 << 12}, {'streamState': 'VANILLA'}, {'terrainFrames': 0},
                        {'terrainDraws': 0}, {'lastTerrainFrame': 90}, {'meshSections': 0},
                        {'visiblePortals': 0}, {'unavailablePortals': 1}, {'lastAffineRevision': 11},
                        {'terrainFrames': True}, {'terrainFrames': -1}):
            with self.subTest(changes=changes), self.assertRaises(AssertionError):
                record.validate_render_path(self.native_host(),
                                            {'wormholesProjection': {**self.native_proof(), **changes}},
                                            'modded', fresh=True)
        for changes in ({'ready': 'false'}, {'streamCaps': '0'}, {'sampleRevision': '0'}, {'visiblePortals': ''}):
            with self.subTest(changes=changes), self.assertRaises(AssertionError):
                record.validate_render_path({**self.native_host(), **changes},
                                            {'wormholesProjection': self.native_proof()}, 'modded', fresh=True)

    def test_native_proof_rejects_frozen_composition_despite_advancing_gpu_frames(self) -> None:
        previous: dict = self.native_proof()
        current: dict = {**previous, 'preparedFrame': 110, 'lastTerrainFrame': 110}
        with self.assertRaisesRegex(AssertionError, 'without successful'):
            record.validate_native_progress(previous, current, True)
        current.update(terrainFrames=85, terrainDraws=170, lastAffineRevision=22)
        record.validate_native_progress(previous, current, True)
        current['lastAffineRevision'] = 1
        with self.assertRaisesRegex(AssertionError, 'reset'):
            record.validate_native_progress(previous, current, True)

    def test_native_proof_allows_intentional_vanish_without_new_terrain_draw(self) -> None:
        previous: dict = self.native_proof()
        vanished: dict = {**previous, 'preparedFrame': 200, 'visiblePortals': 0, 'meshSections': 0}
        record.validate_render_path({**self.native_host(), 'visiblePortals': '0', 'sampleRevision': '99'},
                                    {'wormholesProjection': vanished}, 'modded')
        record.validate_native_progress(previous, vanished, False)

    def test_native_delivery_readiness_can_lag_during_an_active_rendered_animation(self) -> None:
        record.validate_render_path({**self.native_host(), 'ready': 'false', 'sampleRevision': '13'},
                                    {'wormholesProjection': self.native_proof()}, 'modded')
        with self.assertRaises(AssertionError):
            record.validate_render_path({**self.native_host(), 'ready': 'false'},
                                        {'wormholesProjection': self.native_proof()}, 'modded', fresh=True)

    def test_native_gate_requires_all_expected_visible_portals(self) -> None:
        with self.assertRaisesRegex(AssertionError, 'fresh projected-terrain'):
            record.validate_render_path({**self.native_host(), 'visiblePortals': '2'},
                                        {'wormholesProjection': self.native_proof()}, 'modded', fresh=True)

    def test_readiness_times_out_without_client_proof_and_waits_for_negotiation(self) -> None:
        host: dict = self.native_host()
        response: str = ' '.join(key + '=' + value for key, value in host.items())
        bridge: object = SimpleNamespace(state=lambda **kwargs: self.state())
        rcon: object = SimpleNamespace(command=lambda *args: response)
        with patch.object(record.time, 'monotonic', side_effect=[0, 21]), self.assertRaises(TimeoutError) as failure:
            record.await_projection(self.rig, bridge, rcon, 'modded', 'spin')
        self.assertIn('installed mods', str(failure.exception.__cause__))
        client: dict = {**self.state(), 'wormholesProjection': self.native_proof()}
        bridge.state = lambda **kwargs: client
        pending: str = response.replace('renderer=clientview-native', 'renderer=block-packets')
        with patch.object(record.time, 'monotonic', side_effect=[0, 1]), patch.object(record.time, 'sleep'), \
                patch.object(rcon, 'command', side_effect=[pending, response]):
            observed: dict
            observed, _, _ = record.await_projection(self.rig, bridge, rcon, 'modded', 'spin')
            self.assertEqual('clientview-native', observed['renderer'])

    def assert_partial_cleanup(self, exited: bool) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root: Path = Path(directory)
            (root / 'instances').mkdir()
            bridge: object = SimpleNamespace(token='synthetic', command=lambda *args, **kwargs: None)
            rig: object = SimpleNamespace(PRISM=root, free_port=lambda: 32100, Bridge=lambda *args: bridge)

            def fail_setup(*args: object) -> None:
                profile: Path = root / 'instances' / args[-1]
                profile.mkdir()
                (profile / '.optics-demo-owned').write_text(args[-1] + '\n')
                raise ValueError('setup failure')

            with patch.object(record, 'make_profile', side_effect=fail_setup), \
                    patch.object(record, 'stop_owned_processes', side_effect=None if exited else RuntimeError('still alive')), \
                    patch.object(record.traceback, 'print_exc'), self.assertRaisesRegex(ValueError, 'setup failure'):
                record.record_variant(rig, argparse.Namespace(), '26.2', 'unmodded', root / 'server', 32000,
                                      [], root / 'evidence')
            cleanup: dict = json.loads((root / 'evidence/cleanup.json').read_text())
            self.assertEqual(exited, cleanup['clientExited'])
            self.assertEqual(0 if exited else 1, len(list((root / 'instances').iterdir())))


if __name__ == '__main__':
    unittest.main()
