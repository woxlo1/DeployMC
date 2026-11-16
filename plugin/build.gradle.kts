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
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
