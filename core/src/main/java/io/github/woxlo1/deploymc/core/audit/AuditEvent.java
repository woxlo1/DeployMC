package io.github.woxlo1.deploymc.core.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable input for one audit record before the writer assigns sequence and hash. */
public final class AuditEvent {

  private final Instant timestamp;
  private final AuditActor actor;
  private final String action;
  private final AuditTarget target;
  private final AuditResult result;
  private final String reason;
  private final String requestId;
  private final String deploymentId;
  private final Map<String, Object> details;

  private AuditEvent(Builder builder) {
    this.timestamp = builder.timestamp;
    this.actor = builder.actor;
    this.action = builder.action;
    this.target = builder.target;
    this.result = builder.result;
    this.reason = builder.reason;
    this.requestId = builder.requestId;
    this.deploymentId = builder.deploymentId;
    this.details = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(builder.details));
  }

  public static Builder builder() {
    return new Builder();
  }

  public Instant timestamp() {
    return timestamp;
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
    return reason;
  }

  public String requestId() {
    return requestId;
  }

  public String deploymentId() {
    return deploymentId;
  }

  public Map<String, Object> details() {
    return details;
  }

  /** Builder that validates every field before a writer can hash the event. */
  public static final class Builder {

    private Instant timestamp;
    private AuditActor actor;
    private String action;
    private AuditTarget target;
    private AuditResult result;
    private String reason;
    private String requestId;
    private String deploymentId;
    private Map<String, Object> details = Collections.<String, Object>emptyMap();

    public Builder timestamp(Instant value) {
      this.timestamp = value;
      return this;
    }

    public Builder actor(AuditActor value) {
      this.actor = value;
      return this;
    }

    public Builder action(String value) {
      this.action = value;
      return this;
    }

    public Builder target(AuditTarget value) {
      this.target = value;
      return this;
    }

    public Builder result(AuditResult value) {
      this.result = value;
      return this;
    }

    public Builder reason(String value) {
      this.reason = bounded(value, 256, "reason");
      return this;
    }

    public Builder requestId(String value) {
      this.requestId = bounded(value, 128, "requestId");
      return this;
    }

    public Builder deploymentId(String value) {
      this.deploymentId = bounded(value, 256, "deploymentId");
      return this;
    }

    public Builder details(Map<String, Object> value) {
      this.details =
          value == null
              ? Collections.<String, Object>emptyMap()
              : new LinkedHashMap<String, Object>(value);
      return this;
    }

    public AuditEvent build() {
      if (actor == null) {
        throw new IllegalArgumentException("audit actor is required");
      }
      if (!AuditActions.valid(action)) {
        throw new IllegalArgumentException("invalid audit action");
      }
      if (result == null) {
        throw new IllegalArgumentException("audit result is required");
      }
      return new AuditEvent(this);
    }

    private static String bounded(String value, int maximum, String name) {
      if (value == null || value.length() <= maximum) {
        return value;
      }
      throw new IllegalArgumentException("audit " + name + " exceeds " + maximum + " characters");
    }
  }

  /** Returns a copy of this event with a different result and reason. */
  public AuditEvent withResult(AuditResult newResult, String newReason) {
    Builder copy =
        builder()
            .timestamp(timestamp)
            .actor(actor)
            .action(action)
            .target(target)
            .result(newResult)
            .reason(newReason)
            .requestId(requestId)
            .deploymentId(deploymentId)
            .details(new LinkedHashMap<String, Object>(details));
    return copy.build();
  }

  /** Keys used in the access-record filter summary. */
  public static List<String> filterKeys() {
    List<String> keys = new ArrayList<String>();
    Collections.addAll(
        keys,
        "from",
        "to",
        "actor",
        "action",
        "result",
        "deployment-id",
        "path",
        "cursor",
        "limit");
    return Collections.unmodifiableList(keys);
  }
}
