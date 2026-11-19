package io.github.woxlo1.deploymc.gradle;

import java.io.File;
import java.util.Locale;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/** Prints the final plugin JAR size in a locale-independent format. */
public abstract class PluginJarSizeTask extends DefaultTask {

  @InputFile
  public abstract RegularFileProperty getJar();

  @TaskAction
  public void report() {
    File jar = getJar().get().getAsFile();
    long bytes = jar.length();
    getLogger()
        .lifecycle(
            String.format(
                Locale.ROOT,
                "Plugin JAR size: %d bytes (%.2f MiB): %s",
                Long.valueOf(bytes),
                Double.valueOf(bytes / (1024.0 * 1024.0)),
                jar.getName()));
  }
}
