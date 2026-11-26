package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.AuthorizationDecision;
import io.github.woxlo1.deploymc.core.rbac.DecisionExplanation;
import io.github.woxlo1.deploymc.core.rbac.PermissionRegistry;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Authenticated {@code /api/v1/me} route with bounded, redacted explain mode. */
public final class MeHandler implements HttpHandler {

  static final String PATH = "/api/v1/me";
  private static final String SCHEMA_VERSION = "1.0";
  private static final String TOKEN_METHOD = "token";
  private static final String ONE_TIME_METHOD = "local-onetime";
  private static final String CLAIM_TOKEN_OWNER = "token.owner";
  private static final int MAX_QUERY_LENGTH = 8192;
  private static final int MAX_QUERY_PAIRS = 32;
  private static final int MAX_ROLES = 64;
  private static final int MAX_PERMISSIONS = PermissionRegistry.ALL.size();

  private final RbacEvaluator evaluator;
  private final TransportMessages messages;

  public MeHandler(RbacEvaluator evaluator, TransportMessages messages) {
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  @Override
  public HttpResponse handle(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    if (!PATH.equals(request.path())) {
      return HttpResponse.error(404, "not_found")
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    }
    if (!"GET".equals(request.method())) {
      return TransportProblems.problem(
              405,
              "request.method-not-allowed",
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_TITLE,
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_DETAIL,
              request.id(),
              messages,
              false)
          .withHeader("Allow", "GET");
    }
    AuthContext context = request.context();
    Identity identity = context.identity();
    if (identity == null) {
      return TransportProblems.problem(
          401,
          "auth.required",
          MessageKeys.AUTH_REQUIRED_TITLE,
          MessageKeys.AUTH_REQUIRED_DETAIL,
          context.requestId(),
          messages,
          false);
    }
    if (request.query().length() > MAX_QUERY_LENGTH) {
      return invalidRequest(request.id());
    }
    String explainRaw = queryValue(request.query(), "explain");
    String pathRaw = queryValue(request.query(), "path");
    String permission = explainRaw == null ? null : RbacGate.decodePath(explainRaw);
    String requestedPath = pathRaw == null ? null : RbacGate.decodePath(pathRaw);
    if (permission != null && !PermissionRegistry.isKnown(permission)) {
      return invalidRequest(request.id());
    }
    if (explainRaw != null && permission == null) {
      return invalidRequest(request.id());
    }
    if (pathRaw != null && requestedPath == null) {
      return invalidRequest(request.id());
    }
    if (pathRaw != null && permission == null) {
      return invalidRequest(request.id());
    }

    StringBuilder json = new StringBuilder();
    json.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION).append('"');
    json.append(",\"identity\":{\"id\":\"").append(JsonRequestLogger.escape(identity.id()));
    json.append("\",\"kind\":\"").append(kind(identity)).append("\"}");
    appendStrings(json, "roles", roles(identity), MAX_ROLES);
    appendStrings(json, "permissions", evaluator.effective(identity), MAX_PERMISSIONS);
    if (permission != null) {
      appendExplanation(json, evaluator, identity, permission, requestedPath);
    }
    json.append('}');
    return HttpResponse.json(200, json.toString())
        .withHeader(TransportProblems.REQUEST_ID, context.requestId());
  }

  private HttpResponse invalidRequest(String requestId) {
    return TransportProblems.problem(
        400,
        "request.invalid",
        MessageKeys.REQUEST_INVALID_TITLE,
        MessageKeys.REQUEST_INVALID_DETAIL,
        requestId,
        messages,
        false);
  }

  private List<String> roles(Identity identity) {
    RbacPolicy policy = evaluator.policy();
    List<String> assigned = policy.rolesFor(lookupIdentity(identity));
    Set<String> unique = new TreeSet<String>();
    for (String role : assigned) {
      if (role != null && !role.isEmpty()) {
        unique.add(role);
      }
    }
    List<String> result = new ArrayList<String>(unique);
    if (result.size() > MAX_ROLES) {
      return Collections.unmodifiableList(result.subList(0, MAX_ROLES));
    }
    return Collections.unmodifiableList(result);
  }

  private static String lookupIdentity(Identity identity) {
    if (TOKEN_METHOD.equals(identity.method())) {
      return identity.claims().get(CLAIM_TOKEN_OWNER);
    }
    return identity.id();
  }

  private static String kind(Identity identity) {
    String method = identity.method();
    if (TOKEN_METHOD.equals(method)) {
      return TOKEN_METHOD;
    }
    if (ONE_TIME_METHOD.equals(method)) {
      return ONE_TIME_METHOD;
    }
    return "cloudflare";
  }

  private void appendExplanation(
      StringBuilder json,
      RbacEvaluator evaluator,
      Identity identity,
      String permission,
      String requestedPath) {
    AuthorizationDecision decision =
        requestedPath == null
            ? evaluator.authorize(identity, permission, null)
            : evaluator.authorize(identity, permission, requestedPath, SandboxAccessMode.READ);
    DecisionExplanation explanation = decision.explanation();
    json.append(",\"explain\":{");
    json.append("\"permission\":\"").append(JsonRequestLogger.escape(permission)).append('"');
    json.append(",\"decision\":\"").append(decision.allowed() ? "allow" : "deny").append('"');
    json.append(",\"reason\":");
    if (decision.reason() == null) {
      json.append("null");
    } else {
      json.append('"').append(JsonRequestLogger.escape(decision.reason().name())).append('"');
    }
    json.append(",\"path\":{");
    json.append("\"requested\":").append(explanation.pathRequested());
    json.append(",\"resolved\":").append(explanation.pathResolved());
    json.append(",\"withinScope\":").append(explanation.pathWithinScope());
    json.append("},\"roles\":{");
    json.append("\"assigned\":").append(explanation.rolesAssigned());
    json.append(",\"granting\":").append(explanation.rolesGranting());
    json.append(",\"denyRuleMatches\":").append(explanation.rolesDenyRuleMatches());
    json.append(",\"allowRuleMatches\":").append(explanation.rolesAllowRuleMatches());
    json.append('}');
    boolean token = TOKEN_METHOD.equals(identity.method());
    if (token) {
      json.append(",\"token\":{");
      json.append("\"present\":").append(explanation.tokenPresent());
      json.append(",\"containsPermission\":").append(explanation.tokenContainsPermission());
      json.append('}');
    }
    json.append('}');
  }

  private static void appendStrings(
      StringBuilder json, String name, List<String> values, int maximum) {
    json.append(",\"").append(name).append("\":[");
    int count = Math.min(values.size(), maximum);
    for (int index = 0; index < count; index++) {
      if (index > 0) {
        json.append(',');
      }
      json.append('"').append(JsonRequestLogger.escape(values.get(index))).append('"');
    }
    json.append(']');
  }

  private static String queryValue(String raw, String key) {
    if (raw == null || raw.isEmpty() || raw.length() > MAX_QUERY_LENGTH) {
      return null;
    }
    int pairs = 0;
    for (String pair : raw.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      if (++pairs > MAX_QUERY_PAIRS) {
        return null;
      }
      int separator = pair.indexOf('=');
      String name = separator < 0 ? pair : pair.substring(0, separator);
      if (!key.equals(name)) {
        continue;
      }
      return separator < 0 ? "" : pair.substring(separator + 1);
    }
    return null;
  }
}
