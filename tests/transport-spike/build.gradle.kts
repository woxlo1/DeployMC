plugins {
    base
    java
    application
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
}

val netty by configurations.creating
val jetty by configurations.creating

dependencies {
    netty("io.netty:netty-codec-http:4.1.141.Final")
    jetty("org.eclipse.jetty.websocket:websocket-server:9.4.58.v20250814")
    jetty("org.eclipse.jetty:jetty-server:9.4.58.v20250814")
    implementation("io.netty:netty-codec-http:4.1.141.Final")
}

application {
    mainClass.set("EchoSpike")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.register("printSpikeSizes") {
    doLast {
        for (name in listOf("netty", "jetty")) {
            val files = configurations.getByName(name).resolvedConfiguration.resolvedArtifacts
            val total = files.sumOf { it.file.length() }
            println("$name artifacts=${files.size} bytes=$total")
            files.sortedBy { it.file.name }.forEach {
                println("  ${it.file.name} ${it.file.length()}")
            }
        }
    }
}

tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("nettyRelocated") {
    archiveBaseName.set("netty-relocated")
    archiveClassifier.set("")
    configurations = listOf(netty)
    relocate("io.netty", "io.github.woxlo1.deploymc.lib.netty")
}

tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("jettyRelocated") {
    archiveBaseName.set("jetty-relocated")
    archiveClassifier.set("")
    configurations = listOf(jetty)
    relocate("org.eclipse.jetty", "io.github.woxlo1.deploymc.lib.jetty")
    relocate("javax.servlet", "io.github.woxlo1.deploymc.lib.javax.servlet")
}
