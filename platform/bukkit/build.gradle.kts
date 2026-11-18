description = "Bukkit-family platform adapter."

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
    api(project(":api"))
    implementation(project(":core"))
    implementation(project(":transport"))
    compileOnly(libs.spigot.api)
}

val generatedBukkitResources = layout.buildDirectory.dir("generated-resources/bukkit")

val generatePluginYml by tasks.registering {
    val pluginVersion = project.version.toString().replace("\"", "\\\"")
    inputs.property("pluginVersion", pluginVersion)
    val outputFile = generatedBukkitResources.map { it.file("plugin.yml") }
    outputs.file(outputFile)

    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            listOf(
                    "name: DeployMC",
                    "main: io.github.woxlo1.deploymc.platform.bukkit.DeployMCPlugin",
                    "version: \"$pluginVersion\"",
                    "api-version: '1.13'",
                    "")
                .joinToString("\n"),
            Charsets.UTF_8)
    }
}

tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    from(generatePluginYml)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("deploymcVersion", project.version.toString())
}
