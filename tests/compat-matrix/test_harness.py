"""Offline unit tests for the compatibility harness. No server or network is used."""

from __future__ import annotations

import hashlib
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional

import harness


class FakeResponse:
    def __init__(self, body: bytes, status: int = 200, headers: Optional[Dict[str, str]] = None):
        self.body = body
        self.status = status
        self.headers = headers or {}
        self.offset = 0

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *args: Any) -> None:
        return None

    def read(self, size: int = -1) -> bytes:
        if size < 0:
            result = self.body[self.offset :]
            self.offset = len(self.body)
            return result
        result = self.body[self.offset : self.offset + size]
        self.offset += len(result)
        return result

    def getcode(self) -> int:
        return self.status


class FakeProcess:
    def __init__(self, exit_code: Optional[int] = None):
        self.returncode = exit_code

    def poll(self) -> Optional[int]:
        return self.returncode


class FakeStdin:
    def __init__(self) -> None:
        self.value = b""
        self.flushed = False

    def write(self, value: bytes) -> None:
        self.value += value

    def flush(self) -> None:
        self.flushed = True


class HarnessTest(unittest.TestCase):
    def test_provision_token_stores_only_the_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            token_dir = Path(directory) / "tokens.d"
            secret = harness.provision_token(token_dir, lambda _: b"a" * 32)

            self.assertTrue(secret.startswith("dmc_"))
            self.assertNotIn("=", secret)
            record_path = token_dir / "compat-harness.json"
            record = json.loads(record_path.read_text(encoding="utf-8"))
            self.assertEqual(set(record), {"id", "label", "secretHash"})
            self.assertEqual(record["id"], "compat-harness")
            self.assertEqual(
                record["secretHash"], hashlib.sha256(secret.encode("utf-8")).hexdigest()
            )
            self.assertNotIn(secret, record_path.read_text(encoding="utf-8"))

    def test_resolve_build_uses_official_checksum(self) -> None:
        entry = {"project": "paper", "minecraftVersion": "26.3", "build": 256}
        builds = [
            {
                "id": 255,
                "channel": "STABLE",
                "downloads": {"server:default": {"checksums": {"sha256": "f" * 64}}},
            },
            {
                "id": 256,
                "channel": "STABLE",
                "downloads": {
                    "server:default": {
                        "checksums": {"sha256": "a" * 64},
                        "size": 12,
                        "url": "https://example.invalid/server.jar",
                    }
                },
            },
        ]

        build = harness.resolve_build(entry, lambda _: builds)

        self.assertEqual(build["sha256"], "a" * 64)
        self.assertEqual(build["id"], 256)

    def test_download_rejects_checksum_mismatch(self) -> None:
        payload = b"not the expected server"
        build = {
            "sha256": "0" * 64,
            "size": len(payload),
            "url": "https://example.invalid/server.jar",
        }

        with self.assertRaises(harness.HarnessError):
            harness.download_server(
                build, Path(tempfile.mkdtemp()), lambda _request, **_kwargs: FakeResponse(payload)
            )

    def test_wait_for_done_waits_for_the_signal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "server.log"
            log.write_text("Preparing level\nLoading DeployMC\nDone (2.345s)!\n", encoding="utf-8")

            harness.wait_for_done(log, FakeProcess(), timeout_seconds=1)

    def test_wait_for_done_fails_when_server_exits(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "server.log"
            log.write_text("No done signal\n", encoding="utf-8")

            with self.assertRaises(harness.HarnessError):
                harness.wait_for_done(log, FakeProcess(exit_code=1), timeout_seconds=1)

    def test_wait_for_log_observes_adapter_release(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "server.log"
            log.write_text("[INFO] " + harness.ADAPTER_RELEASED_LOG + "\n", encoding="utf-8")

            harness.wait_for_log(
                log,
                FakeProcess(),
                re.escape(harness.ADAPTER_RELEASED_LOG),
                timeout_seconds=1,
            )

    def test_send_console_command_writes_disable_command(self) -> None:
        stdin = FakeStdin()

        harness.send_console_command(stdin, harness.DISABLE_COMMAND)

        self.assertEqual(stdin.value, b"fixture-disable-deploymc\n")
        self.assertTrue(stdin.flushed)

    def test_inspect_compatibility_jars_detects_relocation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            deploy = root / "deploy.jar"
            conflict = root / "conflict.jar"
            with zipfile.ZipFile(deploy, "w") as archive:
                archive.writestr("io/github/woxlo1/deploymc/lib/netty/ByteBuf.class", b"x")
                archive.writestr("plugin.yml", b"name: DeployMC\n")
            with zipfile.ZipFile(conflict, "w") as archive:
                archive.writestr("io/netty/ByteBuf.class", b"y")

            evidence = harness.inspect_compatibility_jars(deploy, conflict)

            self.assertTrue(evidence["conflictingNettyBundled"])
            self.assertTrue(evidence["relocatedNettyBundled"])
            self.assertTrue(evidence["deployUnrelocatedNettyAbsent"])
            self.assertTrue(evidence["noNettyClassOverlap"])
            self.assertEqual(evidence["relocatedNettyClassCount"], 1)
            self.assertEqual(evidence["conflictingNettyClassCount"], 1)

    def test_deployed_netty_threads_distinguishes_before_and_after(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before = root / "before.txt"
            after = root / "after.txt"
            before.write_text(
                '"DeployMC-netty-boss-1" #1 prio=8\n'
                '"DeployMC-netty-worker-1" #2 prio=8\n'
                '"Server thread" #3 prio=5\n',
                encoding="utf-8",
            )
            after.write_text('"Server thread" #3 prio=5\n', encoding="utf-8")

            threads_before = harness.deployed_netty_threads(before)
            threads_after = harness.deployed_netty_threads(after)

            self.assertEqual(
                threads_before,
                ["DeployMC-netty-boss-1", "DeployMC-netty-worker-1"],
            )
            self.assertEqual(threads_after, [])
            self.assertFalse(set(threads_before).intersection(threads_after))

    def test_smoke_test_checks_auth_and_schema(self) -> None:
        info = {
            "deployMcVersion": "0.1.0-SNAPSHOT",
            "apiVersion": "v1",
            "platform": {
                "softwareName": "Paper",
                "softwareVersion": "1.21.11",
                "minecraftVersion": "1.21.11",
                "javaVersion": "21",
                "operatingSystem": "Test",
            },
            "capabilities": {"command.dispatch": "AVAILABLE"},
        }
        calls: List[Any] = []

        def fake_urlopen(request: Any, **_kwargs: Any) -> FakeResponse:
            calls.append(request)
            if request.get_header("Authorization") is None:
                return FakeResponse(b'{"code":"auth.required"}', status=401)
            return FakeResponse(
                json.dumps(info).encode("utf-8"),
                headers={"ETag": '"abc"', "X-Request-Id": "request-1"},
            )

        result = harness.smoke_test("http://127.0.0.1:1234", "dmc_test", fake_urlopen)

        self.assertEqual(result["apiVersion"], "v1")
        self.assertEqual(result["capabilityCount"], 1)
        self.assertEqual(calls[0].get_header("Authorization"), None)
        self.assertEqual(calls[1].get_header("Authorization"), "Bearer dmc_test")

    def test_check_server_log_detects_token_leak(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "server.log"
            log.write_text(
                "DeployMC 0.1.0-SNAPSHOT\nBind: 127.0.0.1:25565\nAuth mode: token\n"
                "WARNING: No external restart script is configured.\n"
                + harness.ADAPTER_RELEASED_LOG
                + "\nSECRET=dmc_leaked\n",
                encoding="utf-8",
            )

            checks = harness.check_server_log(log, "dmc_leaked", 25565)

            self.assertFalse(checks["noTokenInLog"])
            self.assertTrue(checks["pluginLoaded"])
            self.assertTrue(checks["startupBannerBind"])
            self.assertTrue(checks["adapterTasksReleased"])

    def test_write_config_replaces_only_the_bind_port(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "config.yml"
            harness.write_config(harness.DEFAULT_CONFIG_TEMPLATE, target, 30123)
            text = target.read_text(encoding="utf-8")

            self.assertIn("port: 30123", text)
            self.assertNotIn("port: 8080", text)
            self.assertIn("auth:", text)

    def test_prepare_sandbox_uses_distinct_api_and_minecraft_ports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            server_jar = root / "server.jar"
            plugin_jar = root / "plugin.jar"
            conflict_jar = root / "conflict.jar"
            server_jar.write_bytes(b"server")
            plugin_jar.write_bytes(b"plugin")
            conflict_jar.write_bytes(b"conflict")

            sandbox = harness.prepare_sandbox(
                root / "work",
                server_jar,
                plugin_jar,
                conflict_jar,
                harness.DEFAULT_CONFIG_TEMPLATE,
                40123,
                40223,
            )

            config = (sandbox["root"] / "plugins" / "DeployMC" / "config.yml").read_text(
                encoding="utf-8"
            )
            properties = (sandbox["root"] / "server.properties").read_text(encoding="utf-8")
            self.assertIn("port: 40123", config)
            self.assertIn("server-port=40223", properties)
            self.assertNotIn("server-port=40123", properties)
            conflict_copy = sandbox["root"] / "plugins" / "LegacyNettyFixture.jar"
            self.assertEqual(conflict_copy.read_bytes(), b"conflict")
            with self.assertRaises(harness.HarnessError):
                harness.prepare_sandbox(
                    root / "work",
                    server_jar,
                    plugin_jar,
                    conflict_jar,
                    harness.DEFAULT_CONFIG_TEMPLATE,
                    40123,
                    40123,
                )

    def test_render_section_replaces_markers(self) -> None:
        matrix = harness.BEGIN_MARKER + "\nold\n" + harness.END_MARKER + "\n"
        results = [
            {
                "suite": "compat-v1",
                "entry": "paper-26.3",
                "status": "pass",
                "server": {"minecraftVersion": "26.3", "build": 256},
                "runtime": {
                    "os": "Windows",
                    "deployMcCommit": "abc1234",
                    "runUrl": "https://example.invalid/run",
                },
                "finishedAt": "2026-11-20T00:00:00Z",
            }
        ]
        section = harness.render_smoke_section(results)

        self.assertIn("| compat-v1 | paper-26.3 | 26.3 | 256 |", section)
        self.assertIn(harness.BEGIN_MARKER, section)
        self.assertIn(harness.END_MARKER, section)


if __name__ == "__main__":
    unittest.main()
