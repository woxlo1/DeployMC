# Spike 7: Restart behavior with common supervisors

Status: `NOT RUN`. No supervisor-specific result is claimed.

## Question

How does DeployMC behave under systemd, Docker, a loop script, and Pterodactyl when it requests a
server restart or when the supervisor restarts the process externally?

## Blocker

Owner decision D-001 asks for the real hosting setup and Minecraft versions in use. It is still open
in [decisions-pending.md](../../development/decisions-pending.md). Without that answer there is no
reference environment, and no local supervisor experiment can represent the owner's deployment.

The SPI already records the design gap in [platform-spi.md](../../architecture/platform-spi.md):
`ServerController` exposes shutdown and `restartSupported`, not a restart method, and the restart
modes (`shutdown-only`, `spigot-restart-script`, `none`) need a precise contract.

## Test plan, not evidence

Each environment needs the following recorded before a capability is advertised:

| Supervisor | Required evidence |
|---|---|
| systemd | Unit file, `Restart=`, stop/start result, exit code, log continuity, data lock release |
| Docker | Image and compose file, restart policy, volume state, signal propagation, data lock release |
| Loop script | Script source, exit code handling, restart delay, duplicate-process check, data lock release |
| Pterodactyl | Panel/Wings version, restart action, allocation behavior, log visibility, data lock release |

For every environment, record DeployMC commit, Java version, OS, server version, restart mode,
whether the data lock was released, whether a snapshot was restored, and the exact command or panel
action used. A restart that leaks the `data.lock` or starts a second instance is a failure.

## Result

No restart mode is selected by this spike. The default product behavior remains shutdown-only until
a real supervisor run justifies another mode. ADR-0014 is unchanged, and no support tier is earned.
