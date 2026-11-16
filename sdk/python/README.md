# DeployMC Python SDK

Python client SDK for DeployMC. This is a Phase 0 skeleton: it validates and normalizes client
configuration. It does not perform HTTP requests yet.

## Commands

Run from `sdk/python/`:

```powershell
python -m venv .venv
.venv\Scripts\python -m pip install -e ".[dev]"
.venv\Scripts\python -m ruff check .
.venv\Scripts\python -m ruff format --check .
.venv\Scripts\python -m mypy
.venv\Scripts\python -m pytest
```

## Security defaults

- Remote endpoints must use HTTPS. Plain HTTP is accepted only for loopback addresses.
- Credentials embedded in the base URL are rejected; pass a token separately.
- Tokens are excluded from the dataclass representation.
