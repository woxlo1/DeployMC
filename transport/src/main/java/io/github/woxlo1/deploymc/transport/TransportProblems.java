package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.auth.AuthorizationDeniedException;
import java.util.Collections;
import java.util.Objects;

/** Stable Problem Details builders shared by the transport handlers. */
final class TransportProblems {

  static final String RATE_LIMITED_CODE = "auth.rate-limited";
  static final String LOCKED_OUT_CODE = "auth.locked-out";
  static final String SERVER_BUSY_CODE = "server.busy";
  static final String METRICS_FORBIDDEN_CODE = "metrics.forbidden";
  static final String AUTHORIZATION_DENIED_CODE = AuthorizationDeniedException.DEFAULT_CODE;
  static final String TOKEN_SCOPE_EXCEEDED_CODE = "rbac.token-scope-exceeded";
  static final String PATH_DENIED_CODE = "rbac.path-denied";
  static final String METRICS_NOT_FOUND_CODE = "metrics.not-found";
  static final String AUDIT_UNAVAILABLE_CODE = AuditException.Code.UNAVAILABLE.problemCode();
  static final String AUDIT_WRITE_FAILED_CODE = AuditException.Code.WRITE_FAILED.problemCode();
  static final String AUDIT_EXPORT_LIMIT_CODE = AuditException.Code.EXPORT_LIMIT.problemCode();
  static final String REQUEST_INVALID_CODE = "request.invalid";
  static final String REQUEST_ORIGIN_REJECTED_CODE = "request.origin-rejected";
  static final String REQUEST_TOO_LARGE_CODE = "request.too-large";
  static final String REQUEST_INVALID_CURSOR_CODE = "request.invalid-cursor";
  static final String RETRY_AFTER = "Retry-After";
  static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
  static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
  static final String RATE_LIMIT_RESET = "RateLimit-Reset";
  static final String REQUEST_ID = "X-Request-Id";

  private TransportProblems() {}

  static HttpResponse rateLimited(
      RateLimitDecision decision, String requestId, TransportMessages messages) {
    Objects.requireNonNull(decision, "decision");
    HttpResponse response =
        problem(
            429,
            RATE_LIMITED_CODE,
            MessageKeys.AUTH_RATE_LIMITED_TITLE,
            MessageKeys.AUTH_RATE_LIMITED_DETAIL,
            requestId,
            messages,
            true);
    return response
        .withHeader(RETRY_AFTER, Long.toString(decision.retryAfterSeconds()))
        .withHeader(RATE_LIMIT_LIMIT, Integer.toString(decision.limit()))
        .withHeader(RATE_LIMIT_REMAINING, Integer.toString(decision.remaining()))
        .withHeader(RATE_LIMIT_RESET, Long.toString(decision.resetEpochSeconds()));
  }

  static HttpResponse lockedOut(
      String requestId, long retryAfterSeconds, TransportMessages messages) {
    HttpResponse response =
        problem(
            429,
            LOCKED_OUT_CODE,
            MessageKeys.AUTH_LOCKED_OUT_TITLE,
            MessageKeys.AUTH_LOCKED_OUT_DETAIL,
            requestId,
            messages,
            true);
    return response.withHeader(RETRY_AFTER, Long.toString(Math.max(1L, retryAfterSeconds)));
  }

  static HttpResponse originRejected(String requestId, TransportMessages messages) {
    return problem(
        403,
        REQUEST_ORIGIN_REJECTED_CODE,
        MessageKeys.REQUEST_ORIGIN_REJECTED_TITLE,
        MessageKeys.REQUEST_ORIGIN_REJECTED_DETAIL,
        requestId,
        messages,
        false);
  }

  static HttpResponse tooLarge(String requestId, TransportMessages messages) {
    return problem(
        413,
        REQUEST_TOO_LARGE_CODE,
        MessageKeys.REQUEST_TOO_LARGE_TITLE,
        MessageKeys.REQUEST_TOO_LARGE_DETAIL,
        requestId,
        messages,
        false);
  }

  static HttpResponse serverBusy(String requestId, TransportMessages messages) {
    HttpResponse response =
        problem(
            503,
            SERVER_BUSY_CODE,
            MessageKeys.SERVER_BUSY_TITLE,
            MessageKeys.SERVER_BUSY_DETAIL,
            requestId,
            messages,
            true);
    return response.withHeader(RETRY_AFTER, "1");
  }

