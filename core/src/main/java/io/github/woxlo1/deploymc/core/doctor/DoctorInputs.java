package io.github.woxlo1.deploymc.core.doctor;

import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.auth.cloudflare.JwtDiagnosticsSnapshot;
import io.github.woxlo1.deploymc.core.config.ConfigValidationResult;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/** Injectable inputs for one deterministic doctor run. */
public final class DoctorInputs {

  private final Clock clock;
  private final Locale locale;
  private final DoctorEnvironment environment;
  private final PlatformCapabilities capabilities;
  private final SandboxResolver sandboxResolver;
  private final SnapshotStore snapshotStore;
  private final SnapshotStoreConfig snapshotLimits;
  private final ConfigValidationResult configValidation;
  private final JwtDiagnosticsSnapshot jwtDiagnosticsSnapshot;
  private final int missingLocalizationKeys;

  private DoctorInputs(Builder builder) {
    this.clock = builder.clock;
    this.locale = builder.locale;
    this.environment = builder.environment;
    this.capabilities = builder.capabilities;
    this.sandboxResolver = builder.sandboxResolver;
    this.snapshotStore = builder.snapshotStore;
    this.snapshotLimits = builder.snapshotLimits;
    this.configValidation = builder.configValidation;
    this.jwtDiagnosticsSnapshot = builder.jwtDiagnosticsSnapshot;
    this.missingLocalizationKeys = builder.missingLocalizationKeys;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Clock clock() {
    return clock;
  }

  public Locale locale() {
    return locale;
  }

  public DoctorEnvironment environment() {
    return environment;
  }

  public PlatformCapabilities capabilities() {
    return capabilities;
  }

  public SandboxResolver sandboxResolver() {
    return sandboxResolver;
  }

  public SnapshotStore snapshotStore() {
    return snapshotStore;
  }

  public SnapshotStoreConfig snapshotLimits() {
    return snapshotLimits;
  }

  public ConfigValidationResult configValidation() {
    return configValidation;
  }

  /** Null outside Cloudflare mode; the snapshot contains only closed rejection counters. */
  public JwtDiagnosticsSnapshot jwtDiagnosticsSnapshot() {
    return jwtDiagnosticsSnapshot;
  }

  public int missingLocalizationKeys() {
    return missingLocalizationKeys;
  }

  /** Mutable builder. Optional product dependencies stay nullable and are reported as WARN. */
  public static final class Builder {

    private Clock clock = Clock.systemUTC();
    private Locale locale = Locale.ROOT;
    private DoctorEnvironment environment = DoctorEnvironment.builder().build();
    private PlatformCapabilities capabilities = PlatformCapabilities.empty();
    private SandboxResolver sandboxResolver;
    private SnapshotStore snapshotStore;
    private SnapshotStoreConfig snapshotLimits;
    private ConfigValidationResult configValidation;
    private JwtDiagnosticsSnapshot jwtDiagnosticsSnapshot;
    private int missingLocalizationKeys = -1;

    public Builder clock(Clock value) {
      this.clock = Objects.requireNonNull(value, "clock");
      return this;
    }

    public Builder locale(Locale value) {
      this.locale = value == null ? Locale.ROOT : value;
      return this;
    }

    public Builder environment(DoctorEnvironment value) {
      this.environment = Objects.requireNonNull(value, "environment");
      return this;
    }

    public Builder capabilities(PlatformCapabilities value) {
      this.capabilities = Objects.requireNonNull(value, "capabilities");
      return this;
    }

    public Builder sandboxResolver(SandboxResolver value) {
      this.sandboxResolver = value;
      return this;
    }

    public Builder snapshotStore(SnapshotStore value) {
      this.snapshotStore = value;
      return this;
    }

    public Builder snapshotLimits(SnapshotStoreConfig value) {
      this.snapshotLimits = value;
      return this;
    }

    public Builder configValidation(ConfigValidationResult value) {
      this.configValidation = value;
      return this;
    }

    public Builder jwtDiagnosticsSnapshot(JwtDiagnosticsSnapshot value) {
      this.jwtDiagnosticsSnapshot = value;
      return this;
    }

    public Builder missingLocalizationKeys(int value) {
      this.missingLocalizationKeys = value;
      return this;
    }

    public DoctorInputs build() {
      return new DoctorInputs(this);
    }
  }
}
