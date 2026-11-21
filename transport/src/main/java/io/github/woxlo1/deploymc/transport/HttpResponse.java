package io.github.woxlo1.deploymc.transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Internal, immutable response representation. */
public final class HttpResponse {

  private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  private static final String PROBLEM_CONTENT_TYPE = "application/problem+json; charset=utf-8";

  private final int status;
  private final Map<String, List<String>> headers;
  private final byte[] body;

  private HttpResponse(int status, Map<String, List<String>> headers, byte[] body) {
    this.status = status;
    this.headers = Collections.unmodifiableMap(headers);
    this.body = body.clone();
  }

  public static HttpResponse status(int status) {
    return new HttpResponse(status, new LinkedHashMap<String, List<String>>(), new byte[0]);
  }

  public static HttpResponse json(int status, String json) {
    Objects.requireNonNull(json, "json");
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("content-type", singleton(JSON_CONTENT_TYPE));
    return new HttpResponse(status, headers, json.getBytes(StandardCharsets.UTF_8));
  }

  public static HttpResponse of(int status, String contentType, byte[] body) {
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(body, "body");
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("content-type", singleton(contentType));
    return new HttpResponse(status, headers, body);
  }

  public static HttpResponse error(int status, String code) {
    return json(status, "{\"code\":\"" + escape(code) + "\"}");
  }

  /** RFC 9457 Problem Details with the DeployMC stable code and request id extensions. */
  public static HttpResponse problem(
      int status, String code, String title, String detail, String requestId) {
    return problem(
        status, code, title, detail, requestId, false, Collections.<ProblemIssue>emptyList());
  }

  /** RFC 9457 Problem Details with field- or path-level issues. */
  public static HttpResponse problem(
      int status,
      String code,
      String title,
      String detail,
      String requestId,
      boolean retryable,
      List<ProblemIssue> issues) {
    StringBuilder body = new StringBuilder();
    body.append("{\"type\":\"urn:deploymc:error:").append(escape(code)).append('"');
    body.append(",\"title\":\"").append(escape(title)).append('"');
    body.append(",\"status\":").append(status);
    body.append(",\"code\":\"").append(escape(code)).append('"');
    body.append(",\"detail\":\"").append(escape(detail)).append('"');
    body.append(",\"requestId\":\"").append(escape(requestId)).append('"');
    body.append(",\"retryable\":").append(retryable ? "true" : "false");
    body.append(",\"errors\":[");
    if (issues != null) {
      for (int i = 0; i < issues.size(); i++) {
        if (i > 0) {
          body.append(',');
        }
        ProblemIssue issue = issues.get(i);
        body.append("{\"pointer\":\"").append(escape(issue.pointer)).append('"');
        body.append(",\"code\":\"").append(escape(issue.code)).append('"');
        body.append(",\"detail\":\"").append(escape(issue.detail)).append('"');
        if (issue.severity != null) {
          body.append(",\"severity\":\"").append(escape(issue.severity)).append('"');
        }
        if (issue.line != null) {
          body.append(",\"line\":").append(issue.line.intValue());
        }
        if (issue.column != null) {
          body.append(",\"column\":").append(issue.column.intValue());
        }
        if (issue.offset != null) {
          body.append(",\"offset\":").append(issue.offset.longValue());
        }
        if (issue.parameters != null && !issue.parameters.isEmpty()) {
          body.append(",\"parameters\":[");
          for (int p = 0; p < issue.parameters.size(); p++) {
            if (p > 0) {
              body.append(',');
            }
            body.append('"').append(escape(issue.parameters.get(p))).append('"');
          }
          body.append(']');
        }
        body.append('}');
      }
    }
    body.append("]}");
    return of(status, PROBLEM_CONTENT_TYPE, body.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** One stable, localized entry in a Problem's {@code errors[]} array. */
  public static final class ProblemIssue {

    private final String pointer;
    private final String code;
    private final String detail;
    private final String severity;
    private final Integer line;
    private final Integer column;
    private final Long offset;
    private final List<String> parameters;

    private ProblemIssue(
        String pointer,
        String code,
        String detail,
        String severity,
        Integer line,
        Integer column,
        Long offset,
        List<String> parameters) {
      this.pointer = Objects.requireNonNull(pointer, "pointer");
      this.code = Objects.requireNonNull(code, "code");
      this.detail = Objects.requireNonNull(detail, "detail");
      this.severity = severity;
      this.line = line;
      this.column = column;
      this.offset = offset;
      this.parameters =
          parameters == null
              ? Collections.<String>emptyList()
              : Collections.unmodifiableList(new ArrayList<String>(parameters));
    }

    public static ProblemIssue of(String pointer, String code, String detail) {
      return new ProblemIssue(pointer, code, detail, null, null, null, null, null);
    }

    public static ProblemIssue finding(
        String pointer,
        String code,
        String detail,
        String severity,
        int line,
        int column,
        long offset,
        List<String> parameters) {
      return new ProblemIssue(
          pointer,
          code,
          detail,
          severity,
          Integer.valueOf(line),
          Integer.valueOf(column),
          Long.valueOf(offset),
          parameters);
    }
  }

  private static String escape(String value) {
    Objects.requireNonNull(value, "value");
    StringBuilder result = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\');
      }
      if (character < 0x20) {
        result.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character)));
      } else {
        result.append(character);
      }
    }
    return result.toString();
  }

  public HttpResponse withHeader(String name, String value) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    List<String> values = copy.get(name.toLowerCase(Locale.ROOT));
    if (values == null) {
      values = new ArrayList<>();
      copy.put(name.toLowerCase(Locale.ROOT), values);
    }
    values.add(value);
    return new HttpResponse(status, copy, body);
  }

  public int status() {
    return status;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public byte[] body() {
    return body.clone();
  }

  private static List<String> singleton(String value) {
    List<String> values = new ArrayList<>();
    values.add(value);
    return values;
  }
}