  static HttpResponse metricsForbidden(String requestId, TransportMessages messages) {
    return problem(
        403,
        METRICS_FORBIDDEN_CODE,
        MessageKeys.METRICS_FORBIDDEN_TITLE,
        MessageKeys.METRICS_FORBIDDEN_DETAIL,
        requestId,
        messages,
        false);
  }

  /** Uniform 403 for authorization denials. No permission, path, or identity detail is included. */
  static HttpResponse authorizationDenied(String requestId, TransportMessages messages) {
    return authorizationDenied(requestId, AUTHORIZATION_DENIED_CODE, messages);
  }

  /** 403 Problem Details carrying only a stable RBAC decision code. */
  static HttpResponse authorizationDenied(
      String requestId, String decisionCode, TransportMessages messages) {
    String code = decisionCode == null ? AUTHORIZATION_DENIED_CODE : decisionCode;
    String titleKey;
    String detailKey;
    if (TOKEN_SCOPE_EXCEEDED_CODE.equals(code)) {
      titleKey = MessageKeys.RBAC_TOKEN_SCOPE_EXCEEDED_TITLE;
      detailKey = MessageKeys.RBAC_TOKEN_SCOPE_EXCEEDED_DETAIL;
    } else if (PATH_DENIED_CODE.equals(code)) {
      titleKey = MessageKeys.RBAC_PATH_DENIED_TITLE;
      detailKey = MessageKeys.RBAC_PATH_DENIED_DETAIL;
    } else {
      code = AUTHORIZATION_DENIED_CODE;
      titleKey = MessageKeys.AUTH_DENIED_TITLE;
      detailKey = MessageKeys.AUTH_DENIED_DETAIL;
    }
    return problem(403, code, titleKey, detailKey, requestId, messages, false);
  }

  static HttpResponse metricsNotFound(String requestId, TransportMessages messages) {
    return problem(
        404,
        METRICS_NOT_FOUND_CODE,
        MessageKeys.METRICS_NOT_FOUND_TITLE,
        MessageKeys.METRICS_NOT_FOUND_DETAIL,
        requestId,
        messages,
        false);
  }

  /** Maps a fail-closed core audit exception to its catalogued Problem Details. */
  static HttpResponse auditProblem(
      AuditException failure, String requestId, TransportMessages messages) {
    Objects.requireNonNull(failure, "failure");
    switch (failure.code()) {
      case UNAVAILABLE:
        return problem(
            503,
            AUDIT_UNAVAILABLE_CODE,
            MessageKeys.AUDIT_UNAVAILABLE_TITLE,
            MessageKeys.AUDIT_UNAVAILABLE_DETAIL,
            requestId,
            messages,
            true);
      case WRITE_FAILED:
        return problem(
            503,
            AUDIT_WRITE_FAILED_CODE,
            MessageKeys.AUDIT_WRITE_FAILED_TITLE,
            MessageKeys.AUDIT_WRITE_FAILED_DETAIL,
            requestId,
            messages,
            true);
      case EXPORT_LIMIT:
        return problem(
            413,
            AUDIT_EXPORT_LIMIT_CODE,
            MessageKeys.AUDIT_EXPORT_LIMIT_TITLE,
            MessageKeys.AUDIT_EXPORT_LIMIT_DETAIL,
            requestId,
            messages,
            false);
      case INVALID_CURSOR:
        return problem(
            400,
            REQUEST_INVALID_CURSOR_CODE,
            MessageKeys.REQUEST_INVALID_CURSOR_TITLE,
            MessageKeys.REQUEST_INVALID_CURSOR_DETAIL,
            requestId,
            messages,
            false);
      case INVALID:
      default:
        return problem(
            400,
            REQUEST_INVALID_CODE,
            MessageKeys.REQUEST_INVALID_TITLE,
            MessageKeys.REQUEST_INVALID_DETAIL,
            requestId,
            messages,
            false);
    }
  }

  static HttpResponse problem(
      int status,
      String code,
      String titleKey,
      String detailKey,
      String requestId,
      TransportMessages messages,
      boolean retryable) {
    Objects.requireNonNull(messages, "messages");
    return HttpResponse.problem(
            status,
            code,
            messages.format(titleKey),
            messages.format(detailKey),
            requestId,
            retryable,
            Collections.<HttpResponse.ProblemIssue>emptyList())
        .withHeader(REQUEST_ID, requestId);
  }
}
