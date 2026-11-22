plugins {
    `java-library`
}

description = "Test-only Bukkit plugin with a missing hard dependency."

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

val generatedResources = layout.buildDirectory.dir("generated-resources/deploymc-test-dependency")

val generateDescriptor by tasks.registering {
    val fixtureVersion = "1.0.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val pluginYml = generatedResources.map { it.file("plugin.yml") }
    outputs.file(pluginYml)
    doLast {
        pluginYml.get().asFile.parentFile.mkdirs()
        pluginYml.get().asFile.writeText(
            listOf(
                "name: DeployMCTestDependency",
                "main: io.github.woxlo1.deploymc.fixture.DependencyTestPlugin",
                "version: \"$fixtureVersion\"",
                "api-version: '1.13'",
                "depend:",
                "  - DeployMCTestMissingDependency",
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

tasks.named("processResources") { dependsOn(generateDescriptor) }
tasks.named("sourcesJar") { dependsOn(generateDescriptor) }

tasks.named<Jar>("jar") {
    dependsOn(generateDescriptor)
    archiveBaseName.set("deploymc-test-dependency")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
