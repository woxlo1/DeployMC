package io.github.woxlo1.deploymc.core.doctor;

import java.util.Objects;

/** Stable result of one doctor check, ordered from least to most severe. */
public enum DoctorStatus {
  PASS(0),
  WARN(1),
  FAIL(2);

  private final int severity;

  DoctorStatus(int severity) {
    this.severity = severity;
  }

  /** Numeric severity: {@code PASS=0}, {@code WARN=1}, {@code FAIL=2}. */
  public int severity() {
    return severity;
  }

  /** Returns the more severe of this value and {@code other}. */
  public DoctorStatus moreSevere(DoctorStatus other) {
    Objects.requireNonNull(other, "other");
    return severity >= other.severity ? this : other;
  }

  /** Returns the more severe of two values. */
  public static DoctorStatus worst(DoctorStatus first, DoctorStatus second) {
    Objects.requireNonNull(first, "first");
    return first.moreSevere(second);
  }

  /** Returns the most severe value in a non-empty sequence, or {@link #WARN} when empty. */
  public static DoctorStatus worst(DoctorStatus... values) {
    Objects.requireNonNull(values, "values");
    DoctorStatus result = WARN;
    for (DoctorStatus value : values) {
      result = result.moreSevere(Objects.requireNonNull(value, "value"));
    }
    return result;
  }
}
