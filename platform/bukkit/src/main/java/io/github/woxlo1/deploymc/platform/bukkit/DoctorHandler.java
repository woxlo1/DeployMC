package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.doctor.DoctorReport;
import io.github.woxlo1.deploymc.transport.HttpHandler;
import io.github.woxlo1.deploymc.transport.HttpRequest;
import io.github.woxlo1.deploymc.transport.HttpResponse;
import java.util.Objects;

/** Authenticated host route for the redacted PART 36 doctor report. */
public final class DoctorHandler implements HttpHandler {

  public static final String PATH = "/api/v1/server/doctor";
  private static final String REQUEST_ID = "X-Request-Id";
  private static final String NO_STORE = "no-store";

  private final DoctorRuntime runtime;
  private final LocalizedMessages messages;

  public DoctorHandler(DoctorRuntime runtime, LocalizedMessages messages) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  @Override
  public HttpResponse handle(HttpRequest request) {
    Objects.requireNonNull(request, "request");
    if (!PATH.equals(request.path())) {
      return common(HttpResponse.error(404, "not_found"), request.id());
    }
    if (!"GET".equals(request.method())) {
      return common(
          HttpResponse.error(405, "method_not_allowed").withHeader("Allow", "GET"), request.id());
    }
    Identity identity = request.identity();
    if (identity == null) {
      return common(
          problem(
              401,
              "auth.required",
              MessageKeys.PROBLEM_DOCTOR_UNAUTHORIZED_TITLE,
              MessageKeys.PROBLEM_DOCTOR_UNAUTHORIZED_DETAIL,
              request.id(),
              true),
          request.id());
    }
    if (!identity.adminEquivalent()) {
      return common(
          problem(
              403,
              "doctor.forbidden",
              MessageKeys.PROBLEM_DOCTOR_FORBIDDEN_TITLE,
              MessageKeys.PROBLEM_DOCTOR_FORBIDDEN_DETAIL,
              request.id(),
              false),
          request.id());
    }
    DoctorReport report = runtime.run().redacted();
    return common(HttpResponse.json(200, report.toJson()), request.id());
  }

  private HttpResponse problem(
      int status,
      String code,
      String titleKey,
      String detailKey,
      String requestId,
      boolean bearer) {
    HttpResponse response =
        HttpResponse.problem(
            status, code, messages.format(titleKey), messages.format(detailKey), requestId);
    if (bearer) {
      response = response.withHeader("WWW-Authenticate", "Bearer");
    }
    return response;
  }

  private static HttpResponse common(HttpResponse response, String requestId) {
    return response.withHeader(REQUEST_ID, requestId).withHeader("Cache-Control", NO_STORE);
  }
}
