# Development Environment

- Last updated: 2026-11-16
- Repository: `https://github.com/woxlo1/DeployMC`
- Work path: `C:\Users\satos\OneDrive\ドキュメント\IdeaProjects\tororo1066\DeployMC`
- Shell: PowerShell on Windows
- Owner: `woxlo1`

## Verified tooling

The following versions were executed and verified on 2026-11-16.

| Tool | Version | Notes |
|---|---|---|
| Git | 2.45.1.windows.1 | `git version` |
| JDK (build) | 21.0.11 LTS (Eclipse Adoptium) | `JAVA_HOME` points here; used by Gradle toolchains |
| JDK (PATH) | 17.0.10 | `java`/`javac` on PATH differ from `JAVA_HOME`; always rely on Gradle toolchains |
| Node.js | 24.13.1 | |
| npm | 11.8.0 | |
| pnpm | 9.15.4 | Web UI package manager |
| Python | 3.12.10 (`python`), 3.13.0 (`py`) | Python SDK targets a documented modern version |
| Rust | 1.100.0 | GNU toolchain: `stable-x86_64-pc-windows-gnu` |
| Cargo | 1.100.0 | |
| GCC | 16.2.0 (MinGW-w64, POSIX threads, SEH) | Linker for the GNU Rust toolchain |
| GNU Make | 4.4.1 | Optional task runner |
| Docker | 28.1.1 | CLI installed; daemon was not running at inspection time |
| GitHub CLI | 2.104.0 | Installed through Scoop; authenticated as `woxlo1` with `repo` and `workflow` scopes |
| winget | 1.29.380 | Available |
| Scoop | Available | Used for user-scope tool installation |

Gradle is intentionally **not** installed globally. The repository uses the Gradle Wrapper (8.10.2,
`bin` distribution, verified on 2026-11-16). `.\gradlew.bat build` completes successfully and runs 32
Java tests with zero failures; `.\gradlew.bat verifyJava8` confirms production classes target Java 8
bytecode.

Rust is installed through Scoop (`rustup-gnu`), with `CARGO_HOME` and `RUSTUP_HOME` under
`%USERPROFILE%\scoop\persist\rustup-gnu`. Shells started before the installation may not see `cargo`
on `PATH`; open a new terminal, or set both variables and use the absolute `cargo.exe` path.

## Missing or unavailable at inspection

| Tool | Impact | Handling |
|---|---|---|
| Rust MSVC toolchain / Visual Studio Build Tools | Not present | GNU toolchain with MinGW is used locally; CI uses stable MSVC on Windows and stable GNU/Linux elsewhere |
| Docker daemon | Not running | Required only for container-based compatibility work; start Docker Desktop when needed |
| `just` | Not installed | Not required; documented commands are used instead |

## Owner decisions recorded

| Decision | Value | Date |
|---|---|---|
| License | Apache License 2.0 | 2026-11-16 |
| Java package / Maven group | `io.github.woxlo1.deploymc` | 2026-11-16 |
| Work path | Keep the current OneDrive path | 2026-11-16 |
| AI co-author trailer | Use `Co-authored-by: opencode <221189863+opencode-agent@users.noreply.github.com>` when OpenCode contributes | 2026-11-16 |
| Development tracking | Use GitHub Issues, milestones, and labels | 2026-11-16 |
| Initial languages | English canonical, Japanese first translation | 2026-11-16 |

## Local constraints and risks

1. **OneDrive synchronization.** The repository is inside a OneDrive-synced directory. OneDrive can
   transiently lock files, which may interfere with Gradle builds and Windows JAR-replacement tests.
   The owner chose to keep this path; if file-lock failures appear, moving to a non-synced directory
   (for example `C:\dev\DeployMC`) is the first remedy to try.
2. **JDK mismatch on PATH.** `JAVA_HOME` is JDK 21 while `java` on PATH is JDK 17. All builds must use
   Gradle toolchains with an explicit Java 8 release target, never the PATH `javac` directly.
3. **GNU Rust toolchain locally.** Local `cargo` builds target `x86_64-pc-windows-gnu`. CI must cover
   the MSVC target as the primary Windows release target.
4. **Long paths on Windows.** Path length must be considered in the sandbox design and tests.
5. **Non-ASCII repository path breaks Gradle test workers.** The repository lives under
   `...\ドキュメント\IdeaProjects\...`. Gradle passes the test-worker classpath through a temporary
   argument file whose encoding does not match the Java launcher on this host, so test classes fail
   with `ClassNotFoundException` when Gradle runs from the real path. CI uses ASCII paths and is not
   affected. Locally, create an ASCII junction and run Gradle through it:

   ```powershell
   cmd /c mklink /J C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii "C:\Users\satos\OneDrive\ドキュメント\IdeaProjects\tororo1066\DeployMC"
   cd C:\Users\satos\AppData\Local\Temp\opencode\dmc-ascii
   .\gradlew.bat build
   ```

   Verified on 2026-11-16: tests fail with the real path and pass through the junction.
6. **OneDrive and Gradle caches.** Keep `.gradle/` and `build/` out of problem reports; delete them
   only after stopping the Gradle daemon (`.\gradlew.bat --stop`).

## GitHub configuration status

- Repository visibility: public
- Default branch: `main`
- Discussions: enabled
- Milestones: `0.1.0` and `0.2.0` created
- Label taxonomy: initial set created
- Branch protection on `main`: enabled with linear history, no force pushes, no branch deletion,
  admin enforcement, and zero required approvals
- Issue forms, PR template, CODEOWNERS, Dependabot, and CI workflows: pending (WP-0.7 and WP-0.8)

## Reproducing the inspection

```powershell
git --version
$env:JAVA_HOME
java -version
node --version
pnpm --version
python --version
cargo --version
rustc --version
docker --version
gh --version
gh auth status
```
