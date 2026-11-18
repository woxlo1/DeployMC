plugins {
    alias(libs.plugins.shadow)
}

description = "DeployMC plugin aggregator and shaded distribution."

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
