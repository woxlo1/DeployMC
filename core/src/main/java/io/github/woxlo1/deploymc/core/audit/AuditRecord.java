package io.github.woxlo1.deploymc.core.audit;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable audit record with its original wire line retained for verbatim export. */
public final class AuditRecord {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final Map<String, Object> values;
  private final String canonicalJson;
  private final String rawLine;
  private final String fileName;
  private final int lineNumber;
  private final long seq;
  private final Instant timestamp;
  private final String hash;
  private final String previousHash;
  private final AuditActor actor;
  private final String action;
  private final AuditTarget target;
  private final AuditResult result;

  private AuditRecord(
      Map<String, Object> values,
      String canonicalJson,
      String rawLine,
      String fileName,
      int lineNumber) {
    this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    this.canonicalJson = canonicalJson;
    this.rawLine = rawLine;
    this.fileName = fileName;
    this.lineNumber = lineNumber;
    this.seq = (Long) values.get("seq");
    this.hash = (String) values.get("hash");
    this.previousHash = (String) values.get("prev");
    this.actor = AuditActor.fromJson(values.get("actor"));
    this.action = (String) values.get("action");
    this.target = values.get("target") == null ? null : AuditTarget.fromJson(values.get("target"));
    this.result = AuditResult.parse((String) values.get("result"));
    try {
      this.timestamp = Instant.parse((String) values.get("ts"));
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("invalid audit timestamp");
    }
  }

  /** Creates the next chained record. The caller supplies sequence and predecessor hash. */
  static AuditRecord create(AuditEvent event, Instant timestamp, long seq, String previousHash) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("v", Long.valueOf(1L));
    values.put("seq", Long.valueOf(seq));
    values.put("ts", formatTimestamp(timestamp));
    values.put("prev", previousHash);
    values.put("actor", event.actor().toJsonObject());
    values.put("action", event.action());
    values.put("target", event.target() == null ? null : event.target().toJsonObject());
    values.put("result", event.result().wire());
    values.put("reason", event.reason());
    values.put("request-id", event.requestId());
    values.put("deployment-id", event.deploymentId());
    values.put("details", AuditDetails.validate(event.details()));
    String hash = AuditHash.hash(AuditJson.canonical(values));
    values.put("hash", hash);
    String canonical = AuditJson.canonical(values);
    return new AuditRecord(values, canonical, canonical, null, 0);
  }

  /** Parses one line of an existing audit file. */
  public static AuditRecord parse(String rawLine, String fileName, int lineNumber) {
    Map<String, Object> values;
    try {
      values = AuditJson.parseObject(rawLine);
    } catch (IllegalArgumentException invalid) {
      throw invalid;
    }
    if (!Long.valueOf(1L).equals(values.get("v"))) {
      throw new IllegalArgumentException("unsupported audit record version");
    }
    if (!(values.get("seq") instanceof Long) || ((Long) values.get("seq")).longValue() < 1L) {
      throw new IllegalArgumentException("invalid audit sequence");
    }
    requireString(values, "ts", 64);
    if (!AuditHash.valid((String) values.get("prev"))
        || !AuditHash.valid((String) values.get("hash"))) {
      throw new IllegalArgumentException("invalid audit hash");
    }
    requireString(values, "action", 128);
    if (!AuditActions.valid((String) values.get("action"))) {
      throw new IllegalArgumentException("invalid audit action");
    }
    if (AuditResult.parse((String) values.get("result")) == null) {
      throw new IllegalArgumentException("invalid audit result");
    }
    requireNullableString(values, "reason", 256);
    requireNullableString(values, "request-id", 128);
    requireNullableString(values, "deployment-id", 256);
    if (!(values.get("details") instanceof Map)) {
      throw new IllegalArgumentException("invalid audit details");
    }
    if (values.get("target") != null && !(values.get("target") instanceof Map)) {
      throw new IllegalArgumentException("invalid audit target");
    }
    return new AuditRecord(values, AuditJson.canonical(values), rawLine, fileName, lineNumber);
  }

  public long seq() {
    return seq;
  }

  public Instant timestamp() {
    return timestamp;
  }

  public String hash() {
    return hash;
  }

  public String previousHash() {
    return previousHash;
  }

  public AuditActor actor() {
    return actor;
  }

  public String action() {
    return action;
  }

  public AuditTarget target() {
    return target;
  }

  public AuditResult result() {
    return result;
  }

  public String reason() {
    return (String) values.get("reason");
  }

  public String requestId() {
    return (String) values.get("request-id");
  }

  public String deploymentId() {
    return (String) values.get("deployment-id");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> details() {
    return Collections.unmodifiableMap((Map<String, Object>) values.get("details"));
  }

  public Map<String, Object> values() {
    return values;
  }

  /** Canonical object text for REST responses; writer-generated lines are already canonical. */
  public String canonicalJson() {
    return canonicalJson;
  }

  /** Original line for verbatim export and hash verification. */
  public String rawLine() {
    return rawLine == null ? canonicalJson : rawLine;
  }

  public String fileName() {
    return fileName;
  }

  public int lineNumber() {
    return lineNumber;
  }

  /** Recomputes the chain hash from every parsed field except {@code hash}. */
  public String computedHash() {
    Map<String, Object> withoutHash = new LinkedHashMap<String, Object>(values);
    withoutHash.remove("hash");
    return AuditHash.hash(AuditJson.canonical(withoutHash));
  }

  public static String formatTimestamp(Instant timestamp) {
    return TIMESTAMP.format(timestamp);
  }

  private static void requireString(Map<String, Object> values, String key, int maximum) {
    Object value = values.get(key);
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("invalid audit " + key);
    }
    String text = (String) value;
    if (text.isEmpty() || text.length() > maximum) {
      throw new IllegalArgumentException("invalid audit " + key);
    }
  }

  private static void requireNullableString(Map<String, Object> values, String key, int maximum) {
    Object value = values.get(key);
    if (value != null && (!(value instanceof String) || ((String) value).length() > maximum)) {
      throw new IllegalArgumentException("invalid audit " + key);
    }
  }
}
