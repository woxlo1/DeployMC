description = "Platform-independent DeployMC core."

// Security corpora live with the other security tests and are shared by unit and fuzz runs.
sourceSets {
    test {
        resources {
            srcDir("../tests/security")
        }
    }
}

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
