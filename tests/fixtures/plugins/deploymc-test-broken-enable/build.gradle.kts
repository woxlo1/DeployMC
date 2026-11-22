plugins {
    `java-library`
}

description = "Test-only Bukkit plugin that fails during onEnable."

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

val generatedResources = layout.buildDirectory.dir("generated-resources/deploymc-test-broken-enable")

val generateDescriptor by tasks.registering {
    val fixtureVersion = "1.0.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val pluginYml = generatedResources.map { it.file("plugin.yml") }
    val configYml = generatedResources.map { it.file("config.yml") }
    outputs.files(pluginYml, configYml)
    doLast {
        pluginYml.get().asFile.parentFile.mkdirs()
        pluginYml.get().asFile.writeText(
            listOf(
                "name: DeployMCTestBrokenEnable",
                "main: io.github.woxlo1.deploymc.fixture.BrokenEnableTestPlugin",
                "version: \"$fixtureVersion\"",
                "api-version: '1.13'",
            ).joinToString("\n") + "\n"
        )
        configYml.get().asFile.writeText("fixture-message: deploymc-test-broken-enable $fixtureVersion\n")
    }
}

sourceSets {
    main { resources.srcDir(generatedResources) }
}

dependencies {
    compileOnly(libs.spigot.api)
}

tasks.named("processResources") { dependsOn(generateDescriptor) }
tasks.named("sourcesJar") { dependsOn(generateDescriptor) }

tasks.named<Jar>("jar") {
    dependsOn(generateDescriptor)
    archiveBaseName.set("deploymc-test-broken-enable")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
