import io.github.woxlo1.deploymc.gradle.PluginJarFixturesTask
import io.github.woxlo1.deploymc.gradle.PluginJarSizeTask
import io.github.woxlo1.deploymc.gradle.PluginJarVerifierTask

plugins {
    alias(libs.plugins.shadow)
}

description = "DeployMC plugin aggregator and shaded distribution."

val webDistDir = rootProject.layout.projectDirectory.dir("web/dist")
val webIndexFile = webDistDir.file("index.html").asFile
val webResourcesDir = layout.buildDirectory.dir("generated/resources/web")

val copyWebDist =
    tasks.register<Sync>("copyWebDist") {
      from(webDistDir)
      into(webResourcesDir.map { it.dir("deploymc-web") })
      inputs.file(webIndexFile).withPropertyName("webIndex")
    }

sourceSets {
  main { resources.srcDir(webResourcesDir) }
}

tasks.named("processResources") { dependsOn(copyWebDist) }
tasks.named("sourcesJar") { dependsOn(copyWebDist) }

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))
    implementation(project(":transport"))
    implementation(project(":platform:bukkit"))
    implementation(project(":platform:standalone"))
}

val shadowJarTask = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    inputs.property("relocations", "io.netty|org.yaml.snakeyaml")
    inputs.property("excludes", "BlockHoundIntegration|module-info.class|META-INF/versions/**")
    archiveBaseName.set("DeployMC")
    archiveClassifier.set("")
    relocate("io.netty", "io.github.woxlo1.deploymc.lib.netty")
    relocate("org.yaml.snakeyaml", "io.github.woxlo1.deploymc.lib.snakeyaml")
    exclude("META-INF/services/reactor.blockhound.integration.BlockHoundIntegration")
    exclude("module-info.class")
    exclude("META-INF/versions/**")
    mergeServiceFiles()
}

val verifyPluginJar =
    tasks.register<PluginJarVerifierTask>("verifyPluginJar") {
      group = "verification"
      description = "Fails the build when the final plugin JAR violates packaging rules."
      dependsOn(shadowJarTask)
      jar.set(shadowJarTask.flatMap { it.archiveFile })
    }

val generatePluginJarFixtures =
    tasks.register<PluginJarFixturesTask>("generatePluginJarFixtures") {
      group = "verification"
      description = "Generates the fixture JARs used by verifyPluginJarFixture."
      outputDirectory.set(layout.buildDirectory.dir("fixtures"))
    }

val fixtureJarPath = providers.gradleProperty("deploymc.verifyJarFile")
val verifyPluginJarFixture =
    tasks.register<PluginJarVerifierTask>("verifyPluginJarFixture") {
      group = "verification"
      description = "Verifies a fixture JAR supplied with -Pdeploymc.verifyJarFile=<path>."
      dependsOn(generatePluginJarFixtures)
      if (fixtureJarPath.isPresent) {
        jar.set(file(fixtureJarPath.get()))
      }
    }

val reportPluginJarSize =
    tasks.register<PluginJarSizeTask>("reportPluginJarSize") {
      group = "verification"
      description = "Prints the final plugin JAR size for CI."
      dependsOn(shadowJarTask)
      jar.set(shadowJarTask.flatMap { it.archiveFile })
    }

tasks.named("check") { dependsOn(verifyPluginJar) }
tasks.named("build") { dependsOn(shadowJarTask) }
