plugins {
    `java-library`
}

description = "Test-only Bukkit plugin that emits logs at all levels at configurable rates."

repositories {
    maven {
        name = "spigotSnapshots"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "paperPublic"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

val generatedResources = layout.buildDirectory.dir("generated-resources/deploymc-test-log-noise")

val generateFixtureResources by tasks.registering {
    val fixtureVersion = "1.0.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val pluginYml = generatedResources.map { it.file("plugin.yml") }
    val configYml = generatedResources.map { it.file("config.yml") }
    outputs.files(pluginYml, configYml)
    doLast {
        pluginYml.get().asFile.parentFile.mkdirs()
        pluginYml.get().asFile.writeText(
            listOf(
                "name: DeployMCTestLogNoise",
                "main: io.github.woxlo1.deploymc.fixture.LogNoiseTestPlugin",
                "version: \"$fixtureVersion\"",
                "api-version: '1.13'",
            ).joinToString("\n") + "\n"
        )
        configYml.get().asFile.writeText(
            listOf(
                "enabled: true",
                "levels:",
                "  finest:",
                "    ticks: 40",
                "  finer:",
                "    ticks: 40",
                "  fine:",
                "    ticks: 40",
                "  config:",
                "    ticks: 40",
                "  info:",
                "    ticks: 20",
                "  warning:",
                "    ticks: 20",
                "  severe:",
                "    ticks: 20",
            ).joinToString("\n") + "\n"
        )
    }
}

sourceSets {
    main { resources.srcDir(generatedResources) }
}

dependencies {
    compileOnly(libs.spigot.api)
}

tasks.named("processResources") { dependsOn(generateFixtureResources) }
tasks.named("sourcesJar") { dependsOn(generateFixtureResources) }

tasks.named<Jar>("jar") {
    dependsOn(generateFixtureResources)
    archiveBaseName.set("deploymc-test-log-noise")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
