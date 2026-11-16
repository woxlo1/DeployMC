//! Testable core of the DeployMC command-line interface.

use clap::{Parser, Subcommand};

pub const CLI_NAME: &str = "deploymc";

/// DeployMC command-line interface (pre-alpha).
#[derive(Debug, Parser)]
#[command(name = "deploymc", version, about, long_about = None)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Command,
}

#[derive(Debug, Subcommand)]
pub enum Command {
    /// Print local CLI and platform information.
    Info,
}

/// Description of the local environment. This intentionally reports only what the CLI can verify
/// without a running server or an authenticated connection.
pub fn info_lines() -> Vec<String> {
    vec![
        format!("{} {}", CLI_NAME, env!("CARGO_PKG_VERSION")),
        format!("os: {}", std::env::consts::OS),
        format!("arch: {}", std::env::consts::ARCH),
        "status: pre-alpha (local information only)".to_owned(),
    ]
}

#[cfg(test)]
mod tests {
    use super::{info_lines, Cli, Command};
    use clap::{error::ErrorKind, Parser};

    #[test]
    fn parses_info_subcommand() {
        let cli = Cli::try_parse_from(["deploymc", "info"]).expect("info should parse");
        assert!(matches!(cli.command, Command::Info));
    }

    #[test]
    fn requires_a_subcommand() {
        assert!(Cli::try_parse_from(["deploymc"]).is_err());
    }

    #[test]
    fn exposes_the_crate_version() {
        let error = Cli::try_parse_from(["deploymc", "--version"]).expect_err("version exits");
        assert_eq!(error.kind(), ErrorKind::DisplayVersion);
        assert!(error.to_string().contains(env!("CARGO_PKG_VERSION")));
    }

    #[test]
    fn reports_local_platform_information() {
        let lines = info_lines();
        assert_eq!(
            lines.first().map(String::as_str),
            Some(concat!("deploymc ", env!("CARGO_PKG_VERSION")))
        );
        assert!(lines
            .iter()
            .any(|line| line == "os: windows" || line == "os: linux" || line == "os: macos"));
        assert!(lines.iter().any(|line| line.starts_with("arch: ")));
        assert!(lines
            .iter()
            .any(|line| line.starts_with("status: pre-alpha")));
    }
}
