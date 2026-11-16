from __future__ import annotations

import pytest

from deploymc_sdk import ClientConfig, ConfigError


def test_accepts_https_endpoint() -> None:
    config = ClientConfig(base_url="https://deploymc.example.com", token="example-token")

    assert config.base_url == "https://deploymc.example.com"
    assert not config.is_loopback


def test_accepts_plain_http_for_loopback_only() -> None:
    assert ClientConfig(base_url="http://127.0.0.1:8080").is_loopback
    assert ClientConfig(base_url="http://[::1]:8080").is_loopback
    assert ClientConfig(base_url="http://localhost:8080").is_loopback


@pytest.mark.parametrize(
    "base_url",
    [
        "http://deploymc.example.com",
        "ftp://deploymc.example.com",
        "deploymc.example.com",
        "https://",
        "https://deploymc.example.com/path?query=1",
        "https://user:secret@deploymc.example.com",
    ],
)
def test_rejects_invalid_or_unsafe_endpoints(base_url: str) -> None:
    with pytest.raises(ConfigError):
        ClientConfig(base_url=base_url)


@pytest.mark.parametrize("timeout", [0, -0.1, float("inf")])
def test_rejects_non_positive_or_infinite_timeouts(timeout: float) -> None:
    with pytest.raises(ConfigError):
        ClientConfig(base_url="https://deploymc.example.com", timeout_seconds=timeout)


def test_rejects_blank_token() -> None:
    with pytest.raises(ConfigError):
        ClientConfig(base_url="https://deploymc.example.com", token="   ")


def test_token_is_not_exposed_by_repr() -> None:
    config = ClientConfig(base_url="https://deploymc.example.com", token="super-secret-token")

    assert "super-secret-token" not in repr(config)
    assert config.token == "super-secret-token"
