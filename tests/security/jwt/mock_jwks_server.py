#!/usr/bin/env python3
"""Localhost-only mock JWKS server for JWT fixture consumers.

The server binds to an OS-assigned port on 127.0.0.1, never a fixed port, and serves
only the generated public JWKS documents. Rotation and unavailability are explicit
so tests can exercise cache, retry, and fail-closed paths deterministically.
"""

from __future__ import annotations

import hashlib
import http.client
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

JWKS_PATHS = ("/cdn-cgi/access/certs", "/jwks")


class JwksServer:
    """Context-managed mock server with dynamic port and controlled state changes."""

    def __init__(self, initial: dict[str, Any], rotated: dict[str, Any] | None = None) -> None:
        self._documents = {"initial": initial, "rotated": rotated or initial}
        self._state = "initial"
        self._unavailable = False
        self._generation = 0
        self._lock = threading.Lock()
        self._thread: threading.Thread | None = None
        self._httpd = ThreadingHTTPServer(("127.0.0.1", 0), self._handler_class())

    @classmethod
    def from_directory(cls, directory: Path) -> "JwksServer":
        initial = json.loads((directory / "jwks-initial.json").read_text(encoding="ascii"))
        rotated = json.loads((directory / "jwks-rotated.json").read_text(encoding="ascii"))
        return cls(initial, rotated)

    @property
    def port(self) -> int:
        return self._httpd.server_address[1]

    @property
    def url(self) -> str:
        return "http://127.0.0.1:{0}/cdn-cgi/access/certs".format(self.port)

    def rotate(self) -> None:
        with self._lock:
            self._state = "rotated"
            self._generation += 1

    def reset(self) -> None:
        with self._lock:
            self._state = "initial"
            self._generation += 1

    def set_unavailable(self, unavailable: bool) -> None:
        with self._lock:
            self._unavailable = unavailable

    def state(self) -> dict[str, Any]:
        with self._lock:
            return {"generation": self._generation, "state": self._state,
                    "unavailable": self._unavailable}

    def _handler_class(self) -> type[BaseHTTPRequestHandler]:
        outer = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def do_GET(self) -> None:  # noqa: N802 - required by BaseHTTPRequestHandler
                path = urlsplit(self.path).path
                if path == "/state":
                    outer._send_json(self, outer.state(), 200)
                    return
                if path not in JWKS_PATHS:
                    outer._send_json(self, {"error": "not_found"}, 404)
                    return
                with outer._lock:
                    document = outer._documents[outer._state]
                    unavailable = outer._unavailable
                if unavailable:
                    outer._send_json(
                        self,
                        {"error": "jwks_unavailable"},
                        503,
                        {"Retry-After": "1"},
                    )
                    return
                payload = json.dumps(
                    document, sort_keys=True, separators=(",", ":"), ensure_ascii=False
                ).encode("utf-8")
                digest = hashlib.sha256(payload).hexdigest()
                outer._send_json_bytes(
                    self,
                    payload,
                    200,
                    {"Cache-Control": "public, max-age=3600", "ETag": '"{0}"'.format(digest)},
                )

            def log_message(self, format: str, *args: Any) -> None:
                return

        return Handler

    def _send_json(
        self,
        handler: BaseHTTPRequestHandler,
        document: dict[str, Any],
        status: int,
        extra: dict[str, str] | None = None,
    ) -> None:
        payload = json.dumps(
            document, sort_keys=True, separators=(",", ":"), ensure_ascii=False
        ).encode("utf-8")
        self._send_json_bytes(handler, payload, status, extra)

    def _send_json_bytes(
        self,
        handler: BaseHTTPRequestHandler,
        payload: bytes,
        status: int,
        extra: dict[str, str] | None = None,
    ) -> None:
        handler.send_response(status)
        handler.send_header("Content-Type", "application/json; charset=utf-8")
        handler.send_header("Content-Length", str(len(payload)))
        for name, value in (extra or {}).items():
            handler.send_header(name, value)
        handler.end_headers()
        handler.wfile.write(payload)

    def __enter__(self) -> "JwksServer":
        self._thread = threading.Thread(
            target=self._httpd.serve_forever,
            kwargs={"poll_interval": 0.05},
            name="dmctest-mock-jwks",
            daemon=True,
        )
        self._thread.start()
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        self._httpd.shutdown()
        self._httpd.server_close()
        if self._thread is not None:
            self._thread.join(timeout=5.0)


def fetch(path: str, port: int) -> tuple[int, dict[str, Any], dict[str, str]]:
    """Fetch a JSON response from this process's localhost mock server."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5.0)
    try:
        connection.request("GET", path)
        response = connection.getresponse()
        payload = response.read()
        return response.status, json.loads(payload), dict(response.getheaders())
    finally:
        connection.close()


def main() -> None:
    server = JwksServer.from_directory(Path(__file__).parent)
    with server:
        print(server.url, flush=True)
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
