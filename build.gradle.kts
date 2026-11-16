import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "io.github.woxlo1.deploymc"
    version = providers.gradleProperty("deploymcVersion").getOrElse("0.1.0-SNAPSHOT")

    repositories {
        mavenCentral()
    }
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    dependencies {
        "testImplementation"(libsCatalog.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libsCatalog.findLibrary("junit-platform-launcher").get())
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat(libsCatalog.findVersion("google-java-format").get().requiredVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("misc") {
            target("*.md", ".gitignore", ".gitattributes", ".editorconfig")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

tasks.register("verifyJava8") {
    group = "verification"
    description = "Checks that production class files target Java 8 bytecode."
    dependsOn(subprojects.map { it.tasks.named("classes") })
    doLast {
        subprojects.forEach { project ->
            val classesDir = project.layout.buildDirectory.dir("classes/java/main").get().asFile
            if (!classesDir.exists()) {
                return@forEach
            }
            classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
                file.inputStream().use { stream ->
                    stream.skip(6)
                    val major = (stream.read() shl 8) or stream.read()
                    check(major <= 52) {
                        "Class ${file.relativeTo(classesDir)} targets bytecode $major; expected 52 (Java 8) or lower"
                    }
                }
            }
        }
    }
}
