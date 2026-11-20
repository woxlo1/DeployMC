rootProject.name = "deploymc"

include(
    ":api",
    ":core",
    ":transport",
    ":platform:bukkit",
    ":platform:standalone",
    ":plugin",
    ":tests:fixtures:conflicting-plugin",
)
