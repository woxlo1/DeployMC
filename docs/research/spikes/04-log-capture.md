# Spike 4: Log capture across oldest and newest targets

Status: `LOCAL-ONLY` for deterministic corpora; `NOT RUN` on real servers.

## Question

Which log capture mechanism works across the oldest and newest targeted server versions, and how
should attribution, level, timestamps, and multiline stack traces be handled?

## Current repository evidence

The SPI records the gap in [platform-spi.md](../../architecture/platform-spi.md): `ConsoleLine` carries
level, logger, thread, and message, but there is no contract for attaching a Log4j appender versus a
`java.util.logging` handler. The Bukkit adapter does not capture console output.

The deterministic corpus from PART 35 was exercised on 2026-11-23:

```powershell
python -m unittest discover -s tests/fixtures/log-corpora -p "test_*.py"
```

Result: `Ran 6 tests`, `OK` in 0.069s. The corpus checks parser behavior against fixed inputs; it does
not attach a handler to a running server.

## Design constraints from this spike

- Attach and detach handlers only through the platform adapter so disable cannot leak a logger.
- Preserve source level, logger name, thread name, timestamp, and multiline stack trace grouping.
- Treat unknown formats as raw lines with a documented attribution marker, not as structured data.
- Apply the console ring-buffer and WebSocket drop policies in
  [threading-model.md](../../architecture/threading-model.md) before any log reaches a client.
- Never assume that command output and server logs share one stream; command correlation is a
  separate SPI gap.
- No user-facing log string may be hard-coded in an adapter.

## Result

The spike confirms that log capture is a platform-specific capability and that the product has no
implemented cross-version console consumer yet. Keep `log.capture` and `log.capture.structured` as
capability values, not advertised features.

## Not verified

No oldest or newest target version was selected because D-001 is open. No Paper, Spigot, Folia,
Velocity, or BungeeCord process was started with a DeployMC log handler, and no real multiline stack
trace was captured. No support tier or cross-version parity claim is made.
