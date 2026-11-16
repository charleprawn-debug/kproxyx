#!/usr/bin/env python3
"""Run actual generated shell against a fault-injectable model; never uses root."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
CHAIN = "KPX_WEBRTC_BLOCK"
MOCK = r'''
import json, os, sys
from pathlib import Path
path = Path(os.environ["FIREWALL_STATE"])
state = json.loads(path.read_text())
family = Path(sys.argv[0]).name
args = sys.argv[1:]
if args[:1] == ["-w"]:
    args = args[2:]
op = args[0]
if os.environ.get("FAIL") == family + ":" + op:
    sys.exit(4)
chains = state[family]
chain = args[1] if len(args) > 1 else None
rule = args[2:]
if op == "-S":
    if chain is not None and chain not in chains:
        sys.exit(1)
    for name, rules in chains.items():
        if chain is not None and name != chain:
            continue
        print("-N " + name)
        for entry in rules:
            print("-A " + name + " " + " ".join(entry))
elif op == "-N":
    if chain in chains:
        sys.exit(1)
    chains[chain] = []
elif chain not in chains:
    sys.exit(1)
elif op == "-A":
    chains[chain].append(rule)
elif op == "-I":
    chains[chain].insert(int(rule[0]) - 1, rule[1:])
elif op == "-C":
    sys.exit(0 if rule in chains[chain] else 1)
elif op == "-D":
    if rule not in chains[chain]:
        sys.exit(1)
    chains[chain].remove(rule)
elif op == "-F":
    chains[chain] = []
elif op == "-X":
    if chains[chain] or any(
        ["-j", chain] == r[-2:] for rules in chains.values() for r in rules
    ):
        sys.exit(1)
    del chains[chain]
else:
    sys.exit(2)
path.write_text(json.dumps(state))
'''


class FirewallTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory()
        build = Path(cls.build.name)
        runner = build / "Emit.kt"
        runner.write_text(
            'package com.proxyplatform.app\n'
            'fun main(args: Array<String>) { '
            'print(if (args[0] == "apply") RootWebRtcRules.apply(10123) '
            'else RootWebRtcRules.restore) }\n'
        )
        jar = build / "rules.jar"
        source = ROOT / "android/app/src/main/java/com/proxyplatform/app/RootWebRtcRules.kt"
        subprocess.run(
            ["kotlinc", str(source), str(runner), "-include-runtime", "-d", str(jar)],
            check=True, timeout=120,
        )
        cls.scripts = {
            kind: subprocess.check_output(
                ["java", "-jar", str(jar), kind], text=True, timeout=30,
            )
            for kind in ("apply", "restore")
        }

    @classmethod
    def tearDownClass(cls):
        cls.build.cleanup()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.state_path = self.folder / "state.json"
        self.original = {
            name: {
                "OUTPUT": [["-p", "tcp", "--dport", "22", "-j", "ACCEPT"]],
                "FORWARD": [["-j", "ACCEPT"]],
            }
            for name in ("iptables", "ip6tables")
        }
        self.state_path.write_text(json.dumps(self.original))
        for name in self.original:
            binary = self.folder / name
            binary.write_text("#!" + sys.executable + "\n" + MOCK)
            binary.chmod(0o755)
        self.env = {
            **os.environ,
            "PATH": str(self.folder),
            "FIREWALL_STATE": str(self.state_path),
        }

    def run_script(self, name, fail=None):
        result = subprocess.run(
            [os.environ.get("TEST_SHELL", "/bin/sh"), "-c", self.scripts[name]],
            env={**self.env, **({"FAIL": fail} if fail else {})},
            text=True, capture_output=True, timeout=30,
        )
        return result.returncode

    def state(self):
        return json.loads(self.state_path.read_text())

    def assert_restored(self):
        self.assertEqual(0, self.run_script("restore"))
        self.assertEqual(self.original, self.state())

    def permitted(self, family, proto, uid, port, interface="wlan0", sport=12345):
        """Evaluate the managed chain's modeled match semantics."""
        packet = {
            "-o": interface, "-p": proto, "--uid-owner": str(uid),
            "--dport": str(port), "--sport": str(sport),
        }
        for rule in self.state()[family][CHAIN]:
            if all(
                rule[rule.index(key) + 1] == value
                for key, value in packet.items() if key in rule
            ):
                return rule[-1] == "RETURN"
        return True  # non-TCP/UDP control traffic falls through to Android

    def test_ipv4_ipv6_direct_leaks_and_upstream(self):
        self.assertEqual(0, self.run_script("apply"))
        for family in self.original:
            for proto in ("udp", "tcp"):
                for port in (53, 443, 3478, 5349, 19302, 52000):
                    with self.subTest(family=family, proto=proto, port=port):
                        self.assertFalse(self.permitted(family, proto, 10222, port))
                self.assertTrue(self.permitted(family, proto, 10222, 10808, "lo"))
            self.assertTrue(self.permitted(family, "tcp", 10123, 3478))
            self.assertFalse(self.permitted(family, "udp", 10123, 3478))
            for uid in (0, 1000, 1051):
                self.assertTrue(self.permitted(family, "udp", uid, 53))
                self.assertTrue(self.permitted(family, "tcp", uid, 53))
            self.assertEqual(["-j", CHAIN], self.state()[family]["OUTPUT"][0])
            self.assertEqual(self.original[family]["FORWARD"], self.state()[family]["FORWARD"])
        self.assert_restored()

    def test_restore_is_idempotent(self):
        self.assert_restored()
        self.assert_restored()

    def test_partial_second_family_build_rolls_back(self):
        self.assertNotEqual(0, self.run_script("apply", "ip6tables:-A"))
        for family in self.original:
            self.assertEqual(self.original[family]["OUTPUT"], self.state()[family]["OUTPUT"])
        self.assert_restored()

    def test_partial_second_family_attach_rolls_back(self):
        self.assertNotEqual(0, self.run_script("apply", "ip6tables:-I"))
        self.assert_restored()

    def test_failed_deletion_is_not_reported_as_success(self):
        self.assertEqual(0, self.run_script("apply"))
        self.assertNotEqual(0, self.run_script("restore", "iptables:-D"))
        self.assertIn(CHAIN, self.state()["iptables"])
        self.assert_restored()

    def test_root_or_lock_failure_remains_retryable(self):
        self.assertEqual(0, self.run_script("apply"))
        self.assertNotEqual(0, self.run_script("restore", "ip6tables:-S"))
        self.assertIn(CHAIN, self.state()["ip6tables"])
        self.assert_restored()

    def test_missing_ipv6_tool_fails_closed(self):
        (self.folder / "ip6tables").unlink()
        self.assertNotEqual(0, self.run_script("apply"))
        self.assertNotEqual(0, self.run_script("restore"))
        self.assertEqual(self.original["iptables"], self.state()["iptables"])

    def test_duplicate_jump_cleanup(self):
        self.assertEqual(0, self.run_script("apply"))
        state = self.state()
        state["iptables"]["OUTPUT"].append(["-j", CHAIN])
        self.state_path.write_text(json.dumps(state))
        self.assert_restored()

    def test_does_not_flush_live_chain_on_reapply(self):
        self.assertEqual(0, self.run_script("apply"))
        active = self.state()
        self.assertNotEqual(0, self.run_script("apply"))
        self.assertEqual(active, self.state())
        self.assert_restored()


if __name__ == "__main__":
    if not shutil.which("kotlinc") or not shutil.which("java"):
        sys.exit("JDK and kotlinc are required")
    unittest.main(verbosity=2)
