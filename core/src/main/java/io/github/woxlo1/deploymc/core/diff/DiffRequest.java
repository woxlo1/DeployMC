package io.github.woxlo1.deploymc.core.diff;

import java.util.Arrays;

/** Immutable byte-array input for one diff operation. */
public final class DiffRequest {

  private final byte[] before;
  private final byte[] after;
  private final DiffMode mode;
  private final DiffLimits limits;

  public DiffRequest(byte[] before, byte[] after, DiffMode mode) {
    this(before, after, mode, DiffLimits.DEFAULTS);
  }

  public DiffRequest(byte[] before, byte[] after, DiffMode mode, DiffLimits limits) {
    if (before == null) {
      throw new IllegalArgumentException("before");
    }
    if (after == null) {
      throw new IllegalArgumentException("after");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode");
    }
    if (limits == null) {
      throw new IllegalArgumentException("limits");
    }
    this.before = Arrays.copyOf(before, before.length);
    this.after = Arrays.copyOf(after, after.length);
    this.mode = mode;
    this.limits = limits;
  }

  public byte[] before() {
    return Arrays.copyOf(before, before.length);
  }

  public byte[] after() {
    return Arrays.copyOf(after, after.length);
  }

  byte[] beforeInternal() {
    return before;
  }

  byte[] afterInternal() {
    return after;
  }

  public DiffMode mode() {
    return mode;
  }

  public DiffLimits limits() {
    return limits;
  }
}
