package io.github.woxlo1.deploymc.core.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Closed warning vocabulary for a verification report. */
public final class AuditVerificationWarning {

  public static final String CLOCK_REGRESSION = "clock-regression";
  public static final String PENDING_WITHOUT_FINAL = "pending-without-final";
  public static final String RETENTION_INCOMPLETE = "retention-incomplete";
  public static final String PARTIAL_LINE = "partial-line";

  private final String code;
  private final String detail;

  public AuditVerificationWarning(String code, String detail) {
    if (!CLOCK_REGRESSION.equals(code)
        && !PENDING_WITHOUT_FINAL.equals(code)
        && !RETENTION_INCOMPLETE.equals(code)
        && !PARTIAL_LINE.equals(code)) {
      throw new IllegalArgumentException("unknown audit warning code");
    }
    if (detail == null || detail.isEmpty() || detail.length() > 512) {
      throw new IllegalArgumentException("invalid audit warning detail");
    }
    this.code = code;
    this.detail = detail;
  }

  public String code() {
    return code;
  }

  public String detail() {
    return detail;
  }

  public Map<String, Object> toJsonObject() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("code", code);
    result.put("detail", detail);
    return result;
  }
}
