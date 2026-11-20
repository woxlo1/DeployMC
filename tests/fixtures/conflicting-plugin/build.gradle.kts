plugins {
    alias(libs.plugins.shadow)
}

description = "Compatibility fixture that bundles an older, unrelocated Netty."

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

dependencies {
    compileOnly(libs.spigot.api)
    implementation("io.netty:netty-all:4.1.100.Final")
}

val generatedResources = layout.buildDirectory.dir("generated-resources/conflicting-plugin")

val generatePluginYml by tasks.registering {
    val outputFile = generatedResources.map { it.file("plugin.yml") }
    outputs.file(outputFile)
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            listOf(
                "name: LegacyNettyFixture",
                "main: io.github.woxlo1.deploymc.fixture.LegacyNettyPlugin",
                "version: \"1.0.0\"",
                "api-version: '1.13'",
                "commands:",
                "  fixture-disable-deploymc:",
                "    description: Compatibility fixture command for explicit plugin disable",
                "    usage: /fixture-disable-deploymc",
            ).joinToString("\n") + "\n"
        )
    }
}

sourceSets {
    main { resources.srcDir(generatedResources) }
}

tasks.named("processResources") { dependsOn(generatePluginYml) }
tasks.named("sourcesJar") { dependsOn(generatePluginYml) }

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("legacy-netty-fixture")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
    exclude("module-info.class")
    exclude("META-INF/versions/**")
    mergeServiceFiles()
}

tasks.named("build") { dependsOn(tasks.named("shadowJar")) }
