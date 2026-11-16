"""Configuration types for the DeployMC Python SDK."""

from __future__ import annotations

import ipaddress
import math
from dataclasses import dataclass, field
from urllib.parse import urlsplit

_ALLOWED_SCHEMES = frozenset({"http", "https"})


class ConfigError(ValueError):
    """Raised when a client configuration value violates a security or shape constraint."""


@dataclass(frozen=True, slots=True)
class ClientConfig:
    """Validated connection settings for a DeployMC server.

    Remote endpoints must use HTTPS. HTTP is accepted only for loopback hosts so local development
    and the documented local test mode work without weakening deployed defaults.
    """

    base_url: str
    token: str | None = field(default=None, repr=False)
    timeout_seconds: float = 10.0
    verify_tls: bool = True

    def __post_init__(self) -> None:
        parts = urlsplit(self.base_url)

        if parts.scheme not in _ALLOWED_SCHEMES:
            raise ConfigError("base_url must use http or https")
        if not parts.hostname:
            raise ConfigError("base_url must include a host")
        if parts.username is not None or parts.password is not None:
            raise ConfigError("base_url must not contain credentials; pass token instead")
        if parts.query or parts.fragment:
            raise ConfigError("base_url must not contain a query or fragment")
        if not self.is_loopback and parts.scheme != "https":
            raise ConfigError("non-loopback base_url must use https")
        if not math.isfinite(self.timeout_seconds) or self.timeout_seconds <= 0:
            raise ConfigError("timeout_seconds must be a finite value greater than zero")
        if self.token is not None and not self.token.strip():
            raise ConfigError("token must not be blank when provided")

    @property
    def is_loopback(self) -> bool:
        """Whether the configured host resolves lexically to a loopback address."""
        host = urlsplit(self.base_url).hostname
        if host is None:
            return False
        try:
            return ipaddress.ip_address(host).is_loopback
        except ValueError:
            return host.lower() == "localhost"
