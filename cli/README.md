# DeployMC CLI

Rust command-line client for DeployMC. This is a Phase 0 skeleton: it exposes `--version`, `--help`,
and a local `info` command. It does not contact a server yet.

## Commands

Run from `cli/`:

```powershell
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo build --workspace
```

On Windows with the GNU toolchain, a repository path containing non-ASCII characters makes the
MinGW linker fail to find artifacts. Point Cargo at an ASCII target directory:

```powershell
$env:CARGO_TARGET_DIR = "C:\Users\<you>\AppData\Local\Temp\deploymc-cli-target"
cargo test --workspace
```
