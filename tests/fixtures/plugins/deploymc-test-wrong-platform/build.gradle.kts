plugins {
    `java-library`
}

description = "Test-only plugin whose descriptor targets a different platform family."

val generatedResources = layout.buildDirectory.dir("generated-resources/deploymc-test-wrong-platform")

val generateDescriptor by tasks.registering {
    val fixtureVersion = "1.0.0"
    inputs.property("fixtureVersion", fixtureVersion)
    val descriptor = generatedResources.map { it.file("velocity-plugin.json") }
    outputs.file(descriptor)
    doLast {
        descriptor.get().asFile.parentFile.mkdirs()
        // A Velocity descriptor without plugin.yml is the deliberate defect for validator tests.
        descriptor.get().asFile.writeText(
            listOf(
                "{",
                "  \"id\": \"deploymc-test-wrong-platform\",",
                "  \"name\": \"DeployMCTestWrongPlatform\",",
                "  \"version\": \"$fixtureVersion\",",
                "  \"main\": \"io.github.woxlo1.deploymc.fixture.WrongPlatformTestPlugin\",",
                "  \"api\": { \"id\": \"velocity\", \"version\": \"3.3\" },",
                "  \"authors\": [\"DeployMC test fixture\"]",
                "}",
            ).joinToString("\n") + "\n"
        )
    }
}

sourceSets {
    main { resources.srcDir(generatedResources) }
}

tasks.named("processResources") { dependsOn(generateDescriptor) }
tasks.named("sourcesJar") { dependsOn(generateDescriptor) }

tasks.named<Jar>("jar") {
    dependsOn(generateDescriptor)
    archiveBaseName.set("deploymc-test-wrong-platform")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
