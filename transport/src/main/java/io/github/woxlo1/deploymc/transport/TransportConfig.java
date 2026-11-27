package io.github.woxlo1.deploymc.transport;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable transport limits and validation policy inputs. */
public final class TransportConfig {

  private static final Set<String> LOOPBACK_HOSTS =
      Collections.unmodifiableSet(
          new LinkedHashSet<>(Arrays.asList("localhost", "127.0.0.1", "::1", "[::1]")));

  private final HttpBindConfig bind;
  private final int maxHeaderBytes;
  private final int maxBodyBytes;
  private final int maxUploadBodyBytes;
  private final int maxConnections;
  private final int workerThreads;
  private final int idleTimeoutMillis;
  private final int shutdownTimeoutMillis;
  private final int maxWebSocketFrameBytes;
  private final int maxWebSocketQueuedMessages;
  private final int maxWebSocketQueuedBytes;
  private final int maxWebSocketConnections;
  private final int slowConsumerMillis;
  private final ObservabilityConfig observability;
  private final Set<String> allowedHosts;
  private final Set<String> allowedOrigins;
  private final Set<String> trustedProxies;
  private final boolean tokenLockoutEnabled;
  private final String webSocketPath;

  private TransportConfig(Builder builder) {
    this.bind = builder.bind;
    this.maxHeaderBytes = builder.maxHeaderBytes;
    this.maxBodyBytes = builder.maxBodyBytes;
    this.maxUploadBodyBytes = builder.maxUploadBodyBytes;
    this.maxConnections = builder.maxConnections;
    this.workerThreads = builder.workerThreads;
    this.idleTimeoutMillis = builder.idleTimeoutMillis;
    this.shutdownTimeoutMillis = builder.shutdownTimeoutMillis;
    this.maxWebSocketFrameBytes = builder.maxWebSocketFrameBytes;
    this.maxWebSocketQueuedMessages = builder.maxWebSocketQueuedMessages;
    this.maxWebSocketQueuedBytes = builder.maxWebSocketQueuedBytes;
    this.maxWebSocketConnections = builder.maxWebSocketConnections;
    this.slowConsumerMillis = builder.slowConsumerMillis;
    this.observability = builder.observability;
    this.webSocketPath = builder.webSocketPath;
    Set<String> hosts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    hosts.addAll(LOOPBACK_HOSTS);
    hosts.addAll(builder.allowedHosts);
    this.allowedHosts = Collections.unmodifiableSet(hosts);
    this.allowedOrigins = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedOrigins));
    this.trustedProxies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.trustedProxies));
    this.tokenLockoutEnabled = builder.tokenLockoutEnabled;
  }

  public static Builder builder(HttpBindConfig bind) {
    return new Builder(bind);
  }

  public HttpBindConfig bind() {
    return bind;
  }

  public int maxHeaderBytes() {
    return maxHeaderBytes;
  }

  public int maxBodyBytes() {
    return maxBodyBytes;
  }

  public int maxUploadBodyBytes() {
    return maxUploadBodyBytes;
  }

  public int maxConnections() {
    return maxConnections;
  }

  public int workerThreads() {
    return workerThreads;
  }

  public int idleTimeoutMillis() {
    return idleTimeoutMillis;
  }

  public int shutdownTimeoutMillis() {
    return shutdownTimeoutMillis;
  }

  public int maxWebSocketFrameBytes() {
    return maxWebSocketFrameBytes;
  }

  public int maxWebSocketQueuedMessages() {
    return maxWebSocketQueuedMessages;
  }

  public int maxWebSocketQueuedBytes() {
    return maxWebSocketQueuedBytes;
  }

  public int maxWebSocketConnections() {
    return maxWebSocketConnections;
  }

  public int slowConsumerMillis() {
    return slowConsumerMillis;
  }

  public ObservabilityConfig observability() {
    return observability;
  }

  public Set<String> allowedHosts() {
    return allowedHosts;
  }

  public Set<String> allowedOrigins() {
    return allowedOrigins;
  }

  public Set<String> trustedProxies() {
    return trustedProxies;
  }

  public boolean tokenLockoutEnabled() {
    return tokenLockoutEnabled;
  }

  public String webSocketPath() {
    return webSocketPath;
  }

  /** Builder for {@link TransportConfig}. */
  public static final class Builder {

    private final HttpBindConfig bind;
    private int maxHeaderBytes = 8 * 1024;
    private int maxBodyBytes = 1024 * 1024;
    private int maxUploadBodyBytes = 64 * 1024 * 1024;
    private int maxConnections = 200;
    private int workerThreads = 4;
    private int idleTimeoutMillis = 10_000;
    private int shutdownTimeoutMillis = 5_000;
    private int maxWebSocketFrameBytes = 64 * 1024;
    private int maxWebSocketQueuedMessages = 1_000;
    private int maxWebSocketQueuedBytes = 2 * 1024 * 1024;
    private int maxWebSocketConnections = 50;
    private int slowConsumerMillis = 5_000;
    private ObservabilityConfig observability = ObservabilityConfig.defaults();
    private String webSocketPath = "/api/v1/ws";
    private Collection<String> allowedHosts = Collections.emptySet();
    private Collection<String> allowedOrigins = Collections.emptySet();
    private Collection<String> trustedProxies = Collections.emptySet();
    private boolean tokenLockoutEnabled = false;

    private Builder(HttpBindConfig bind) {
      this.bind = Objects.requireNonNull(bind, "bind");
    }

    public Builder maxHeaderBytes(int value) {
      this.maxHeaderBytes = value;
      return this;
    }

    public Builder maxBodyBytes(int value) {
      this.maxBodyBytes = value;
      return this;
    }

    public Builder maxUploadBodyBytes(int value) {
      this.maxUploadBodyBytes = value;
      return this;
    }

    public Builder maxConnections(int value) {
      this.maxConnections = value;
      return this;
    }

    public Builder workerThreads(int value) {
      this.workerThreads = value;
      return this;
    }

    public Builder idleTimeoutMillis(int value) {
      this.idleTimeoutMillis = value;
      return this;
    }

    public Builder shutdownTimeoutMillis(int value) {
      this.shutdownTimeoutMillis = value;
      return this;
    }

    public Builder maxWebSocketFrameBytes(int value) {
      this.maxWebSocketFrameBytes = value;
      return this;
    }

    public Builder maxWebSocketQueuedMessages(int value) {
      this.maxWebSocketQueuedMessages = value;
      return this;
    }

    public Builder maxWebSocketQueuedBytes(int value) {
      this.maxWebSocketQueuedBytes = value;
      return this;
    }

    public Builder maxWebSocketConnections(int value) {
      this.maxWebSocketConnections = value;
      return this;
    }

    public Builder slowConsumerMillis(int value) {
      this.slowConsumerMillis = value;
      return this;
    }

    public Builder observability(ObservabilityConfig value) {
      this.observability = Objects.requireNonNull(value, "observability");
      return this;
    }

    public Builder webSocketPath(String value) {
      this.webSocketPath = value;
      return this;
    }

    public Builder allowedHosts(Collection<String> values) {
      this.allowedHosts = clean(values, "allowedHosts");
      return this;
    }

    public Builder allowedOrigins(Collection<String> values) {
      this.allowedOrigins = clean(values, "allowedOrigins");
      return this;
    }

    public Builder trustedProxies(Collection<String> values) {
      this.trustedProxies = clean(values, "trustedProxies");
      return this;
    }

    public Builder tokenLockoutEnabled(boolean value) {
      this.tokenLockoutEnabled = value;
      return this;
    }

    public TransportConfig build() {
      requireRange(maxHeaderBytes, 512, 1024 * 1024, "maxHeaderBytes");
      requireRange(maxBodyBytes, 1024, 64 * 1024 * 1024, "maxBodyBytes");
      requireRange(maxUploadBodyBytes, 1024, 64 * 1024 * 1024, "maxUploadBodyBytes");
      requireRange(maxConnections, 1, 10_000, "maxConnections");
      requireRange(workerThreads, 1, 64, "workerThreads");
      requireRange(idleTimeoutMillis, 1_000, 10 * 60 * 1000, "idleTimeoutMillis");
      requireRange(shutdownTimeoutMillis, 1_000, 60_000, "shutdownTimeoutMillis");
      requireRange(maxWebSocketFrameBytes, 1024, 16 * 1024 * 1024, "maxWebSocketFrameBytes");
      requireRange(maxWebSocketQueuedMessages, 1, 1_000_000, "maxWebSocketQueuedMessages");
      requireRange(maxWebSocketQueuedBytes, 1024, 64 * 1024 * 1024, "maxWebSocketQueuedBytes");
      requireRange(maxWebSocketConnections, 1, 10_000, "maxWebSocketConnections");
      requireRange(slowConsumerMillis, 100, 60_000, "slowConsumerMillis");
      if (webSocketPath == null || !webSocketPath.startsWith("/") || webSocketPath.endsWith("/")) {
        throw new IllegalArgumentException(
            "webSocketPath must be an absolute path without a trailing slash");
      }
      new OriginValidator(allowedOrigins, () -> bind.port());
      new EffectivePeerResolver(trustedProxies);
      return new TransportConfig(this);
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
      if (value < minimum || value > maximum) {
        throw new IllegalArgumentException(
            name + " must be between " + minimum + " and " + maximum);
      }
    }

    private static Collection<String> clean(Collection<String> values, String name) {
      Objects.requireNonNull(values, name);
      Set<String> cleaned = new LinkedHashSet<>();
      for (String value : values) {
        Objects.requireNonNull(value, name + " entry");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
          throw new IllegalArgumentException(name + " must not contain empty values");
        }
        for (int index = 0; index < trimmed.length(); index++) {
          char character = trimmed.charAt(index);
          if (character < 0x20 || character == 0x7f) {
            throw new IllegalArgumentException(name + " must not contain control characters");
          }
        }
        cleaned.add(trimmed);
      }
      return cleaned;
    }
  }
}
