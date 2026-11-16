package io.github.woxlo1.deploymc.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Validated HTTP bind configuration.
 *
 * <p>Binding to a non-loopback address is refused unless it is explicitly enabled. This mirrors the
 * security default that DeployMC is reached through a local tunnel.
 */
public final class HttpBindConfig {

  private static final int MAX_ADDRESS_LENGTH = 255;

  private final String address;
  private final int port;
  private final boolean allowNonLoopback;

  private HttpBindConfig(String address, int port, boolean allowNonLoopback) {
    this.address = address;
    this.port = port;
    this.allowNonLoopback = allowNonLoopback;
  }

  public static HttpBindConfig of(String address, int port, boolean allowNonLoopback) {
    Objects.requireNonNull(address, "address");
    for (int index = 0; index < address.length(); index++) {
      char character = address.charAt(index);
      if (character < 0x20 || character == 0x7f) {
        throw new IllegalArgumentException("bind address must not contain control characters");
      }
    }
    String trimmed = address.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("bind address must not be empty");
    }
    if (trimmed.length() > MAX_ADDRESS_LENGTH) {
      throw new IllegalArgumentException("bind address is too long");
    }
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
    if (!isLoopbackAddress(trimmed) && !allowNonLoopback) {
      throw new IllegalArgumentException(
          "refusing to bind to non-loopback address '" + trimmed + "' without allow-non-loopback");
    }
    return new HttpBindConfig(trimmed, port, allowNonLoopback);
  }

  public static boolean isLoopbackAddress(String address) {
    Objects.requireNonNull(address, "address");
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException exception) {
      throw new IllegalArgumentException("Unknown bind address: " + address, exception);
    }
  }

  public String address() {
    return address;
  }

  public int port() {
    return port;
  }

  public boolean allowNonLoopback() {
    return allowNonLoopback;
  }

  public boolean isLoopbackOnly() {
    return isLoopbackAddress(address);
  }

  public String describe() {
    return address + ":" + port;
  }
}
