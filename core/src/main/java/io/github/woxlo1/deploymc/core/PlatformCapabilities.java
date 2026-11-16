package io.github.woxlo1.deploymc.core;

import io.github.woxlo1.deploymc.api.CapabilityState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable set of capability states reported by the current platform adapter. */
public final class PlatformCapabilities {

  private static final PlatformCapabilities EMPTY =
      new PlatformCapabilities(Collections.<String, CapabilityState>emptyMap());

  private final Map<String, CapabilityState> states;

  private PlatformCapabilities(Map<String, CapabilityState> states) {
    this.states = states;
  }

  public static PlatformCapabilities empty() {
    return EMPTY;
  }

  public static PlatformCapabilities of(Map<String, CapabilityState> states) {
    Objects.requireNonNull(states, "states");
    Map<String, CapabilityState> copy = new LinkedHashMap<String, CapabilityState>();
    for (Map.Entry<String, CapabilityState> entry : states.entrySet()) {
      String key = Objects.requireNonNull(entry.getKey(), "capability key");
      if (key.trim().isEmpty()) {
        throw new IllegalArgumentException("capability key must not be empty");
      }
      copy.put(key, Objects.requireNonNull(entry.getValue(), "capability state"));
    }
    return new PlatformCapabilities(Collections.unmodifiableMap(copy));
  }

  /** Returns the state, or {@link CapabilityState#UNKNOWN} when the capability was not reported. */
  public CapabilityState state(String key) {
    CapabilityState state = states.get(Objects.requireNonNull(key, "key"));
    return state == null ? CapabilityState.UNKNOWN : state;
  }

  public boolean isAvailable(String key) {
    return state(key).isAvailable();
  }

  public Map<String, CapabilityState> asMap() {
    return states;
  }
}
