package io.github.woxlo1.deploymc.core;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic version value object with the subset of SemVer 2.0.0 needed for update and migration
 * checks.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern PATTERN =
      Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

  private final int major;
  private final int minor;
  private final int patch;
  private final String preRelease;
  private final String buildMetadata;

  private SemanticVersion(
      int major, int minor, int patch, String preRelease, String buildMetadata) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.preRelease = preRelease;
    this.buildMetadata = buildMetadata;
  }

  public static SemanticVersion parse(String value) {
    Objects.requireNonNull(value, "value");
    Matcher matcher = PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid semantic version: " + value);
    }
    try {
      return new SemanticVersion(
          parseComponent(matcher.group(1), value),
          parseComponent(matcher.group(2), value),
          parseComponent(matcher.group(3), value),
          matcher.group(4),
          matcher.group(5));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Semantic version component is too large: " + value, exception);
    }
  }

  private static int parseComponent(String component, String fullVersion) {
    if (component.length() > 1 && component.charAt(0) == '0') {
      throw new IllegalArgumentException(
          "Semantic version components must not have leading zeros: " + fullVersion);
    }
    return Integer.parseInt(component);
  }

  public int major() {
    return major;
  }

  public int minor() {
    return minor;
  }

  public int patch() {
    return patch;
  }

  public String preRelease() {
    return preRelease;
  }

  public String buildMetadata() {
    return buildMetadata;
  }

  @Override
  public int compareTo(SemanticVersion other) {
    Objects.requireNonNull(other, "other");
    int result = Integer.compare(major, other.major);
    if (result != 0) {
      return result;
    }
    result = Integer.compare(minor, other.minor);
    if (result != 0) {
      return result;
    }
    result = Integer.compare(patch, other.patch);
    if (result != 0) {
      return result;
    }
    if (preRelease == null && other.preRelease == null) {
      return 0;
    }
    if (preRelease == null) {
      return 1;
    }
    if (other.preRelease == null) {
      return -1;
    }
    return comparePreRelease(preRelease, other.preRelease);
  }

  private static int comparePreRelease(String left, String right) {
    String[] leftParts = left.split("\\.");
    String[] rightParts = right.split("\\.");
    int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      if (index >= leftParts.length) {
        return -1;
      }
      if (index >= rightParts.length) {
        return 1;
      }
      int comparison = compareIdentifier(leftParts[index], rightParts[index]);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  private static int compareIdentifier(String left, String right) {
    boolean leftNumeric = isNumeric(left);
    boolean rightNumeric = isNumeric(right);
    if (leftNumeric && rightNumeric) {
      return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
    }
    if (leftNumeric) {
      return -1;
    }
    if (rightNumeric) {
      return 1;
    }
    return left.compareTo(right);
  }

  private static boolean isNumeric(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      if (!Character.isDigit(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SemanticVersion)) {
      return false;
    }
    SemanticVersion that = (SemanticVersion) other;
    return major == that.major
        && minor == that.minor
        && patch == that.patch
        && Objects.equals(preRelease, that.preRelease)
        && Objects.equals(buildMetadata, that.buildMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, preRelease, buildMetadata);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(major).append('.').append(minor).append('.').append(patch);
    if (preRelease != null) {
      builder.append('-').append(preRelease);
    }
    if (buildMetadata != null) {
      builder.append('+').append(buildMetadata);
    }
    return builder.toString();
  }
}
