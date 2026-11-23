package io.github.woxlo1.deploymc.transport;

import java.util.Collections;
import java.util.Objects;

/** Stable Problem Details builders shared by the transport handlers. */
final class TransportProblems {

  static final String RATE_LIMITED_CODE = "auth.rate-limited";
  static final String SERVER_BUSY_CODE = "server.busy";
  static final String METRICS_FORBIDDEN_CODE = "metrics.forbidden";
  static final String METRICS_NOT_FOUND_CODE = "metrics.not-found";
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
