package io.github.woxlo1.deploymc.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Derives an internal effective peer address from a socket peer and trusted forwarded headers.
 *
 * <p>The output is used only for bounded rate-limit and lockout keys. It is never logged, returned,
 * or used as an identity or audit attribute. DNS names are never resolved.
 */
public final class EffectivePeerResolver {

  private static final int MAX_CHAIN_ENTRIES = 16;

  private final List<Network> trustedNetworks;

  /** Creates a resolver that trusts loopback peers and the supplied literal IP/CIDR networks. */
  public EffectivePeerResolver(Collection<String> trustedProxies) {
    Objects.requireNonNull(trustedProxies, "trustedProxies");
    if (trustedProxies.size() > 64) {
      throw new IllegalArgumentException("trustedProxies must contain at most 64 entries");
    }
    List<Network> networks = new ArrayList<Network>();
    for (String entry : trustedProxies) {
      if (entry == null || entry.trim().isEmpty()) {
        throw new IllegalArgumentException("trustedProxies must not contain empty entries");
      }
      networks.add(Network.parse(entry.trim()));
    }
    this.trustedNetworks = Collections.unmodifiableList(networks);
  }

  /**
   * Returns the canonical effective peer, or {@code null} for a missing or malformed socket peer.
   */
  public String resolve(String socketPeer, Map<String, List<String>> headers) {
    byte[] socketBytes = parseAddress(socketPeer);
    if (socketBytes == null) {
      return null;
    }
    if (!isLoopback(socketBytes) && !isTrustedNetwork(socketBytes)) {
      return canonical(socketBytes);
    }
    String forwarded = firstHeader(headers, "forwarded");
    String value;
    if (forwarded != null) {
      value = parseForwarded(forwarded);
    } else {
      String xForwardedFor = firstHeader(headers, "x-forwarded-for");
      if (xForwardedFor != null) {
        value = parseChain(xForwardedFor);
      } else {
        String connectingIp = firstHeader(headers, "cf-connecting-ip");
        if (connectingIp == null) {
          return canonical(socketBytes);
        }
        byte[] candidate = parseAddress(connectingIp);
        return candidate == null ? canonical(socketBytes) : canonical(candidate);
      }
    }
    byte[] candidate = value == null ? null : parseAddress(value);
    return candidate == null ? canonical(socketBytes) : canonical(candidate);
  }

  private boolean isTrustedNetwork(byte[] address) {
    for (Network network : trustedNetworks) {
      if (network.contains(address)) {
        return true;
      }
    }
    return false;
  }

  private static String firstHeader(Map<String, List<String>> headers, String name) {
    if (headers == null) {
      return null;
    }
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (!name.equalsIgnoreCase(entry.getKey())) {
        continue;
      }
      List<String> values = entry.getValue();
      if (values == null || values.size() != 1) {
        return null;
      }
      String value = values.get(0);
      return value == null || value.trim().isEmpty() ? null : value.trim();
    }
    return null;
  }

  private static String parseForwarded(String value) {
    String[] entries = value.split(",", -1);
    if (entries.length < 1 || entries.length > MAX_CHAIN_ENTRIES) {
      return null;
    }
    String[] parts = entries[0].split(";", -1);
    for (String part : parts) {
      int equals = part.indexOf('=');
      if (equals < 0 || !"for".equalsIgnoreCase(part.substring(0, equals).trim())) {
        continue;
      }
      String result = part.substring(equals + 1).trim();
      if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
        result = result.substring(1, result.length() - 1).trim();
      }
      if (result.startsWith("[") && result.endsWith("]")) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }
    return null;
  }

  private static String parseChain(String value) {
    String[] entries = value.split(",", -1);
    if (entries.length < 1 || entries.length > MAX_CHAIN_ENTRIES) {
      return null;
    }
    return entries[0].trim();
  }

  private static boolean isLoopback(byte[] address) {
    if (address.length == 4) {
      return (address[0] & 0xff) == 127;
    }
    for (int index = 0; index < address.length - 1; index++) {
      if ((address[index] & 0xff) != 0) {
        return false;
      }
    }
    return (address[address.length - 1] & 0xff) == 1;
  }

  private static String canonical(byte[] address) {
    try {
      return InetAddress.getByAddress(address).getHostAddress();
    } catch (UnknownHostException impossible) {
      throw new IllegalStateException("A parsed address became invalid", impossible);
    }
  }

  private static byte[] parseAddress(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() < 3 || trimmed.length() > 45 || trimmed.indexOf('%') >= 0) {
      return null;
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    for (int index = 0; index < trimmed.length(); index++) {
      char character = trimmed.charAt(index);
      boolean allowed =
          (character >= '0' && character <= '9')
              || (character >= 'a' && character <= 'f')
              || (character >= 'A' && character <= 'F')
              || character == '.'
              || character == ':';
      if (!allowed) {
        return null;
      }
    }
    if (trimmed.indexOf(':') < 0 && !isStrictIpv4(trimmed)) {
      return null;
    }
    try {
      InetAddress parsed = InetAddress.getByName(trimmed);
      byte[] bytes = parsed.getAddress();
      return bytes.length == 4 || bytes.length == 16 ? bytes : null;
    } catch (UnknownHostException invalid) {
      return null;
    }
  }

  private static boolean isStrictIpv4(String value) {
    String[] octets = value.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    for (String octet : octets) {
      if (octet.isEmpty() || octet.length() > 3) {
        return false;
      }
      int parsed = 0;
      for (int index = 0; index < octet.length(); index++) {
        char character = octet.charAt(index);
        if (character < '0' || character > '9') {
          return false;
        }
        parsed = parsed * 10 + (character - '0');
      }
      if (parsed > 255) {
        return false;
      }
    }
    return true;
  }

  private static final class Network {

    private final byte[] address;
    private final int prefixBits;

    private Network(byte[] address, int prefixBits) {
      this.address = address;
      this.prefixBits = prefixBits;
    }

    private static Network parse(String value) {
      int slash = value.indexOf('/');
      String rawAddress = slash < 0 ? value : value.substring(0, slash);
      byte[] address = parseAddress(rawAddress);
      if (address == null) {
        throw new IllegalArgumentException("trusted proxy is not an IP literal");
      }
      int maximum = address.length * 8;
      int prefix = maximum;
      if (slash >= 0) {
        String rawPrefix = value.substring(slash + 1).trim();
        if (rawPrefix.isEmpty() || rawPrefix.length() > 3) {
          throw new IllegalArgumentException("trusted proxy CIDR prefix is invalid");
        }
        for (int index = 0; index < rawPrefix.length(); index++) {
          char character = rawPrefix.charAt(index);
          if (character < '0' || character > '9') {
            throw new IllegalArgumentException("trusted proxy CIDR prefix is invalid");
          }
        }
        prefix = Integer.parseInt(rawPrefix);
        if (prefix > maximum) {
          throw new IllegalArgumentException("trusted proxy CIDR prefix is too large");
        }
      }
      return new Network(address, prefix);
    }

    private boolean contains(byte[] candidate) {
      if (candidate.length != address.length) {
        return false;
      }
      int wholeBytes = prefixBits / 8;
      for (int index = 0; index < wholeBytes; index++) {
        if (address[index] != candidate[index]) {
          return false;
        }
      }
      int remaining = prefixBits % 8;
      if (remaining == 0) {
        return true;
      }
      int mask = (0xff << (8 - remaining)) & 0xff;
      return (address[wholeBytes] & mask) == (candidate[wholeBytes] & mask);
    }
  }
}
