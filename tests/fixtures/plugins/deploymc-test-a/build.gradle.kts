plugins {
    `java-library`
}

description = "Test-only healthy Bukkit plugin with two stable-identity versions."

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

val generated100 = layout.buildDirectory.dir("generated-resources/deploymc-test-a-1.0.0")
val generated110 = layout.buildDirectory.dir("generated-resources/deploymc-test-a-1.1.0")

val generateDescriptor100 by tasks.registering {
    val fixtureVersion = "1.0.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val pluginYml = generated100.map { it.file("plugin.yml") }
    val configYml = generated100.map { it.file("config.yml") }
    outputs.files(pluginYml, configYml)
    doLast {
        pluginYml.get().asFile.parentFile.mkdirs()
        pluginYml.get().asFile.writeText(
            listOf(
                    "name: DeployMCTestA",
                    "main: io.github.woxlo1.deploymc.fixture.VersionedTestPlugin",
                    "version: \"$fixtureVersion\"",
                    "api-version: '1.13'",
                    "commands:",
                    "  dmctest:",
                    "    description: Test-only version report",
                    "    usage: /dmctest",
                )
                .joinToString("\n") + "\n"
        )
        configYml.get().asFile.writeText("fixture-message: deploymc-test-a $fixtureVersion\n")
    }
}

val generateDescriptor110 by tasks.registering {
    val fixtureVersion = "1.1.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val pluginYml = generated110.map { it.file("plugin.yml") }
    val configYml = generated110.map { it.file("config.yml") }
    outputs.files(pluginYml, configYml)
    doLast {
        pluginYml.get().asFile.parentFile.mkdirs()
        pluginYml.get().asFile.writeText(
            listOf(
                    "name: DeployMCTestA",
                    "main: io.github.woxlo1.deploymc.fixture.VersionedTestPlugin",
                    "version: \"$fixtureVersion\"",
                    "api-version: '1.13'",
                    "commands:",
                    "  dmctest:",
                    "    description: Test-only version report",
                    "    usage: /dmctest",
                )
                .joinToString("\n") + "\n"
        )
        configYml.get().asFile.writeText("fixture-message: deploymc-test-a $fixtureVersion\n")
    }
}

sourceSets {
    main { resources.srcDir(generated100) }
    create("version110") {
        java.srcDir("src/main/java")
        resources.srcDir(generated110)
    }
}

dependencies {
    compileOnly(libs.spigot.api)
    "version110CompileOnly"(libs.spigot.api)
}

tasks.named("processResources") { dependsOn(generateDescriptor100) }
tasks.named("sourcesJar") { dependsOn(generateDescriptor100) }
tasks.named("processVersion110Resources") { dependsOn(generateDescriptor110) }
tasks.named("version110Classes") { dependsOn(generateDescriptor110) }

tasks.named<Jar>("jar") {
    dependsOn(generateDescriptor100)
    archiveBaseName.set("deploymc-test-a")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}

val version110Jar by tasks.registering(Jar::class) {
    dependsOn(generateDescriptor110, tasks.named("version110Classes"))
    archiveBaseName.set("deploymc-test-a")
    archiveVersion.set("1.1.0")
    archiveClassifier.set("")
    from(sourceSets.named("version110").get().output)
}

tasks.named("build") { dependsOn(version110Jar) }
