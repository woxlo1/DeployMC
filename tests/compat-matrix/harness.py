#!/usr/bin/env python3
"""DeployMC compatibility harness v1.

The harness downloads a pinned Paper build through PaperMC's official Fill API,
verifies its SHA-256 digest, starts a headless server, provisions a bearer token
directly into the host-controlled token directory, and calls /api/v1/server.

The harness never parses token values from console output and never writes a
secret to a result file or log. CLI output is developer tooling, not product UI.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import platform
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

USER_AGENT = "DeployMC-compat-harness/0.1.0 (https://github.com/woxlo1/DeployMC)"
FILL_BASE = "https://fill.papermc.io/v3"
HEX64 = re.compile(r"^[0-9a-f]{64}$")
DONE_SIGNAL = re.compile(r"\bDone\s*\(", re.IGNORECASE)
BEGIN_MARKER = "<!-- BEGIN GENERATED SMOKE MATRIX -->"
END_MARKER = "<!-- END GENERATED SMOKE MATRIX -->"
CONFLICT_PLUGIN_NAME = "LegacyNettyFixture"
CONFLICT_PLUGIN_VERSION = "1.0.0"
NETTY_THREAD_PREFIX = "DeployMC-netty-"
DISABLE_COMMAND = "fixture-disable-deploymc"
ADAPTER_RELEASED_LOG = "DeployMC disabled; adapter tasks and listeners released."
REQUIRED_CHECKS = {
    "checksumVerified",
    "serverDownloadedFromOfficialApi",
    "eulaAcceptedOnlyInSandbox",
    "tokenProvisionedFromHostFilesystem",
    "distinctPorts",
    "conflictingPluginInstalled",
    "conflictingPluginLoaded",
    "conflictingNettyBundled",
    "relocatedNettyBundled",
    "deployUnrelocatedNettyAbsent",
    "noNettyClassOverlap",
    "doneSignalObserved",
    "healthProbePassed",
    "httpListenerStarted",
    "threadDumpCaptured",
    "threadDumpBeforeCaptured",
    "deployNettyThreadsBeforeDisable",
    "threadDumpAfterCaptured",
    "deployNettyThreadsGoneAfterDisable",
    "adapterTasksReleased",
    "unauthenticatedRequestRejected",
    "authenticatedServerInfoPassed",
    "pluginLoaded",
    "startupBannerBind",
    "startupBannerAuth",
    "startupBannerWarning",
    "noTokenInLog",
    "noDeployMcLoaderError",
    "noConflictLoaderErrors",
    "cleanShutdown",
}

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
DEFAULT_ENTRIES = HERE / "entries.json"
DEFAULT_CACHE = HERE / ".cache"
DEFAULT_WORK = HERE / "work"
DEFAULT_CONFIG_TEMPLATE = (
    REPO_ROOT / "core" / "src" / "main" / "resources" / "config" / "default-config.yml"
)
DEFAULT_CONFLICT_JAR = (
    REPO_ROOT
    / "tests"
    / "fixtures"
    / "conflicting-plugin"
    / "build"
    / "libs"
    / "legacy-netty-fixture-1.0.0.jar"
)

UrlOpen = Callable[..., Any]


class HarnessError(RuntimeError):
    """A recoverable harness failure that must fail the run closed."""


def load_entries(path: Path = DEFAULT_ENTRIES) -> Dict[str, Dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data.get("entries")
    if not isinstance(entries, list) or not entries:
        raise HarnessError("entries.json must contain at least one entry")
    result: Dict[str, Dict[str, Any]] = {}
    for entry in entries:
        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or entry_id in result:
            raise HarnessError("each entry needs a unique id")
        result[entry_id] = entry
    return result


def get_entry(entry_id: str, entries_path: Path = DEFAULT_ENTRIES) -> Dict[str, Any]:
    entries = load_entries(entries_path)
    if entry_id not in entries:
        raise HarnessError("unknown entry: " + entry_id)
    return entries[entry_id]


def cache_key(entry: Dict[str, Any]) -> str:
    return "{}-{}-{}".format(entry["project"], entry["minecraftVersion"], entry["build"])


def fetch_json(
    url: str, urlopen: UrlOpen = urllib.request.urlopen, timeout: int = 30
) -> Any:
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"}
    )
    with urlopen(request, timeout=timeout) as response:
        status = getattr(response, "status", None)
        if status is None:
            status = response.getcode()
        body = response.read()
    if status != 200:
        raise HarnessError("API returned HTTP {}: {}".format(status, url))
    decoded = json.loads(body.decode("utf-8"))
    if isinstance(decoded, dict) and decoded.get("ok") is False:
        raise HarnessError("API error: {}".format(decoded.get("message", "unknown")))
    return decoded


def resolve_build(
    entry: Dict[str, Any], fetch: Callable[[str], Any] = fetch_json
) -> Dict[str, Any]:
    url = "{}/projects/{}/versions/{}/builds".format(
        FILL_BASE, entry["project"], entry["minecraftVersion"]
    )
    builds = fetch(url)
    if not isinstance(builds, list):
        raise HarnessError("builds response is not a list: " + url)
    matches = [item for item in builds if item.get("id") == entry["build"]]
    if len(matches) != 1:
        raise HarnessError("build {} was not found exactly once".format(entry["build"]))
    build = matches[0]
    if build.get("channel") != "STABLE":
        raise HarnessError("pinned build is not a STABLE build")
    download = build.get("downloads", {}).get("server:default")
    if not isinstance(download, dict):
        raise HarnessError("build has no server:default download")
    checksum = download.get("checksums", {}).get("sha256")
    if not isinstance(checksum, str) or not HEX64.fullmatch(checksum):
        raise HarnessError("build has no valid SHA-256 checksum")
    return {
        "id": build["id"],
        "time": build.get("time"),
        "channel": build["channel"],
        "sha256": checksum,
        "size": download.get("size"),
        "url": download.get("url"),
        "name": download.get("name"),
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_server(
    build: Dict[str, Any],
    cache_dir: Path,
    urlopen: UrlOpen = urllib.request.urlopen,
) -> Tuple[Path, bool]:
    destination = cache_dir / (build["sha256"] + ".jar")
    cache_dir.mkdir(parents=True, exist_ok=True)
    if destination.is_file():
        if sha256_file(destination) != build["sha256"]:
            raise HarnessError("cached server JAR checksum does not match its name")
        return destination, True

    url = build.get("url")
    if not isinstance(url, str) or not url.startswith("https://"):
        raise HarnessError("official download URL is missing or not HTTPS")
    partial = destination.with_suffix(".jar.part")
    digest = hashlib.sha256()
    written = 0
    try:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urlopen(request, timeout=120) as response:
            status = getattr(response, "status", None)
            if status is None:
                status = response.getcode()
            if status != 200:
                raise HarnessError("download returned HTTP {}".format(status))
            with partial.open("wb") as handle:
                for chunk in iter(lambda: response.read(1024 * 1024), b""):
                    handle.write(chunk)
                    digest.update(chunk)
                    written += len(chunk)
        if written != build.get("size"):
            raise HarnessError(
                "downloaded size {} does not match API size {}".format(
                    written, build.get("size")
                )
            )
        if digest.hexdigest() != build["sha256"]:
            raise HarnessError("downloaded SHA-256 does not match the API checksum")
        os.replace(str(partial), str(destination))
    finally:
        if partial.exists():
            partial.unlink()
    return destination, False


def inspect_compatibility_jars(deploy_jar: Path, conflict_jar: Path) -> Dict[str, Any]:
    """Compare shaded class namespaces without executing either plugin JAR."""
    for label, path in (("DeployMC plugin JAR", deploy_jar), ("conflicting plugin JAR", conflict_jar)):
        if not path.is_file():
            raise HarnessError("{} is missing: {}".format(label, path))
    with zipfile.ZipFile(str(deploy_jar)) as deploy, zipfile.ZipFile(str(conflict_jar)) as conflict:
        deploy_names = set(deploy.namelist())
        conflict_names = set(conflict.namelist())
    relocated = {
        name
        for name in deploy_names
        if name.startswith("io/github/woxlo1/deploymc/lib/netty/") and name.endswith(".class")
    }
    unrelocated = {
        name for name in deploy_names if name.startswith("io/netty/") and name.endswith(".class")
    }
    conflict_netty = {
        name for name in conflict_names if name.startswith("io/netty/") and name.endswith(".class")
    }
    overlap = relocated & conflict_netty
    return {
        "conflictingNettyBundled": bool(conflict_netty),
        "relocatedNettyBundled": bool(relocated),
        "deployUnrelocatedNettyAbsent": not unrelocated,
        "noNettyClassOverlap": not overlap,
        "relocatedNettyClassCount": len(relocated),
        "conflictingNettyClassCount": len(conflict_netty),
    }


def provision_token(token_dir: Path, random_bytes: Callable[[int], bytes] = secrets.token_bytes) -> str:
    secret = "dmc_" + base64.urlsafe_b64encode(random_bytes(32)).decode("ascii").rstrip("=")
    record = {
        "id": "compat-harness",
        "label": "ci-smoke",
        "secretHash": hashlib.sha256(secret.encode("utf-8")).hexdigest(),
    }
    token_dir.mkdir(parents=True, exist_ok=True)
    target = token_dir / (record["id"] + ".json")
    target.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    if os.name != "nt":
        os.chmod(str(target), 0o600)
    return secret


def write_config(template: Path, target: Path, port: int) -> None:
    original = template.read_text(encoding="utf-8")
    replaced, count = re.subn(
        r"(?m)^(\s*port:\s*)\d+(\s*(?:#.*)?)$",
        lambda match: match.group(1) + str(port) + match.group(2),
        original,
    )
    if count != 1:
        raise HarnessError("config template must contain exactly one bind port")
    target.write_text(replaced, encoding="utf-8")


def write_server_properties(target: Path, port: int) -> None:
    lines = [
        "# Generated by the DeployMC compatibility harness in an isolated test sandbox.",
        "level-type=flat",
        "level-name=compat",
        "online-mode=false",
        "enable-query=false",
        "enable-rcon=false",
        "spawn-protection=0",
        "max-tick-time=-1",
        "server-port={}".format(port),
    ]
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")


def prepare_sandbox(
    work_dir: Path,
    server_jar: Path,
    plugin_jar: Path,
    conflict_jar: Path,
    template: Path,
    api_port: int,
    minecraft_port: int,
) -> Dict[str, Any]:
    if api_port == minecraft_port:
        raise HarnessError("the Minecraft and DeployMC API ports must be distinct")
    if not conflict_jar.is_file():
        raise HarnessError("conflicting plugin JAR is missing: " + str(conflict_jar))
    work_dir.mkdir(parents=True, exist_ok=True)
    root = Path(tempfile.mkdtemp(prefix="run-", dir=str(work_dir)))
    plugins = root / "plugins"
    token_dir = plugins / "DeployMC" / "tokens.d"
    logs = root / "logs"
    for directory in (plugins, token_dir, logs):
        directory.mkdir(parents=True, exist_ok=True)

    shutil.copyfile(str(server_jar), str(root / "server.jar"))
    shutil.copyfile(str(plugin_jar), str(plugins / "DeployMC.jar"))
    shutil.copyfile(str(conflict_jar), str(plugins / "LegacyNettyFixture.jar"))
    write_config(template, plugins / "DeployMC" / "config.yml", api_port)
    write_server_properties(root / "server.properties", minecraft_port)
    (root / "eula.txt").write_text(
        "# CI test sandbox only; this is not a production recommendation.\neula=true\n",
        encoding="utf-8",
    )
    secret = provision_token(token_dir)
    return {
        "root": root,
        "logs": logs,
        "token": secret,
        "tokenDir": token_dir,
        "apiPort": api_port,
        "minecraftPort": minecraft_port,
        "serverLog": logs / "server.log",
        "threadDumpBefore": logs / "thread-dump-before-disable.txt",
        "threadDumpAfter": logs / "thread-dump-after-disable.txt",
    }


def find_free_port(exclude: Tuple[int, ...] = ()) -> int:
    while True:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as candidate:
            candidate.bind(("127.0.0.1", 0))
            port = int(candidate.getsockname()[1])
        if port not in exclude:
            return port


def find_free_ports() -> Tuple[int, int]:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as api:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as minecraft:
            api.bind(("127.0.0.1", 0))
            minecraft.bind(("127.0.0.1", 0))
            return int(api.getsockname()[1]), int(minecraft.getsockname()[1])


def java_runtime(java: str) -> Dict[str, str]:
    first_line = "unknown"
    try:
        completed = subprocess.run(
            [java, "-version"], capture_output=True, text=True, timeout=20, check=False
        )
        for line in (completed.stderr + "\n" + completed.stdout).splitlines():
            if line.strip():
                first_line = line.strip()
                break
    except (OSError, subprocess.SubprocessError):
        pass
    return {"version": first_line, "vendor": first_line.split()[0] if first_line else "unknown"}


def start_server(java: str, sandbox: Dict[str, Any]) -> Tuple[subprocess.Popen, Any]:
    log = sandbox["serverLog"]
    handle = log.open("wb")
    flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0
    process = subprocess.Popen(
        [java, "-jar", "server.jar", "nogui"],
        cwd=str(sandbox["root"]),
        stdin=subprocess.PIPE,
        stdout=handle,
        stderr=subprocess.STDOUT,
        creationflags=flags,
    )
    handle.close()
    return process, process.stdin


def wait_for_done(
    log_path: Path, process: Any, timeout_seconds: int, poll_seconds: float = 0.1
) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise HarnessError(
                "server exited before Done with code {}".format(process.returncode)
            )
        if log_path.is_file():
            text = log_path.read_text(encoding="utf-8", errors="replace")
            if DONE_SIGNAL.search(text):
                return
        time.sleep(poll_seconds)
    raise HarnessError("server did not report Done within {} seconds".format(timeout_seconds))


def wait_for_log(
    log_path: Path,
    process: Any,
    pattern: str,
    timeout_seconds: int,
    poll_seconds: float = 0.1,
) -> None:
    expected = re.compile(pattern, re.IGNORECASE)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise HarnessError(
                "server exited before log pattern {!r} with code {}".format(
                    pattern, process.returncode
                )
            )
        if log_path.is_file() and expected.search(
            log_path.read_text(encoding="utf-8", errors="replace")
        ):
            return
        time.sleep(poll_seconds)
    raise HarnessError("log pattern {!r} was not observed in time".format(pattern))


def send_console_command(stdin: Any, command: str) -> None:
    if stdin is None:
        raise HarnessError("the server console is not available")
    try:
        stdin.write((command + "\n").encode("utf-8"))
        stdin.flush()
    except (BrokenPipeError, OSError) as error:
        raise HarnessError("could not send console command {!r}: {}".format(command, error))


def http_request(
    url: str,
    token: Optional[str] = None,
    urlopen: UrlOpen = urllib.request.urlopen,
    timeout: int = 10,
) -> Tuple[int, Dict[str, str], bytes]:
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urlopen(request, timeout=timeout) as response:
            status = getattr(response, "status", None)
            if status is None:
                status = response.getcode()
            return int(status), dict(response.headers.items()), response.read()
    except urllib.error.HTTPError as error:
        return int(error.code), dict(error.headers.items()), error.read()


def wait_for_health(
    base_url: str,
    timeout_seconds: int,
    urlopen: UrlOpen = urllib.request.urlopen,
    poll_seconds: float = 0.25,
) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_status = "no response"
    while time.monotonic() < deadline:
        try:
            status, _, body = http_request(base_url + "/healthz", urlopen=urlopen)
            last_status = str(status)
            if status == 200 and json.loads(body.decode("utf-8")).get("status") == "ok":
                return
        except Exception as error:  # noqa: BLE001 - readiness polling is deliberately broad
            last_status = str(error)
        time.sleep(poll_seconds)
    raise HarnessError("health probe did not become ready: " + last_status)


def validate_server_info(body: bytes) -> Dict[str, Any]:
    info = json.loads(body.decode("utf-8"))
    required = ("deployMcVersion", "apiVersion", "platform", "capabilities")
    for key in required:
        if key not in info:
            raise HarnessError("server info is missing " + key)
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", info["deployMcVersion"]):
        raise HarnessError("server info has an invalid deployMcVersion")
    if info["apiVersion"] != "v1":
        raise HarnessError("server info has an unexpected apiVersion")
    platform_info = info["platform"]
    for key in ("softwareName", "softwareVersion", "minecraftVersion", "javaVersion", "operatingSystem"):
        if not isinstance(platform_info.get(key), str) or not platform_info[key]:
            raise HarnessError("platform info is missing " + key)
    capabilities = info["capabilities"]
    if not isinstance(capabilities, dict) or not capabilities:
        raise HarnessError("capabilities must be a non-empty object")
    allowed = {"AVAILABLE", "UNAVAILABLE", "UNKNOWN"}
    for key, state in capabilities.items():
        if not isinstance(key, str) or state not in allowed:
            raise HarnessError("capability {} has an invalid state".format(key))
    return {
        "deployMcVersion": info["deployMcVersion"],
        "apiVersion": info["apiVersion"],
        "softwareName": platform_info["softwareName"],
        "softwareVersion": platform_info["softwareVersion"],
        "minecraftVersion": platform_info["minecraftVersion"],
        "javaVersion": platform_info["javaVersion"],
        "operatingSystem": platform_info["operatingSystem"],
        "capabilityCount": len(capabilities),
    }


def smoke_test(
    base_url: str, token: str, urlopen: UrlOpen = urllib.request.urlopen
) -> Dict[str, Any]:
    status, _, _ = http_request(base_url + "/api/v1/server", urlopen=urlopen)
    if status != 401:
        raise HarnessError("unauthenticated /api/v1/server did not return 401")

    status, headers, body = http_request(
        base_url + "/api/v1/server", token=token, urlopen=urlopen
    )
    if status != 200:
        raise HarnessError("authenticated /api/v1/server returned HTTP {}".format(status))
    header_map = {key.lower(): value for key, value in headers.items()}
    if not header_map.get("etag"):
        raise HarnessError("server info response has no ETag")
    if not header_map.get("x-request-id"):
        raise HarnessError("server info response has no X-Request-Id")
    return validate_server_info(body)


def check_server_log(log_path: Path, token: str, port: int) -> Dict[str, bool]:
    text = log_path.read_text(encoding="utf-8", errors="replace")
    return {
        "pluginLoaded": bool(re.search(r"DeployMC\s+\d+\.\d+\.\d+", text)),
        "conflictingPluginLoaded": bool(
            re.search(r"Enabling\s+" + re.escape(CONFLICT_PLUGIN_NAME) + r"\b", text)
        ),
        "httpListenerStarted": "Embedded transport listening on 127.0.0.1:{}".format(port) in text,
        "adapterTasksReleased": ADAPTER_RELEASED_LOG in text,
        "startupBannerBind": "Bind: 127.0.0.1:{}".format(port) in text,
        "startupBannerAuth": "Auth mode: token" in text,
        "startupBannerWarning": "No external restart script" in text,
        "noTokenInLog": token not in text,
        "noDeployMcLoaderError": not bool(
            re.search(r"(?im)^.*(?:error|exception).*DeployMC.*$", text)
        ),
        "noConflictLoaderErrors": not bool(
            re.search(
                r"(?im)^.*(?:error|exception|classloader|ClassNotFound|NoClassDefFound|LinkageError).*"
                r"(?:" + re.escape(CONFLICT_PLUGIN_NAME) + r"|io\.netty|DeployMC).*$",
                text,
            )
        ),
    }


def jcmd_path(java: str) -> Optional[str]:
    name = "jcmd.exe" if os.name == "nt" else "jcmd"
    adjacent = Path(java).with_name(name)
    if adjacent.is_file():
        return str(adjacent)
    return shutil.which("jcmd")


def capture_thread_dump(jcmd: Optional[str], pid: int, target: Path) -> bool:
    if not jcmd:
        return False
    try:
        with target.open("wb") as handle:
            subprocess.run(
                [jcmd, str(pid), "Thread.print", "-l"],
                stdout=handle,
                stderr=subprocess.DEVNULL,
                timeout=30,
                check=False,
            )
        return target.stat().st_size > 0
    except (OSError, subprocess.SubprocessError):
        return False


def deployed_netty_threads(dump_path: Path) -> List[str]:
    if not dump_path.is_file():
        return []
    threads: List[str] = []
    for line in dump_path.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith('"' + NETTY_THREAD_PREFIX):
            closing = line.find('"', 1)
            if closing > 0:
                threads.append(line[1:closing])
    return threads


def stop_server(process: Any, stdin: Any, timeout_seconds: int) -> bool:
    if process.poll() is None and stdin is not None:
        try:
            stdin.write("stop\n".encode("ascii"))
            stdin.flush()
            stdin.close()
        except (BrokenPipeError, OSError):
            pass
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            return process.returncode == 0
        time.sleep(0.2)
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    return False


def run_entry(args: argparse.Namespace) -> int:
    entry = get_entry(args.entry, args.entries)
    build = resolve_build(entry)
    server_jar, cached = download_server(build, args.cache_dir)
    if args.port is not None:
        api_port = args.port
        minecraft_port = find_free_port(exclude=(api_port,))
    else:
        api_port, minecraft_port = find_free_ports()
    sandbox = prepare_sandbox(
        args.work_dir,
        server_jar,
        args.plugin_jar,
        args.conflict_plugin_jar,
        args.template,
        api_port,
        minecraft_port,
    )
    java_info = java_runtime(args.java)
    checks: Dict[str, Any] = {
        "checksumVerified": True,
        "serverDownloadedFromOfficialApi": True,
        "serverCacheUsed": cached,
        "eulaAcceptedOnlyInSandbox": True,
        "tokenProvisionedFromHostFilesystem": True,
        "distinctPorts": api_port != minecraft_port,
        "conflictingPluginInstalled": True,
    }
    result: Dict[str, Any] = {
        "suite": "compat-v1",
        "entry": args.entry,
        "status": "fail",
        "port": api_port,
        "minecraftPort": minecraft_port,
        "server": {
            "project": entry["project"],
            "minecraftVersion": entry["minecraftVersion"],
            "build": build["id"],
            "channel": build["channel"],
            "sha256": build["sha256"],
            "url": build["url"],
            "cacheUsed": cached,
        },
        "runtime": {
            "javaVendor": java_info["vendor"],
            "javaVersion": java_info["version"],
            "os": platform.platform(),
            "pythonVersion": platform.python_version(),
            "deployMcCommit": os.environ.get("GITHUB_SHA", "local"),
            "runUrl": run_url(),
        },
        "checks": checks,
        "artifacts": {
            "serverLog": str(sandbox["serverLog"]),
            "threadDumpBefore": str(sandbox["threadDumpBefore"]),
            "threadDumpAfter": str(sandbox["threadDumpAfter"]),
            "conflictingPluginJar": str(args.conflict_plugin_jar),
            "sandbox": str(sandbox["root"]),
        },
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    process = None
    stdin = None
    try:
        jar_evidence = inspect_compatibility_jars(
            args.plugin_jar, args.conflict_plugin_jar
        )
        checks.update({key: jar_evidence[key] for key in REQUIRED_CHECKS if key in jar_evidence})
        result["conflictingPlugin"] = {
            "name": CONFLICT_PLUGIN_NAME,
            "version": CONFLICT_PLUGIN_VERSION,
            "sha256": sha256_file(args.conflict_plugin_jar),
            "bundledNettyClassCount": jar_evidence["conflictingNettyClassCount"],
            "relocatedNettyClassCount": jar_evidence["relocatedNettyClassCount"],
            "disableCommand": DISABLE_COMMAND,
        }
        process, stdin = start_server(args.java, sandbox)
        wait_for_done(sandbox["serverLog"], process, args.timeout)
        wait_for_health("http://127.0.0.1:{}".format(api_port), 60)
        checks["doneSignalObserved"] = True
        checks["healthProbePassed"] = True
        result["serverInfo"] = smoke_test(
            "http://127.0.0.1:{}".format(api_port), sandbox["token"]
        )
        checks["unauthenticatedRequestRejected"] = True
        checks["authenticatedServerInfoPassed"] = True

        jcmd = jcmd_path(args.java)
        before_dumped = capture_thread_dump(jcmd, process.pid, sandbox["threadDumpBefore"])
        checks["threadDumpBeforeCaptured"] = before_dumped
        threads_before = deployed_netty_threads(sandbox["threadDumpBefore"])
        result["deployNettyThreadsBeforeDisable"] = threads_before
        checks["deployNettyThreadsBeforeDisable"] = bool(threads_before)

        send_console_command(stdin, DISABLE_COMMAND)
        wait_for_log(
            sandbox["serverLog"],
            process,
            re.escape(ADAPTER_RELEASED_LOG),
            30,
        )
        after_dumped = capture_thread_dump(jcmd, process.pid, sandbox["threadDumpAfter"])
        checks["threadDumpAfterCaptured"] = after_dumped
        checks["threadDumpCaptured"] = before_dumped and after_dumped
        threads_after = deployed_netty_threads(sandbox["threadDumpAfter"])
        result["deployNettyThreadsAfterDisable"] = threads_after
        checks["deployNettyThreadsGoneAfterDisable"] = bool(threads_before) and not bool(
            set(threads_before).intersection(threads_after)
        )

        checks.update(check_server_log(sandbox["serverLog"], sandbox["token"], api_port))
        result["status"] = (
            "pass"
            if all(
                checks.get(name) is True
                for name in REQUIRED_CHECKS
                if name != "cleanShutdown"
            )
            else "fail"
        )
    except Exception as error:  # noqa: BLE001 - every failure is recorded and fails the run
        result["error"] = str(error)
    finally:
        if process is not None:
            checks["cleanShutdown"] = stop_server(process, stdin, args.shutdown_timeout)
            result["status"] = (
                "pass"
                if result["status"] == "pass" and checks["cleanShutdown"] is True
                else "fail"
            )
        result["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        args.result.parent.mkdir(parents=True, exist_ok=True)
        args.result.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(
        "compat entry {}: {} (result {})".format(args.entry, result["status"], args.result)
    )
    return 0 if result["status"] == "pass" else 1


def run_url() -> Optional[str]:
    server = os.environ.get("GITHUB_SERVER_URL")
    repository = os.environ.get("GITHUB_REPOSITORY")
    run_id = os.environ.get("GITHUB_RUN_ID")
    if server and repository and run_id:
        return "{}/{}/actions/runs/{}".format(server, repository, run_id)
    return None


def render_smoke_section(results: list) -> str:
    lines = [
        BEGIN_MARKER,
        "### Generated compatibility smoke results (not a support tier)",
        "",
        "These rows record only the v1 smoke suite: concurrent load with an older Netty "
        "fixture, authenticated `/api/v1/server`, before/after-disable thread dumps, logs, "
        "and clean shutdown. They do not earn Tier 1 or Tier 2.",
        "",
        "| Suite | Entry | Minecraft | Build | OS | DeployMC commit | Result | Run | Date |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for result in results:
        server = result.get("server", {})
        runtime = result.get("runtime", {})
        run = runtime.get("runUrl") or "local"
        date = result.get("finishedAt", "unknown")
        lines.append(
            "| {suite} | {entry} | {version} | {build} | {os} | {commit} | {status} | "
            "[run]({run}) | {date} |".format(
                suite=result.get("suite", "compat-v1"),
                entry=result.get("entry", "unknown"),
                version=server.get("minecraftVersion", "unknown"),
                build=server.get("build", "unknown"),
                os=runtime.get("os", "unknown"),
                commit=runtime.get("deployMcCommit", "unknown"),
                status=result.get("status", "fail"),
                run=run,
                date=date,
            )
        )
    lines.append(END_MARKER)
    return "\n".join(lines) + "\n"


def render_matrix(args: argparse.Namespace) -> int:
    results = []
    for path in args.results:
        results.append(json.loads(path.read_text(encoding="utf-8")))
    section = render_smoke_section(results)
    text = args.matrix.read_text(encoding="utf-8")
    if BEGIN_MARKER not in text or END_MARKER not in text:
        raise HarnessError("support matrix is missing generated-section markers")
    start = text.index(BEGIN_MARKER)
    end = text.index(END_MARKER, start) + len(END_MARKER)
    updated = text[:start] + section.rstrip("\n") + text[end:]
    if args.check and updated != text:
        print("generated support-matrix section is out of date")
        return 1
    if not args.check:
        args.matrix.write_text(updated, encoding="utf-8")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    key = commands.add_parser("cache-key", help="print the cache key for an entry")
    key.add_argument("--entry", required=True)
    key.add_argument("--entries", type=Path, default=DEFAULT_ENTRIES)

    run = commands.add_parser("run", help="run one compatibility entry")
    run.add_argument("--entry", default="paper-26.3")
    run.add_argument("--entries", type=Path, default=DEFAULT_ENTRIES)
    run.add_argument("--plugin-jar", type=Path, required=True)
    run.add_argument(
        "--conflict-plugin-jar",
        type=Path,
        default=DEFAULT_CONFLICT_JAR,
        help="fixture JAR that bundles unrelocated Netty",
    )
    run.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    run.add_argument("--work-dir", type=Path, default=DEFAULT_WORK)
    run.add_argument("--result", type=Path, default=HERE / "results" / "compat-v1.json")
    run.add_argument("--template", type=Path, default=DEFAULT_CONFIG_TEMPLATE)
    run.add_argument("--java", default="java")
    run.add_argument("--port", type=int, help="DeployMC API port; Minecraft gets a distinct port")
    run.add_argument("--timeout", type=int, default=300)
    run.add_argument("--shutdown-timeout", type=int, default=30)

    render = commands.add_parser("render", help="render the generated smoke matrix section")
    render.add_argument("results", type=Path, nargs="+")
    render.add_argument("--matrix", type=Path, default=REPO_ROOT / "docs" / "compatibility" / "support-matrix.md")
    render.add_argument("--check", action="store_true")
    return parser


def main(argv: Optional[list] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "cache-key":
            print(cache_key(get_entry(args.entry, args.entries)))
            return 0
        if args.command == "run":
            return run_entry(args)
        if args.command == "render":
            return render_matrix(args)
    except HarnessError as error:
        print("harness error: {}".format(error), file=sys.stderr)
        return 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
