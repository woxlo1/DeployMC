package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditTarget;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fail-closed audit wrapper for privileged HTTP mutations.
 *
 * <p>The pending record is committed before the delegate runs. The final record is committed even
 * when the delegate throws or returns an error, and an audit failure replaces the delegate result
 * with a redacted Problem Details response.
 */
final class AuditMutationHandler implements HttpHandler {

  private static final String FILES_PREFIX = "/api/v1/files";
  private static final String SNAPSHOTS_PREFIX = "/api/v1/snapshots";
  private static final int MAX_PATH_LENGTH = 1024;
  private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

  private final HttpHandler delegate;
  private final AuditRecorder audit;

  AuditMutationHandler(HttpHandler delegate, AuditRecorder audit) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.audit = Objects.requireNonNull(audit, "audit");
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    Objects.requireNonNull(request, "request");
    Plan plan = plan(request);
    if (plan == null) {
      return delegate.handle(request);
    }
    TransportMessages messages =
        TransportMessages.of(
            request.header("accept-language") != null
                    && request.header("accept-language").toLowerCase(Locale.ROOT).contains("ja")
                ? Locale.JAPANESE
                : Locale.ENGLISH);
    AuditRecord pending;
    try {
      pending = audit.begin(request, plan.action, plan.target, plan.details);
    } catch (AuditException failure) {
      return TransportProblems.auditProblem(failure, request.id(), messages)
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    }
    HttpResponse response;
    try {
      response = delegate.handle(request);
    } catch (Exception failure) {
      try {
        audit.complete(pending, AuditResult.FAILED, "handler-exception");
      } catch (AuditException auditFailure) {
        return TransportProblems.auditProblem(auditFailure, request.id(), messages)
            .withHeader(TransportProblems.REQUEST_ID, request.id());
      }
      throw failure;
    }
    if (response == null) {
      audit.complete(pending, AuditResult.FAILED, "empty-response");
      return response;
    }
    AuditResult result = result(response.status());
    String reason = reason(response.status());
    try {
      audit.complete(pending, result, reason);
    } catch (AuditException failure) {
      return TransportProblems.auditProblem(failure, request.id(), messages)
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    }
    return response;
  }

  private static AuditResult result(int status) {
    if (status >= 200 && status < 400) {
      return AuditResult.SUCCESS;
    }
    if (status == 403) {
      return AuditResult.DENIED;
    }
    return AuditResult.FAILED;
  }

  private static String reason(int status) {
    if (status >= 200 && status < 400) {
      return null;
    }
    if (status == 403) {
      return "authorization-denied";
    }
    if (status >= 500) {
      return "server-error";
    }
    return "request-rejected";
  }

  private static Plan plan(HttpRequest request) {
    String method = request.method().toUpperCase(Locale.ROOT);
    String path = request.path();
    if (FILES_PREFIX.equals(path)) {
      if ("POST".equals(method)) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        String target = bodyString(request, "path");
        String kind = bodyString(request, "kind");
        if (kind != null) {
          details.put("kind", kind);
        }
        return new Plan(AuditActions.FILES_WRITE, safePath(target), details);
      }
      if ("DELETE".equals(method)) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        String revision = request.header("if-match");
        if (revision != null) {
          details.put("expected-revision", stripEtag(revision));
        }
        putBoolean(details, "recursive", queryBoolean(request, "recursive"));
        return new Plan(AuditActions.FILES_DELETE, safePath(queryString(request, "path")), details);
      }
      return null;
    }
    if ((FILES_PREFIX + "/content").equals(path) && "PUT".equals(method)) {
      Map<String, Object> details = new LinkedHashMap<String, Object>();
      String revision = request.header("if-match");
      if (revision != null) {
        details.put("expected-revision", stripEtag(revision));
      }
      return new Plan(AuditActions.FILES_WRITE, safePath(queryString(request, "path")), details);
    }
    if ((FILES_PREFIX + "/upload").equals(path) && "POST".equals(method)) {
      return new Plan(
          AuditActions.FILES_UPLOAD,
          safePath(queryString(request, "path")),
          new LinkedHashMap<String, Object>());
    }
    if ((FILES_PREFIX + "/move").equals(path) && "POST".equals(method)) {
      Map<String, Object> details = new LinkedHashMap<String, Object>();
      String from = bodyString(request, "from");
      String to = bodyString(request, "to");
      if (from != null) {
        details.put("previous-path", from);
      }
      putBoolean(details, "overwrite", bodyBoolean(request, "overwrite"));
      return new Plan(AuditActions.FILES_RENAME, safePath(to), details);
    }
    if (SNAPSHOTS_PREFIX.equals(path) && "POST".equals(method)) {
      return new Plan(
          AuditActions.SNAPSHOTS_CREATE,
          safePath(queryString(request, "path")),
          new LinkedHashMap<String, Object>());
    }
    if ((SNAPSHOTS_PREFIX + "/prune").equals(path) && "POST".equals(method)) {
      Map<String, Object> details = new LinkedHashMap<String, Object>();
      putLong(details, "retain", bodyLong(request, "retain"));
      return new Plan(AuditActions.SNAPSHOTS_PRUNE, null, details);
    }
    if (path.startsWith(SNAPSHOTS_PREFIX + "/")
        && path.endsWith("/restore")
        && "POST".equals(method)) {
      String id =
          path.substring(SNAPSHOTS_PREFIX.length() + 1, path.length() - "/restore".length());
      Map<String, Object> details = new LinkedHashMap<String, Object>();
      if (!id.isEmpty()) {
        details.put("snapshot-id", id);
      }
      return new Plan(AuditActions.SNAPSHOTS_RESTORE, null, details);
    }
    return null;
  }

  private static String bodyString(HttpRequest request, String field) {
    try {
      io.github.woxlo1.deploymc.core.config.YamlValue root =
          RequestJson.parse(request.body(), MAX_BODY_BYTES);
      return RequestJson.optionalString(root, field, MAX_PATH_LENGTH);
    } catch (RequestProblem | RuntimeException invalid) {
      return null;
    }
  }

  private static Boolean bodyBoolean(HttpRequest request, String field) {
    try {
      io.github.woxlo1.deploymc.core.config.YamlValue root =
          RequestJson.parse(request.body(), MAX_BODY_BYTES);
      return Boolean.valueOf(RequestJson.optionalBoolean(root, field, false));
    } catch (RequestProblem | RuntimeException invalid) {
      return null;
    }
  }

  private static Long bodyLong(HttpRequest request, String field) {
    try {
      io.github.woxlo1.deploymc.core.config.YamlValue root =
          RequestJson.parse(request.body(), MAX_BODY_BYTES);
      int value = RequestJson.optionalInteger(root, field, 1, Integer.MAX_VALUE, 1);
      return Long.valueOf(value);
    } catch (RequestProblem | RuntimeException invalid) {
      return null;
    }
  }

  private static String queryString(HttpRequest request, String name) {
    String raw = request.query();
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    for (String pair : raw.split("&", -1)) {
      int separator = pair.indexOf('=');
      if (separator < 0) {
        continue;
      }
      if (name.equals(pair.substring(0, separator))) {
        return RbacGate.decodePath(pair.substring(separator + 1));
      }
    }
    return null;
  }

  private static Boolean queryBoolean(HttpRequest request, String name) {
    String value = queryString(request, name);
    return value == null ? null : Boolean.valueOf("true".equalsIgnoreCase(value));
  }

  private static String stripEtag(String header) {
    String value = header.trim();
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static AuditTarget safePath(String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    try {
      return AuditTarget.path(path);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static void putBoolean(Map<String, Object> details, String key, Boolean value) {
    if (value != null) {
      details.put(key, value);
    }
  }

  private static void putLong(Map<String, Object> details, String key, Long value) {
    if (value != null) {
      details.put(key, value);
    }
  }

  private static final class Plan {

    private final String action;
    private final AuditTarget target;
    private final Map<String, Object> details;

    private Plan(String action, AuditTarget target, Map<String, Object> details) {
      this.action = action;
      this.target = target;
      this.details = details;
    }
  }
}
