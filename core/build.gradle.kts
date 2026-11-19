description = "Platform-independent DeployMC core."

dependencies {
    api(project(":api"))
    implementation(libs.snakeyaml)
}

// The normative config schema is the source of truth for the loader's validation rules.
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.file("specification/config/config.schema.json")) {
        into("config")
    }
}
