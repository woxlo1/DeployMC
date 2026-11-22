rootProject.name = "deploymc"

include(
    ":api",
    ":core",
    ":transport",
    ":platform:bukkit",
    ":platform:standalone",
    ":plugin",
    ":tests:fixtures:conflicting-plugin",
    ":tests:fixtures:plugins:deploymc-test-a",
    ":tests:fixtures:plugins:deploymc-test-broken-enable",
    ":tests:fixtures:plugins:deploymc-test-broken-descriptor",
    ":tests:fixtures:plugins:deploymc-test-slow-enable",
    ":tests:fixtures:plugins:deploymc-test-dependency",
)
