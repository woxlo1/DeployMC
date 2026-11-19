package io.github.woxlo1.deploymc.gradle;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/** Fails when the final plugin JAR violates the packaging rules from WP-1.7. */
public abstract class PluginJarVerifierTask extends DefaultTask {

  private static final String NAMESPACE_PREFIX = "io/github/woxlo1/deploymc/";
  private static final String NAMESPACE_DOT = "io.github.woxlo1.deploymc.";
  private static final int BYTECODE_BASELINE = 52;
  private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
  private static final String SERVICE_PREFIX = "META-INF/services/";

  @InputFile
  public abstract RegularFileProperty getJar();

  @TaskAction
  public void verifyAction() throws Exception {
    getLogger().lifecycle(verify(getJar().get().getAsFile()));
  }

  /** Verifies {@code jar} and returns a one-line summary. */
  public static String verify(File jar) throws Exception {
    if (!jar.isFile()) {
      throw new GradleException("Plugin JAR does not exist: " + jar.getAbsolutePath());
    }

    try (ZipFile zip = new ZipFile(jar)) {
      List<ZipEntry> entries = new ArrayList<ZipEntry>();
      Enumeration<? extends ZipEntry> enumeration = zip.entries();
      while (enumeration.hasMoreElements()) {
        entries.add(enumeration.nextElement());
      }

      Map<String, Integer> counts = new HashMap<String, Integer>();
      for (ZipEntry entry : entries) {
        Integer previous = counts.put(entry.getName(), 1);
        if (previous != null) {
          counts.put(entry.getName(), previous + 1);
        }
      }
      List<String> duplicates = new ArrayList<String>();
      for (Map.Entry<String, Integer> count : counts.entrySet()) {
        if (count.getValue() > 1) {
          duplicates.add(count.getKey());
        }
      }
      if (!duplicates.isEmpty()) {
        Collections.sort(duplicates);
        throw new GradleException("Duplicate JAR entries: " + String.join(", ", duplicates));
      }
      Set<String> names = new HashSet<String>(counts.keySet());

      boolean multiReleaseDeclared = hasMultiReleaseManifest(zip.getEntry("META-INF/MANIFEST.MF"), zip);
      Set<String> classKeys = new HashSet<String>();
      int classCount = 0;
      int versionedClassCount = 0;
      int maxCommonMajor = 0;

      for (ZipEntry entry : entries) {
        if (!entry.getName().endsWith(".class")) {
          continue;
        }
        classCount++;

        String logicalName = entry.getName();
        Integer version = null;
        if (entry.getName().startsWith(MULTI_RELEASE_PREFIX)) {
          String remainder = entry.getName().substring(MULTI_RELEASE_PREFIX.length());
          String[] parts = remainder.split("/", 2);
          if (parts.length != 2 || parts[0].isEmpty() || !isDigits(parts[0])) {
            throw new GradleException("Malformed multi-release entry: " + entry.getName());
          }
          int entryVersion = Integer.parseInt(parts[0]);
          String versionedPath = parts[1];
          if (entryVersion < 9
              || versionedPath.isEmpty()
              || versionedPath.startsWith("/")
              || versionedPath.contains("\\")
              || hasDotSegment(versionedPath)) {
            throw new GradleException("Malformed multi-release entry: " + entry.getName());
          }
          if (!names.contains(versionedPath)) {
            throw new GradleException(
                "Versioned class " + entry.getName() + " has no base entry " + versionedPath);
          }
          logicalName = versionedPath;
          version = Integer.valueOf(entryVersion);
          versionedClassCount++;
        }

        if (!logicalName.startsWith(NAMESPACE_PREFIX)) {
          throw new GradleException("Class outside " + NAMESPACE_PREFIX + ": " + entry.getName());
        }
        String classKey = (version == null ? "common" : "v" + version) + ":" + logicalName;
        if (!classKeys.add(classKey)) {
          throw new GradleException("Duplicate class for " + logicalName + ": " + entry.getName());
        }

        byte[] header = new byte[8];
        try (DataInputStream input = new DataInputStream(zip.getInputStream(entry))) {
          try {
            input.readFully(header);
          } catch (EOFException exception) {
            throw new GradleException("Truncated class file: " + entry.getName());
          }
        }
        if ((header[0] & 0xFF) != 0xCA
            || (header[1] & 0xFF) != 0xFE
            || (header[2] & 0xFF) != 0xBA
            || (header[3] & 0xFF) != 0xBE) {
          throw new GradleException("Invalid class-file magic: " + entry.getName());
        }
        int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        int maximumMajor = version == null ? BYTECODE_BASELINE : 44 + version.intValue();
        if (major > maximumMajor) {
          throw new GradleException(
              "Class " + entry.getName() + " targets bytecode " + major
                  + "; expected at most " + maximumMajor);
        }
        if (version == null && major > maxCommonMajor) {
          maxCommonMajor = major;
        }
      }

      verifyServiceFiles(entries, zip);

      if (versionedClassCount > 0 && !multiReleaseDeclared) {
        throw new GradleException("Multi-release classes present without Multi-Release: true");
      }

      return String.format(
          Locale.ROOT,
          "Plugin JAR verification passed: %s, %d bytes, %d classes (%d versioned), max common bytecode %d",
          jar.getName(),
          Long.valueOf(jar.length()),
          Integer.valueOf(classCount),
          Integer.valueOf(versionedClassCount),
          Integer.valueOf(maxCommonMajor));
    }
  }

  private static void verifyServiceFiles(List<ZipEntry> entries, ZipFile zip) throws Exception {
    for (ZipEntry entry : entries) {
      if (entry.isDirectory() || !entry.getName().startsWith(SERVICE_PREFIX)) {
        continue;
      }
      String service = entry.getName().substring(SERVICE_PREFIX.length());
      if (service.isEmpty() || service.contains("/")) {
        throw new GradleException("Malformed service file: " + entry.getName());
      }
      if (!service.startsWith(NAMESPACE_DOT)) {
        throw new GradleException("Service file outside " + NAMESPACE_DOT + ": " + entry.getName());
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
          lineNumber++;
          int comment = line.indexOf('#');
          if (comment >= 0) {
            line = line.substring(0, comment);
          }
          String provider = line.trim();
          if (provider.isEmpty()) {
            continue;
          }
          if (!provider.startsWith(NAMESPACE_DOT)) {
            throw new GradleException(
                "Service entry " + entry.getName() + ":" + lineNumber + " outside "
                    + NAMESPACE_DOT + ": " + provider);
          }
        }
      }
    }
  }

  private static boolean hasMultiReleaseManifest(ZipEntry manifest, ZipFile zip) throws Exception {
    if (manifest == null) {
      return false;
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.regionMatches(true, 0, "Multi-Release:", 0, "Multi-Release:".length())) {
          return "true".equalsIgnoreCase(line.substring(line.indexOf(':') + 1).trim());
        }
      }
    }
    return false;
  }

  private static boolean isDigits(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return !value.isEmpty();
  }

  private static boolean hasDotSegment(String path) {
    for (String segment : path.split("/")) {
      if (segment.equals(".") || segment.equals("..")) {
        return true;
      }
    }
    return false;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: PluginJarVerifierTask <jar>");
    }
    System.out.println(verify(new File(args[0])));
  }
}
