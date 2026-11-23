package io.github.woxlo1.deploymc.core.doctor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** One immutable, localized doctor check with a closed evidence allow-list. */
public final class DoctorCheck {

  private static final Set<String> ALLOWED_EVIDENCE_KEYS =
      Collections.unmodifiableSet(
          new LinkedHashSet<String>(
              Arrays.asList(
                  "activeDeployment",
                  "errors",
                  "freeBytes",
                  "logCaptureCapability",
                  "language",
                  "loopbackProbe",
                  "maxBytes",
                  "mode",
                  "missingKeys",
                  "ok",
                  "port",
                  "portInUse",
                  "portOwned",
                  "restartCapability",
                  "storeBytes",
                  "tokenMode",
                  "unavailable",
                  "unknown",
                  "updateCheckEnabled",
                  "valuesPresent",
                  "verifyIssues",
                  "warnings")));

  private final String id;
  private final DoctorStatus status;
  private final boolean implemented;
  private final String detail;
  private final String remediation;
  private final Map<String, Object> evidence;

  private DoctorCheck(
      String id,
      DoctorStatus status,
      boolean implemented,
      String detail,
      String remediation,
      Map<String, Object> evidence) {
    this.id = id;
    this.status = status;
    this.implemented = implemented;
    this.detail = detail;
    this.remediation = remediation;
    this.evidence = evidence;
  }

  /** Creates a check without evidence. */
  public static DoctorCheck of(
      String id, DoctorStatus status, boolean implemented, String detail, String remediation) {
    return of(id, status, implemented, detail, remediation, Collections.<String, Object>emptyMap());
  }

  /** Creates a check with scalar, allow-listed evidence. */
  public static DoctorCheck of(
      String id,
      DoctorStatus status,
      boolean implemented,
      String detail,
      String remediation,
      Map<String, Object> evidence) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("id must not be empty");
    }
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(detail, "detail");
    return new DoctorCheck(
        id,
        status,
        implemented,
        DoctorText.limit(detail),
        remediation == null ? "" : DoctorText.limit(remediation),
        copyEvidence(evidence));
  }

  /** The closed set of evidence keys that a check may publish. */
  public static Set<String> allowedEvidenceKeys() {
    return ALLOWED_EVIDENCE_KEYS;
  }

  public String id() {
    return id;
  }

  public DoctorStatus status() {
    return status;
  }

  public boolean implemented() {
    return implemented;
  }

  public String detail() {
    return detail;
  }

  public String remediation() {
    return remediation;
  }

  public Map<String, Object> evidence() {
    return evidence;
  }

  /** Returns a copy with all host-specific evidence removed and path-like tokens redacted. */
  public DoctorCheck redacted() {
    return new DoctorCheck(
        id,
        status,
        implemented,
        DoctorText.redact(detail),
        DoctorText.redact(remediation),
        Collections.<String, Object>emptyMap());
  }

  private static Map<String, Object> copyEvidence(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Object> copy = new TreeMap<String, Object>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (!ALLOWED_EVIDENCE_KEYS.contains(key)) {
        throw new IllegalArgumentException("unsupported doctor evidence key: " + key);
      }
      if (value instanceof String) {
        String text = (String) value;
        if (text.length() > 256) {
          throw new IllegalArgumentException("doctor evidence string exceeds 256 characters");
        }
        copy.put(key, DoctorText.redact(text));
      } else if (value instanceof Integer
          || value instanceof Long
          || value instanceof Boolean
          || value instanceof Short
          || value instanceof Byte) {
        copy.put(key, value);
      } else {
        throw new IllegalArgumentException("doctor evidence must be a scalar value: " + key);
      }
    }
    return Collections.unmodifiableMap(copy);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof DoctorCheck)) {
      return false;
    }
    DoctorCheck that = (DoctorCheck) other;
    return implemented == that.implemented
        && id.equals(that.id)
        && status == that.status
        && detail.equals(that.detail)
        && remediation.equals(that.remediation)
        && evidence.equals(that.evidence);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, status, implemented, detail, remediation, evidence);
  }
}
