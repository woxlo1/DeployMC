package io.github.woxlo1.deploymc.core.rbac;

import io.github.woxlo1.deploymc.core.config.ConfigView;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically swaps immutable RBAC snapshots. Startup and invalid reload never fail open. */
public final class RbacPolicyManager {

  private final AtomicReference<RbacPolicy> current;

  public RbacPolicyManager(RbacPolicy initial) {
    this.current =
        new AtomicReference<RbacPolicy>(initial == null ? RbacPolicy.denyAll() : initial);
  }

  public static RbacPolicyManager from(ConfigView view) {
    return new RbacPolicyManager(RbacPolicy.fromConfig(view));
  }

  public RbacPolicy current() {
    return current.get();
  }

  public void install(ConfigView view) {
    Objects.requireNonNull(view, "view");
    current.set(RbacPolicy.fromConfig(view));
  }

  /** Invalid configuration reloads must call this instead of retaining a permissive snapshot. */
  public void denyAll() {
    current.set(RbacPolicy.denyAll());
  }
}
