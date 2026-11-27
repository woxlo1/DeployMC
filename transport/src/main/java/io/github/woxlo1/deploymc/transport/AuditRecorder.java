package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.audit.AuditActor;
import io.github.woxlo1.deploymc.core.audit.AuditEvent;
import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.audit.AuditTarget;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Host-wired audit seam shared by REST handlers, mutations, and transport denials. */
public final class AuditRecorder {

  private final AuditStore store;
  private final RbacEvaluator evaluator;

  private AuditRecorder(AuditStore store, RbacEvaluator evaluator) {
    this.store = store;
    this.evaluator = evaluator;
  }

  /** Creates a recorder over an already-opened audit store. */
  public static AuditRecorder of(AuditStore store, RbacEvaluator evaluator) {
    return new AuditRecorder(
        Objects.requireNonNull(store, "store"), Objects.requireNonNull(evaluator, "evaluator"));
  }

  /** Explicit seam for server-free adapter tests; never used by product host wiring. */
  static AuditRecorder disabledForServerFreeTests() {
    return new AuditRecorder(null, null);
  }

  /**
   * Wraps a privileged HTTP handler so pending and final records surround the mutation.
   *
   * <p>Host wiring always supplies a real store, so an audit write failure blocks the mutation.
   */
  public HttpHandler wrapMutations(HttpHandler delegate) {
    Objects.requireNonNull(delegate, "delegate");
    if (store == null) {
      return delegate;
    }
    return new AuditMutationHandler(delegate, this);
  }

  /** Verified identity information for one authenticated request. */
  public AuditActor actor(HttpRequest request) {
    if (store == null) {
      return AuditActor.system();
    }
    return AuditActor.fromIdentity(request.identity(), evaluator, null);
  }

  /** Commits a mandatory pending record before a privileged state change. */
  public AuditRecord begin(
      HttpRequest request, String action, AuditTarget target, Map<String, Object> details)
      throws AuditException {
    if (store == null) {
      return null;
    }
    return store.begin(
        AuditEvent.builder()
            .timestamp(java.time.Clock.systemUTC().instant())
            .actor(actor(request))
            .action(action)
            .target(target)
            .result(AuditResult.PENDING)
            .requestId(request.id())
            .details(details == null ? Collections.<String, Object>emptyMap() : details)
            .build());
  }

  /** Commits the final record after a privileged state change. */
  public void complete(AuditRecord pending, AuditResult result, String reason)
      throws AuditException {
    complete(pending, result, reason, null);
  }

  /** Commits a final record with additional outcome details merged over the pending details. */
  public void complete(
      AuditRecord pending, AuditResult result, String reason, Map<String, Object> finalDetails)
      throws AuditException {
    if (store == null || pending == null) {
      return;
    }
    store.complete(pending, result, reason, finalDetails);
  }

  /** Begins a host-initiated mutation whose actor is the local system. */
  public AuditRecord beginSystem(String action, AuditTarget target, Map<String, Object> details)
      throws AuditException {
    if (store == null) {
      return null;
    }
    return store.begin(
        AuditEvent.builder()
            .timestamp(java.time.Clock.systemUTC().instant())
            .actor(AuditActor.system())
            .action(action)
            .target(target)
            .result(AuditResult.PENDING)
            .details(details == null ? Collections.<String, Object>emptyMap() : details)
            .build());
  }

  /** Records an audit-route access after response work, without returned row bodies. */
  public void access(HttpRequest request, AuditQuery query, int rowCount) throws AuditException {
    if (store == null) {
      return;
    }
    store.recordAccess(
        actor(request), request.id(), "audit.read", AuditResult.SUCCESS.wire(), query, rowCount);
  }

  /** Records a denied attempt with a closed decision code and no host path. */
  public void denial(
      HttpRequest request, Identity identity, String action, AuditTarget target, String reasonCode)
      throws AuditException {
    if (store == null) {
      return;
    }
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("reason-code", reasonCode == null ? "rbac.denied" : reasonCode);
    AuditActor actor =
        identity == null ? AuditActor.system() : AuditActor.fromIdentity(identity, evaluator, null);
    store.append(
        AuditEvent.builder()
            .timestamp(java.time.Clock.systemUTC().instant())
            .actor(actor)
            .action(action)
            .target(target)
            .result(AuditResult.DENIED)
            .requestId(request == null ? null : request.id())
            .details(details)
            .build());
  }

  /** Records a host-initiated system event such as an automatic snapshot. */
  public void systemEvent(String action, AuditTarget target, Map<String, Object> details)
      throws AuditException {
    if (store == null) {
      return;
    }
    store.append(
        AuditEvent.builder()
            .timestamp(java.time.Clock.systemUTC().instant())
            .actor(AuditActor.system())
            .action(action)
            .target(target)
            .result(AuditResult.SUCCESS)
            .details(details == null ? Collections.<String, Object>emptyMap() : details)
            .build());
  }
}
