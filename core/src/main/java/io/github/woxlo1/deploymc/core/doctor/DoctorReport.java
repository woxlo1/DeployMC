package io.github.woxlo1.deploymc.core.doctor;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Stable, redactable report described by the {@code DoctorReport} OpenAPI schema. */
public final class DoctorReport {

  public static final String SCHEMA_VERSION = "1.0";
  public static final String SCOPE = "limited";

  private final String generatedAt;
  private final DoctorStatus overall;
  private final List<DoctorCheck> checks;
  private final boolean redacted;

  private DoctorReport(String generatedAt, List<DoctorCheck> checks, boolean redacted) {
    this.generatedAt = generatedAt;
    this.overall = aggregate(checks);
    this.checks = checks;
    this.redacted = redacted;
  }

  /** Creates a report with an injectable clock and deterministic check order. */
  public static DoctorReport of(Clock clock, List<DoctorCheck> checks) {
    Objects.requireNonNull(clock, "clock");
    return of(clock.instant(), checks);
  }

  /** Creates a report with an explicit UTC instant. */
  public static DoctorReport of(Instant generatedAt, List<DoctorCheck> checks) {
    Objects.requireNonNull(generatedAt, "generatedAt");
    if (checks == null || checks.isEmpty()) {
      throw new IllegalArgumentException("checks must not be empty");
    }
    List<DoctorCheck> copy = new ArrayList<DoctorCheck>(checks.size());
    for (DoctorCheck check : checks) {
      copy.add(Objects.requireNonNull(check, "check"));
    }
    return new DoctorReport(
        DateTimeFormatter.ISO_INSTANT.format(generatedAt),
        Collections.unmodifiableList(copy),
        false);
  }

  public String schemaVersion() {
    return SCHEMA_VERSION;
  }

  public String generatedAt() {
    return generatedAt;
  }

  public DoctorStatus overall() {
    return overall;
  }

  public String scope() {
    return SCOPE;
  }

  public List<DoctorCheck> checks() {
    return checks;
  }

  /**
   * Whether this instance is redacted. The transformation method keeps the {@code redacted()} name
   * required by the report contract; Java cannot overload it by return type.
   */
  public boolean isRedacted() {
    return redacted;
  }

  /** Returns a copy with redacted checks and no host-specific evidence. */
  public DoctorReport redacted() {
    if (redacted) {
      return this;
    }
    List<DoctorCheck> redactedChecks = new ArrayList<DoctorCheck>(checks.size());
    for (DoctorCheck check : checks) {
      redactedChecks.add(check.redacted());
    }
    return new DoctorReport(generatedAt, Collections.unmodifiableList(redactedChecks), true);
  }

  /** Renders the stable JSON representation with exactly the schema field names. */
  public String toJson() {
    StringBuilder json = new StringBuilder(1024);
    json.append('{');
    field(json, "schemaVersion").append(DoctorText.quote(schemaVersion())).append(',');
    field(json, "generatedAt").append(DoctorText.quote(generatedAt)).append(',');
    field(json, "overall").append(DoctorText.quote(overall.name())).append(',');
    field(json, "scope").append(DoctorText.quote(scope())).append(',');
    json.append("\"checks\":[");
    for (int i = 0; i < checks.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      appendCheck(json, checks.get(i));
    }
    json.append("],");
    field(json, "redacted").append(redacted);
    return json.append('}').toString();
  }

  /** Renders one line per check plus stored remediation lines and a localized honesty footer. */
  public List<String> renderHuman(Locale locale) {
    Locale effective = locale == null ? Locale.ROOT : locale;
    List<String> lines = new ArrayList<String>(checks.size() * 2 + 1);
    for (DoctorCheck check : checks) {
      lines.add("[" + check.status().name() + "] " + check.id() + ": " + check.detail());
      if (!check.remediation().isEmpty()) {
        lines.add("  " + check.remediation());
      }
    }
    lines.add(DoctorMessages.format(effective, DoctorMessages.FOOTER_NOT_SUPPORT_TIER));
    return Collections.unmodifiableList(lines);
  }

  private static void appendCheck(StringBuilder json, DoctorCheck check) {
    json.append('{');
    field(json, "id").append(DoctorText.quote(check.id())).append(',');
    field(json, "status").append(DoctorText.quote(check.status().name())).append(',');
    field(json, "implemented").append(check.implemented()).append(',');
    field(json, "detail").append(DoctorText.quote(check.detail())).append(',');
    field(json, "remediation").append(DoctorText.quote(check.remediation()));
    if (!check.evidence().isEmpty()) {
      json.append(',');
      field(json, "evidence").append('{');
      boolean first = true;
      for (Map.Entry<String, Object> entry : check.evidence().entrySet()) {
        if (!first) {
          json.append(',');
        }
        first = false;
        field(json, entry.getKey()).append(scalar(entry.getValue()));
      }
      json.append('}');
    }
    json.append('}');
  }

  private static String scalar(Object value) {
    if (value instanceof String) {
      return DoctorText.quote((String) value);
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
      return Integer.toString(((Number) value).intValue());
    }
    if (value instanceof Long) {
      return Long.toString(((Long) value).longValue());
    }
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue() ? "true" : "false";
    }
    throw new IllegalStateException("unsupported doctor evidence value");
  }

  private static StringBuilder field(StringBuilder json, String name) {
    return json.append(DoctorText.quote(name)).append(':');
  }

  private static DoctorStatus aggregate(List<DoctorCheck> checks) {
    DoctorStatus result = DoctorStatus.PASS;
    for (DoctorCheck check : checks) {
      result = result.moreSevere(check.status());
    }
    return result;
  }
}
