package io.github.woxlo1.deploymc.api;

/**
 * Minimal stable service interface exposed to other plugins through the Java API.
 *
 * <p>The interface is intentionally small. It will grow only through documented API versioning.
 */
public interface DeployMCService {

  /** Returns an immutable snapshot of the current server information. */
  ServerInfo serverInfo();
}
