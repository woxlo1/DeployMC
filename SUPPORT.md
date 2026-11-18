# Support

DeployMC is an unfunded **pre-alpha** project maintained by one person. Support is best effort, and
there is no service-level agreement. Please read the status notes in [README.md](README.md) before
asking for help: most user-facing features are not implemented yet.

## Where to ask

| Need | Where |
|---|---|
| Reproducible bug | Open a GitHub issue in <https://github.com/woxlo1/DeployMC/issues> |
| Question, idea, or setup discussion | GitHub Discussions in <https://github.com/woxlo1/DeployMC/discussions> (enabled, currently no posts) |
| Translation help or review | Translation issue form, or Discussions; read [TRANSLATING.md](TRANSLATING.md) first |
| Security vulnerability | **Private report only**, per [SECURITY.md](SECURITY.md) |

Dedicated issue forms are available. Choose the closest one:

| Need | Form |
|---|---|
| Reproducible incorrect behavior | Bug report |
| New capability or changed behavior | Feature request |
| Real result on a server platform/version | Compatibility report |
| Wrong, missing, or stale documentation | Documentation issue |
| Translation or review | Translation help or review |

Plain issues remain enabled if no form fits; include the information below. Security details must
never go into a public form. See [community configuration](docs/development/community.md) for the
full list of templates and settings.

## What to include

- DeployMC version or commit, and how you obtained it.
- Server platform and version (for example Paper 1.21.x), Java vendor and version, operating system.
- Installation type (plugin on a server, standalone, container, panel).
- What you expected and what happened, with exact reproduction steps.
- Relevant logs, configuration, and error messages with tokens, hostnames, and personal data removed.
- Whether files, snapshots, deployment, console, authentication, the Web UI, the CLI, or an SDK is
  affected.

Never paste a real token, session cookie, Cloudflare credentials, or a full hostname. Redact first.

## Response targets

These targets are aspirational and may slip during busy periods:

| Request | Target |
|---|---|
| Acknowledgement of a new issue or discussion | 7 days |
| Initial triage (labels, milestone, next step) | 14 days |
| Security reports | See [SECURITY.md](SECURITY.md) |

No response target is a promise of a fix. Feature requests may be declined or deferred to keep the
project honest about scope.

## Supported versions

There is no supported production release yet. Only the current `main` branch receives fixes. See the
supported-versions table in [SECURITY.md](SECURITY.md).

## Languages

The maintainer communicates in Japanese and English. Repository content is in English; the Japanese
README and catalogs are maintained translations. Questions in either language are welcome. Answer
times in other languages depend on available volunteers.

## What is not offered

- Paid support, priority support, or consulting.
- Help with servers you do not administer or have permission to change.
- Recovery of a host compromised outside DeployMC's scope.
- Support for modified builds or unreviewed forks presented as DeployMC.

## Before filing

1. Search existing issues and discussions.
2. Reproduce on the latest `main` if you can.
3. Remove secrets from logs.
4. Check whether the behavior is already listed as unimplemented in the README.
