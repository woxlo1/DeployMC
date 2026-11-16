package io.github.woxlo1.deploymc.api;

import java.util.Objects;

/** Immutable description of the running server platform. */
public final class PlatformInfo {

  private final String softwareName;
  private final String softwareVersion;
  private final String minecraftVersion;
  private final String javaVersion;
  private final String operatingSystem;

  public PlatformInfo(
      String softwareName,
      String softwareVersion,
      String minecraftVersion,
      String javaVersion,
      String operatingSystem) {
    this.softwareName = requireText(softwareName, "softwareName");
    this.softwareVersion = requireText(softwareVersion, "softwareVersion");
    this.minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
    this.javaVersion = requireText(javaVersion, "javaVersion");
    this.operatingSystem = requireText(operatingSystem, "operatingSystem");
  }

  public String softwareName() {
    return softwareName;
  }

  public String softwareVersion() {
    return softwareVersion;
  }

  public String minecraftVersion() {
    return minecraftVersion;
  }

  public String javaVersion() {
    return javaVersion;
  }

  public String operatingSystem() {
    return operatingSystem;
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PlatformInfo)) {
      return false;
    }
    PlatformInfo that = (PlatformInfo) other;
    return softwareName.equals(that.softwareName)
        && softwareVersion.equals(that.softwareVersion)
        && minecraftVersion.equals(that.minecraftVersion)
        && javaVersion.equals(that.javaVersion)
        && operatingSystem.equals(that.operatingSystem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        softwareName, softwareVersion, minecraftVersion, javaVersion, operatingSystem);
  }

  @Override
  public String toString() {
    return "PlatformInfo{software='"
        + softwareName
        + "', softwareVersion='"
        + softwareVersion
        + "', minecraftVersion='"
        + minecraftVersion
        + "', javaVersion='"
        + javaVersion
        + "', operatingSystem='"
        + operatingSystem
        + "'}";
  }
}
