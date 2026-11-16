package io.github.woxlo1.deploymc.api;

/**
 * State of a platform capability.
 *
 * <p>A capability describes a runtime feature that may be available, unavailable, or not yet
 * determined on the current platform. Clients must degrade gracefully when a capability is not
 * available and must never assume that an unknown capability is available.
 */
public enum CapabilityState {
  AVAILABLE,
  UNAVAILABLE,
  UNKNOWN;

  public boolean isAvailable() {
    return this == AVAILABLE;
  }
}
