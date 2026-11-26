package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.audit.AuditJson;
import io.github.woxlo1.deploymc.core.audit.AuditPage;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.audit.AuditVerificationReport;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Authenticated adapter for audit query, export, and verification routes. */
public final class AuditApiHandler implements HttpHandler {

  public static final String PATH = "/api/v1/audit";
  public static final String EXPORT_PATH = PATH + "/export";
  public static final String VERIFY_PATH = PATH + "/verify";

  private static final String NDJSON = "application/x-ndjson; charset=utf-8";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String EXPORT_FILENAME = "attachment; filename=\"audit-export.ndjson\"";
  private static final String ALLOW = "GET";
  private static final int MAX_QUERY_LENGTH = 8192;
  private static final int MAX_QUERY_PAIRS = 32;
  private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{2}$");

  private final AuditStore store;
  private final AuditRecorder audit;
  private final Locale defaultLocale;

  public AuditApiHandler(AuditStore store, AuditRecorder audit, Locale defaultLocale) {
    this.store = Objects.requireNonNull(store, "store");
    this.audit = Objects.requireNonNull(audit, "audit");
    this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    Objects.requireNonNull(request, "request");
    TransportMessages messages = TransportMessages.of(negotiateLocale(request));
    if (!"GET".equals(request.method().toUpperCase(Locale.ROOT))) {
      return TransportProblems.problem(
              405,
              "request.method-not-allowed",
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_TITLE,
              MessageKeys.REQUEST_METHOD_NOT_ALLOWED_DETAIL,
              request.id(),
              messages,
              false)
          .withHeader("Allow", ALLOW)
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    }
    try {
      Map<String, String> parameters = query(request.query());
      if (PATH.equals(request.path())) {
        AuditQuery filter = AuditQuery.parse(parameters);
        AuditPage page = store.query(filter);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("items", itemValues(page));
        body.put("nextCursor", page.nextCursor());
        body.put("total", Long.valueOf(page.total()));
        return access(
            request,
            messages,
            HttpResponse.json(200, AuditJson.canonical(body)),
            filter,
            page.items().size());
      }
      if (EXPORT_PATH.equals(request.path())) {
        String exported = store.export(parameters);
        int rowCount = countLines(exported);
        HttpResponse response =
            HttpResponse.of(200, NDJSON, exported.getBytes(StandardCharsets.UTF_8))
                .withHeader(CONTENT_DISPOSITION, EXPORT_FILENAME);
        return access(request, messages, response, AuditQuery.defaults(), rowCount);
      }
      if (VERIFY_PATH.equals(request.path())) {
        if (!parameters.isEmpty()) {
          throw AuditException.invalid("verification does not accept filters");
        }
        AuditVerificationReport report = store.verify();
        HttpResponse response = HttpResponse.json(200, AuditJson.canonical(report.toJsonObject()));
        return access(request, messages, response, AuditQuery.defaults(), 0);
      }
      return TransportProblems.problem(
          404,
          "request.invalid",
          MessageKeys.REQUEST_INVALID_TITLE,
          MessageKeys.REQUEST_INVALID_DETAIL,
          request.id(),
          messages,
          false);
    } catch (AuditException failure) {
      audit.denial(request, request.identity(), "audit.read", null, failure.problemCode());
      return TransportProblems.auditProblem(failure, request.id(), messages)
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    } catch (IllegalArgumentException failure) {
      AuditException invalid = AuditException.invalid("invalid audit response");
      return TransportProblems.auditProblem(invalid, request.id(), messages)
          .withHeader(TransportProblems.REQUEST_ID, request.id());
    }
  }

  private HttpResponse access(
      HttpRequest request,
      TransportMessages messages,
      HttpResponse response,
      AuditQuery filter,
      int rowCount)
      throws AuditException {
    try {
      audit.access(request, filter, rowCount);
    } catch (AuditException failure) {
      return TransportProblems.auditProblem(failure, request.id(), messages);
    }
    return response.withHeader(TransportProblems.REQUEST_ID, request.id());
  }

  private static List<Object> itemValues(AuditPage page) {
    List<Object> items = new ArrayList<Object>(page.items().size());
    for (int index = 0; index < page.items().size(); index++) {
      items.add(page.items().get(index).values());
    }
    return Collections.unmodifiableList(items);
  }

  private static int countLines(String value) {
    int count = 0;
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) == '\n') {
        count++;
      }
    }
    return count;
  }

  private Locale negotiateLocale(HttpRequest request) {
    String header = request.header("accept-language");
    if (header != null && header.toLowerCase(Locale.ROOT).contains("ja")) {
      return Locale.JAPANESE;
    }
    return defaultLocale;
  }

  private static Map<String, String> query(String raw) throws AuditException {
    Map<String, String> values = new LinkedHashMap<String, String>();
    if (raw == null || raw.isEmpty()) {
      return values;
    }
    if (raw.length() > MAX_QUERY_LENGTH) {
      throw AuditException.invalid("audit query is too long");
    }
    int pairs = 0;
    for (String pair : raw.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      if (++pairs > MAX_QUERY_PAIRS) {
        throw AuditException.invalid("audit query has too many filters");
      }
      int separator = pair.indexOf('=');
      String key = separator < 0 ? pair : pair.substring(0, separator);
      String value = separator < 0 ? "" : pair.substring(separator + 1);
      String decodedKey = decode(key);
      String decodedValue = decode(value);
      if (decodedKey.isEmpty() || values.containsKey(decodedKey)) {
        throw AuditException.invalid("invalid audit query");
      }
      values.put(decodedKey, decodedValue);
    }
    return values;
  }

  private static String decode(String value) throws AuditException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '%') {
        if (index + 2 >= value.length()) {
          throw AuditException.invalid("invalid audit query escape");
        }
        String hex = value.substring(index + 1, index + 3);
        if (!HEX.matcher(hex).matches()) {
          throw AuditException.invalid("invalid audit query escape");
        }
        bytes.write(Integer.parseInt(hex, 16));
        index += 2;
      } else {
        byte[] encoded = String.valueOf(character).getBytes(StandardCharsets.UTF_8);
        bytes.write(encoded, 0, encoded.length);
      }
    }
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      String decoded = decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
      for (int index = 0; index < decoded.length(); index++) {
        if (decoded.charAt(index) < 0x20 || decoded.charAt(index) == 0x7f) {
          throw AuditException.invalid("invalid audit query character");
        }
      }
      return decoded;
    } catch (CharacterCodingException invalid) {
      throw AuditException.invalid("invalid audit query encoding");
    }
  }
}
