description = "Embedded HTTP and WebSocket transport for DeployMC."

dependencies {
    api(project(":api"))
    implementation(project(":core"))
    implementation(libs.netty.codec.http)
}

// Route-enumeration assertions read the normative API and AsyncAPI documents.
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processTestResources") {
    from(rootProject.layout.projectDirectory.file("specification/api/openapi.yaml")) {
        into("specification/api")
    }
    from(rootProject.layout.projectDirectory.file("specification/api/asyncapi.yaml")) {
        into("specification/api")
    }
}
