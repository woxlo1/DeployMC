description = "Embedded HTTP and WebSocket transport for DeployMC."

dependencies {
    api(project(":api"))
    implementation(project(":core"))
    implementation(libs.netty.codec.http)
}
