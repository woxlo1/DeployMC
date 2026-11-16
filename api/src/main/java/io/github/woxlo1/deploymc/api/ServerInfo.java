package io.github.woxlo1.deploymc.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable server information returned by the public API and the Java API. */
public final class ServerInfo {

  private final String deployMcVersion;
  private final String apiVersion;
  private final PlatformInfo platform;
  private final Map<String, CapabilityState> capabilities;

  public ServerInfo(
      String deployMcVersion,
      String apiVersion,
      PlatformInfo platform,
      Map<String, CapabilityState> capabilities) {
    this.deployMcVersion = requireText(deployMcVersion, "deployMcVersion");
    this.apiVersion = requireText(apiVersion, "apiVersion");
    this.platform = Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(capabilities, "capabilities");

    Map<String, CapabilityState> copy = new LinkedHashMap<String, CapabilityState>();
    for (Map.Entry<String, CapabilityState> entry : capabilities.entrySet()) {
      String key = requireText(entry.getKey(), "capability key");
      CapabilityState state = Objects.requireNonNull(entry.getValue(), "capability state");
      copy.put(key, state);
    }
    this.capabilities = Collections.unmodifiableMap(copy);
  }

  public String deployMcVersion() {
    return deployMcVersion;
  }

  public String apiVersion() {
    return apiVersion;
  }

  public PlatformInfo platform() {
    return platform;
  }

  public Map<String, CapabilityState> capabilities() {
    return capabilities;
  }

  /** Returns the capability state, or {@link CapabilityState#UNKNOWN} when it was not reported. */
  public CapabilityState capability(String key) {
    CapabilityState state = capabilities.get(Objects.requireNonNull(key, "key"));
    return state == null ? CapabilityState.UNKNOWN : state;
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
    if (!(other instanceof ServerInfo)) {
      return false;
    }
    ServerInfo that = (ServerInfo) other;
    return deployMcVersion.equals(that.deployMcVersion)
        && apiVersion.equals(that.apiVersion)
        && platform.equals(that.platform)
        && capabilities.equals(that.capabilities);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deployMcVersion, apiVersion, platform, capabilities);
  }

  @Override
  public String toString() {
    return "ServerInfo{deployMcVersion='"
        + deployMcVersion
        + "', apiVersion='"
        + apiVersion
        + "', platform="
        + platform
        + ", capabilities="
        + capabilities
        + "}";
  }
}
