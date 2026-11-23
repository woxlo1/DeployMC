package io.github.woxlo1.deploymc.core.doctor;

import io.github.woxlo1.deploymc.api.PlatformInfo;

/** Host-supplied facts that the doctor reports without discovering them itself. */
public final class DoctorEnvironment {

  private final PlatformInfo platformInfo;
  private final String deployMcVersion;
  private final String apiVersion;
  private final String bindAddress;
  private final int bindPort;
  private final boolean loopbackOnly;
  private final boolean bindPortOwnedByHost;
  private final String authMode;
  private final String language;
  private final boolean restartCapabilityAvailable;
  private final boolean updateCheckEnabled;

  private DoctorEnvironment(Builder builder) {
    this.platformInfo = builder.platformInfo;
    this.deployMcVersion = builder.deployMcVersion;
    this.apiVersion = builder.apiVersion;
    this.bindAddress = builder.bindAddress;
    this.bindPort = builder.bindPort;
    this.loopbackOnly = builder.loopbackOnly;
    this.bindPortOwnedByHost = builder.bindPortOwnedByHost;
    this.authMode = builder.authMode;
    this.language = builder.language;
    this.restartCapabilityAvailable = builder.restartCapabilityAvailable;
    this.updateCheckEnabled = builder.updateCheckEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public PlatformInfo platformInfo() {
    return platformInfo;
  }

  public String deployMcVersion() {
    return deployMcVersion;
  }

  public String apiVersion() {
    return apiVersion;
  }

  public String bindAddress() {
    return bindAddress;
  }

  public int bindPort() {
    return bindPort;
  }

  public boolean loopbackOnly() {
    return loopbackOnly;
  }

  public boolean bindPortOwnedByHost() {
    return bindPortOwnedByHost;
  }

  public String authMode() {
    return authMode;
  }

  public String language() {
    return language;
  }

  public boolean restartCapabilityAvailable() {
    return restartCapabilityAvailable;
  }

  public boolean updateCheckEnabled() {
    return updateCheckEnabled;
  }

  /** Mutable builder for host-provided environment values. */
  public static final class Builder {

    private PlatformInfo platformInfo;
    private String deployMcVersion;
    private String apiVersion;
    private String bindAddress;
    private int bindPort;
    private boolean loopbackOnly;
    private boolean bindPortOwnedByHost;
    private String authMode;
    private String language;
    private boolean restartCapabilityAvailable;
    private boolean updateCheckEnabled;

    public Builder platformInfo(PlatformInfo value) {
      this.platformInfo = value;
      return this;
    }

    public Builder deployMcVersion(String value) {
      this.deployMcVersion = value;
      return this;
    }

    public Builder apiVersion(String value) {
      this.apiVersion = value;
      return this;
    }

    public Builder bindAddress(String value) {
      this.bindAddress = value;
      return this;
    }

    public Builder bindPort(int value) {
      this.bindPort = value;
      return this;
    }

    public Builder loopbackOnly(boolean value) {
      this.loopbackOnly = value;
      return this;
    }

    public Builder bindPortOwnedByHost(boolean value) {
      this.bindPortOwnedByHost = value;
      return this;
    }

    public Builder authMode(String value) {
      this.authMode = value;
      return this;
    }

    public Builder language(String value) {
      this.language = value;
      return this;
    }

    public Builder restartCapabilityAvailable(boolean value) {
      this.restartCapabilityAvailable = value;
      return this;
    }

    public Builder updateCheckEnabled(boolean value) {
      this.updateCheckEnabled = value;
      return this;
    }

    public DoctorEnvironment build() {
      return new DoctorEnvironment(this);
    }
  }
}
