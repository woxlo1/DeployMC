package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed Bukkit-family platform version information.
 *
 * <p>This class deliberately has no dependency on Bukkit so it can be unit tested and shared
 * between adapters. Platform classes call it with the values returned by the server.
 */
public final class BukkitPlatformVersion {

  private static final Pattern MINECRAFT_IN_PARENTHESES = Pattern.compile("\\(MC:\\s*([^)]+)\\)");
  private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)");

  private final String software;
  private final String implementation;
  private final String minecraftVersion;

  private BukkitPlatformVersion(String software, String implementation, String minecraftVersion) {
    this.software = software;
    this.implementation = implementation;
    this.minecraftVersion = minecraftVersion;
  }

  public static BukkitPlatformVersion parse(String bukkitVersion, String minecraftVersion) {
    Objects.requireNonNull(bukkitVersion, "bukkitVersion");
    if (bukkitVersion.trim().isEmpty()) {
      throw new IllegalArgumentException("bukkitVersion must not be empty");
    }

    String trimmed = bukkitVersion.trim();
    String software = detectSoftware(trimmed);
    String resolvedMinecraftVersion =
        isBlank(minecraftVersion) ? extractMinecraftVersion(trimmed) : minecraftVersion.trim();
    if (isBlank(resolvedMinecraftVersion)) {
      resolvedMinecraftVersion = "unknown";
    }
    return new BukkitPlatformVersion(software, trimmed, resolvedMinecraftVersion);
  }

  private static String detectSoftware(String version) {
    String lower = version.toLowerCase();
    if (lower.contains("purpur")) {
      return "Purpur";
    }
    if (lower.contains("pufferfish")) {
      return "Pufferfish";
    }
    if (lower.contains("folia")) {
      return "Folia";
    }
    if (lower.contains("paper")) {
      return "Paper";
    }
    if (lower.contains("spigot")) {
      return "Spigot";
    }
    if (lower.contains("craftbukkit")) {
      return "CraftBukkit";
    }
    return "Unknown";
  }

  private static String extractMinecraftVersion(String version) {
    Matcher parenthesized = MINECRAFT_IN_PARENTHESES.matcher(version);
    if (parenthesized.find()) {
      return parenthesized.group(1).trim();
    }
    Matcher leading = LEADING_VERSION.matcher(version);
    if (leading.find()) {
      return leading.group(1);
    }
    return "unknown";
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public String software() {
    return software;
  }

  public String implementation() {
    return implementation;
  }

  public String minecraftVersion() {
    return minecraftVersion;
  }
}
