"""Offline/native Windows checks. No JDBC driver loading or live credentials."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'build/azure-native'


def canonical(path):
    return hashlib.sha256(path.read_text(encoding='utf-8').encode('utf-8')).hexdigest()


class TinyProbeTest(unittest.TestCase):
    def test_reviewed_pins(self):
        pins = json.loads((HERE / 'tiny_catalog_probe.pins.json').read_text())
        for pin in pins['files']:
            self.assertEqual(pin['sha256'], canonical(ROOT / pin['path']), pin['path'])
        self.assertEqual('12.6.1.jre11', pins['jdbcVersion'])
        self.assertEqual('3b1a70145dbaff98daa70022791e15becfb2b9534cc9e8cfaa1bdba6a3edeb8e', pins['jdbcSha256'])

    def test_grants_and_reference_definitions_match_installer(self):
        source = (HERE / 'TinyCatalogProbe.java').read_text()
        installer = (ROOT / 'docs/sql/ai-integration/phase3b8-confinement.sql').read_text()
        grants = re.findall(r'"(GRANT [^"\n]+;)"', source)
        self.assertEqual(6, len(grants))
        self.assertEqual([line for line in installer.splitlines() if line.startswith('GRANT ') and line.endswith(' TO AiCaseDataCatalogVerifier;')], grants)
        for definition in re.findall(r'"(CREATE TABLE dbo.AiData(?:Unicode|Word)[^"\n]+;)"', source):
            self.assertIn(definition, installer)
        self.assertNotIn('AzureCatalogComponents.read', source)  # Sanitizing wrapper would discard SQL category.

    def test_real_java_probe_and_classifier_offline(self):
        suffix = '.exe' if os.name == 'nt' else ''
        home = os.environ.get('JAVA_HOME')
        javac = str(Path(home) / ('bin/javac' + suffix)) if home else shutil.which('javac')
        java = str(Path(home) / ('bin/java' + suffix)) if home else shutil.which('java')
        self.assertTrue(javac and java, 'JDK 21 required for the offline probe check')
        with tempfile.TemporaryDirectory(prefix='tiny-probe-offline-') as scratch:
            subprocess.run([javac, '-d', scratch, str(HERE / 'TinyCatalogProbe.java'), str(HERE / 'TinyCatalogProbeOfflineTest.java')], check=True, capture_output=True)
            result = subprocess.run([java, '-cp', scratch, 'TinyCatalogProbeOfflineTest', str(ROOT / 'shale-data/src/test/resources/ai/confinement-v2-catalog-components.sql')], check=True, capture_output=True, text=True)
            self.assertEqual('TINY_CATALOG_OFFLINE PASS azureExecuted=false', result.stdout.strip())
            self.assertEqual('', result.stderr)
            # Direct accidental invocation refuses before loading any driver/network path.
            clean = {k: v for k, v in os.environ.items() if not k.startswith('TINY_PROBE_')}
            refusal = subprocess.run([java, '-cp', scratch, 'TinyCatalogProbe'], env=clean, check=True, capture_output=True, text=True)
            self.assertEqual('LAUNCHER\tOTHER_SANITIZED\t0\tconnectionClosed=true', refusal.stdout.strip())
            self.assertEqual('', refusal.stderr)


if __name__ == '__main__':
    unittest.main()
