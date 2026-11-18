package io.github.woxlo1.deploymc.core;

import java.util.Objects;

/** Immutable metadata for one stable platform capability key. */
public final class CapabilityDefinition {

  private final String key;
  private final String description;
  private final String fallback;

  public CapabilityDefinition(String key, String description, String fallback) {
    this.key = requireText(key, "key");
    this.description = requireText(description, "description");
    this.fallback = requireText(fallback, "fallback");
  }

  public String key() {
    return key;
  }

  public String description() {
    return description;
  }

  /** Behavior clients must use when this capability is unavailable or unknown. */
  public String fallback() {
    return fallback;
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
    if (!(other instanceof CapabilityDefinition)) {
      return false;
    }
    CapabilityDefinition that = (CapabilityDefinition) other;
    return key.equals(that.key)
        && description.equals(that.description)
        && fallback.equals(that.fallback);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, description, fallback);
  }

  @Override
  public String toString() {
    return "CapabilityDefinition{key='" + key + "'}";
  }
}
