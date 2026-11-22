plugins {
    `java-library`
}

description = "Test-only Bukkit plugin that delays enable beyond a verification timeout."

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

val generatedResources = layout.buildDirectory.dir("generated-resources/deploymc-test-slow-enable")

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
                "name: DeployMCTestSlowEnable",
                "main: io.github.woxlo1.deploymc.fixture.SlowEnableTestPlugin",
                "version: \"$fixtureVersion\"",
                "api-version: '1.13'",
            ).joinToString("\n") + "\n"
        )
        configYml.get().asFile.writeText("slow-enable-millis: 30000\n")
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
    archiveBaseName.set("deploymc-test-slow-enable")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
