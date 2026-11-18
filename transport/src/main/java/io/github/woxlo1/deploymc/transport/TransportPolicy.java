package io.github.woxlo1.deploymc.transport;

/** Host and Origin validation hooks. */
public interface TransportPolicy {

  boolean allowHost(String host);

  boolean allowOrigin(String origin);
}
