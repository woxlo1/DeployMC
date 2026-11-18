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

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("DeployMC")
    archiveClassifier.set("")
    relocate("io.netty", "io.github.woxlo1.deploymc.lib.netty")
    exclude("META-INF/services/reactor.blockhound.integration.BlockHoundIntegration")
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
