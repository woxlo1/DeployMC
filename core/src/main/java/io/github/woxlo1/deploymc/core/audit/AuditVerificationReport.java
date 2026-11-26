package io.github.woxlo1.deploymc.core.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable, redacted verification result described by the OpenAPI report schema. */
public final class AuditVerificationReport {

  public static final String LIMITATION_NO_OFFHOST_ANCHOR = "audit.limitation.no-offhost-anchor";
  public static final String LIMITATION_TAMPER_EVIDENCE = "audit.limitation.tamper-evidence";

  private final boolean ok;
  private final int formatVersion;
  private final long recordsChecked;
  private final long lastSeq;
  private final String lastHash;
  private final AuditVerificationFailure firstFailure;
  private final List<AuditVerificationWarning> warnings;
  private final AuditRecord latestAnchor;
  private final List<String> limitations;

  public AuditVerificationReport(
      boolean ok,
      int formatVersion,
      long recordsChecked,
      long lastSeq,
      String lastHash,
      AuditVerificationFailure firstFailure,
      List<AuditVerificationWarning> warnings,
      AuditRecord latestAnchor,
      List<String> limitations) {
    this.ok = ok;
    this.formatVersion = formatVersion;
    this.recordsChecked = recordsChecked;
    this.lastSeq = lastSeq;
    this.lastHash = lastHash;
    this.firstFailure = firstFailure;
    this.warnings = Collections.unmodifiableList(new ArrayList<AuditVerificationWarning>(warnings));
    this.latestAnchor = latestAnchor;
    this.limitations = Collections.unmodifiableList(new ArrayList<String>(limitations));
    if (this.warnings.size() > 1024 || this.limitations.size() > 16) {
      throw new IllegalArgumentException("verification report exceeds bounded collections");
    }
  }

  public boolean ok() {
    return ok;
  }

  public int formatVersion() {
    return formatVersion;
  }

  public long recordsChecked() {
    return recordsChecked;
  }

  public long lastSeq() {
    return lastSeq;
  }

  public String lastHash() {
    return lastHash;
  }

  public AuditVerificationFailure firstFailure() {
    return firstFailure;
  }

  public List<AuditVerificationWarning> warnings() {
    return warnings;
  }

  public AuditRecord latestAnchor() {
    return latestAnchor;
  }

  public List<String> limitations() {
    return limitations;
  }

  /** Renders the exact OpenAPI field names and excludes all host paths. */
  public Map<String, Object> toJsonObject() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("ok", Boolean.valueOf(ok));
    result.put("formatVersion", Long.valueOf(formatVersion));
    result.put("recordsChecked", Long.valueOf(recordsChecked));
    result.put("lastSeq", Long.valueOf(lastSeq));
    result.put("lastHash", lastHash);
    result.put("firstFailure", firstFailure == null ? null : firstFailure.toJsonObject());
    List<Object> warningValues = new ArrayList<Object>(warnings.size());
    for (AuditVerificationWarning warning : warnings) {
      warningValues.add(warning.toJsonObject());
    }
    result.put("warnings", warningValues);
    result.put("latestAnchor", latestAnchor == null ? null : latestAnchor.values());
    result.put("limitations", new ArrayList<Object>(limitations));
    return result;
  }
}
