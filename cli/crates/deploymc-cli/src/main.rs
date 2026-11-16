use clap::Parser;
use deploymc::{info_lines, Cli, Command};

fn main() {
    let cli = Cli::parse();

    match cli.command {
        Command::Info => {
            for line in info_lines() {
                println!("{line}");
            }
        }
    }
}
