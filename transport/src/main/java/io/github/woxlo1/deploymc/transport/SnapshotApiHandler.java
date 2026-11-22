package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.diff.DiffEngine;
import io.github.woxlo1.deploymc.core.diff.DiffJson;
import io.github.woxlo1.deploymc.core.diff.DiffLimits;
import io.github.woxlo1.deploymc.core.diff.DiffMode;
import io.github.woxlo1.deploymc.core.diff.DiffRequest;
import io.github.woxlo1.deploymc.core.diff.DiffResult;
import io.github.woxlo1.deploymc.core.diff.DiffStatus;
import io.github.woxlo1.deploymc.core.files.FileOperationException;
import io.github.woxlo1.deploymc.core.files.ReadRange;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.RestoreResult;
import io.github.woxlo1.deploymc.core.snapshot.RetentionReport;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotKind;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotRecord;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticated HTTP adapter for the six normative snapshot-history endpoints.
 *
 * <p>Authentication is enforced by the transport before this handler runs. The authenticated
 * identity is carried on the internal request and is the only source of the restore actor. Every
 * sandbox path is resolved through the injected boundary, ids are opaque, and no host path, token,
 * blob name, or snapshot content is placed in an error response.
 */
public final class SnapshotApiHandler implements HttpHandler {

  private static final String SNAPSHOTS_PATH = "/api/v1/snapshots";
  private static final String DIFF_PATH = SNAPSHOTS_PATH + "/diff";
  private static final String PRUNE_PATH = SNAPSHOTS_PATH + "/prune";
  private static final int MAX_PATH_LENGTH = 1024;
  private static final int MAX_QUERY_LENGTH = 8192;
  private static final int MAX_SNAPSHOT_ID_LENGTH = 64;
  private static final int DEFAULT_LIST_LIMIT = 50;
  private static final int MAX_LIST_LIMIT = 500;
  private static final Pattern SNAPSHOT_ID = Pattern.compile("^snap_[0-9a-z]{26}$");
  private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{2}$");
  private static final Pattern RANGE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");
  private static final Comparator<SnapshotRecord> NEWEST_FIRST =
      new Comparator<SnapshotRecord>() {
        @Override
        public int compare(SnapshotRecord left, SnapshotRecord right) {
          int result = right.createdAt().compareTo(left.createdAt());
          return result != 0 ? result : left.id().compareTo(right.id());
        }
      };

  private final SandboxResolver resolver;
  private final SnapshotStore snapshots;
  private final Locale defaultLocale;
  private final int maximumBodyBytes;
  private final int maximumDiffBytes;

  public SnapshotApiHandler(
      SandboxResolver resolver,
      SnapshotStore snapshots,
      Locale defaultLocale,
      int maximumBodyBytes) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
    if (maximumBodyBytes < 1024) {
      throw new IllegalArgumentException("maximumBodyBytes must be at least 1024");
    }
    this.maximumBodyBytes = maximumBodyBytes;
    this.maximumDiffBytes = Math.min(maximumBodyBytes, DiffLimits.DEFAULTS.maxBytesPerSide());
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    Objects.requireNonNull(request, "request");
    Locale locale = negotiateLocale(request);
    TransportMessages messages = TransportMessages.of(locale);
    try {
      String method = request.method().toUpperCase(Locale.ROOT);
      String path = request.path();
      Map<String, String> query = query(request.query());
      if (SNAPSHOTS_PATH.equals(path)) {
        if ("GET".equals(method)) {
          return list(request, locale, messages, query);
        }
        return methodNotAllowed(request, locale, messages, "GET");
      }
      if (DIFF_PATH.equals(path)) {
        if ("GET".equals(method)) {
          return diff(request, locale, messages, query);
        }
        return methodNotAllowed(request, locale, messages, "GET");
      }
      if (PRUNE_PATH.equals(path)) {
        if ("POST".equals(method)) {
          return prune(request, locale, messages, request.body());
        }
        return methodNotAllowed(request, locale, messages, "POST");
      }
      String suffix = resource(path);
      if (suffix == null) {
        return problem(request, messages, locale, 404, "snapshots.not-found", false, null);
      }
      String id = suffix;
      String action = "";
      if (suffix.endsWith("/content")) {
        id = suffix.substring(0, suffix.length() - "/content".length());
        action = "content";
      } else if (suffix.endsWith("/restore")) {
        id = suffix.substring(0, suffix.length() - "/restore".length());
        action = "restore";
      }
      if ("content".equals(action)) {
        if ("GET".equals(method)) {
          return content(request, locale, messages, id);
        }
        return methodNotAllowed(request, locale, messages, "GET");
      }
      if ("restore".equals(action)) {
        if ("POST".equals(method)) {
          return restore(request, locale, messages, id);
        }
        return methodNotAllowed(request, locale, messages, "POST");
      }
      if ("GET".equals(method)) {
        return metadata(request, locale, messages, id);
      }
      return methodNotAllowed(request, locale, messages, "GET");
    } catch (RequestProblem failure) {
      return requestProblem(request, locale, messages, failure);
    } catch (SnapshotStoreException failure) {
      return storeProblem(request, locale, messages, failure);
    } catch (FileOperationException failure) {
      return sandboxProblem(request, locale, messages, failure);
    } catch (RuntimeException failure) {
      return problem(request, messages, locale, 500, "server.internal", true, null);
    }
  }

  private static String resource(String path) {
    String prefix = SNAPSHOTS_PATH + "/";
    if (!path.startsWith(prefix) || path.length() == prefix.length()) {
      return null;
    }
    String suffix = path.substring(prefix.length());
    if (suffix.startsWith("diff/") || suffix.startsWith("prune/") || suffix.contains("//")) {
      return null;
    }
    return suffix;
  }

  private HttpResponse list(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, SnapshotStoreException, FileOperationException {
    String path = requiredQuery(query, "path", MAX_PATH_LENGTH);
    int limit = intQuery(query, "limit", DEFAULT_LIST_LIMIT, 1, MAX_LIST_LIMIT);
    int offset = 0;
    String cursor = optionalQuery(query, "cursor", MAX_PATH_LENGTH);
    if (cursor != null) {
      offset = decodeCursor(cursor, path, limit);
    }
    resolve(path, SandboxAccessMode.READ);
    List<SnapshotRecord> records = new ArrayList<SnapshotRecord>(snapshots.findByPath(path));
    Collections.sort(records, NEWEST_FIRST);
    int start = (int) Math.min((long) records.size(), (long) offset);
    int end = (int) Math.min((long) records.size(), (long) offset + (long) limit);
    List<SnapshotRecord> page = records.subList(start, end);
    String nextCursor = end < records.size() ? encodeCursor(path, limit, end) : null;
    StringBuilder body = new StringBuilder();
    body.append("{\"path\":").append(quote(path));
    body.append(",\"items\":[");
    for (int index = 0; index < page.size(); index++) {
      if (index > 0) {
        body.append(',');
      }
      body.append(recordJson(page.get(index)));
    }
    body.append("],\"nextCursor\":").append(nextCursor == null ? "null" : quote(nextCursor));
    body.append(",\"total\":").append(records.size()).append('}');
    return json(request, 200, body.toString());
  }

  private HttpResponse metadata(
      HttpRequest request, Locale locale, TransportMessages messages, String id)
      throws SnapshotStoreException {
    if (!SNAPSHOT_ID.matcher(id).matches()) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, id);
    }
    SnapshotRecord record = snapshots.read(id);
    String etag = etag(id);
    if (matches(request.header("if-none-match"), etag)) {
      return withRequestId(HttpResponse.status(304).withHeader("ETag", etag), request);
    }
    return withRequestId(
        HttpResponse.json(200, recordJson(record)).withHeader("ETag", etag), request);
  }

  private HttpResponse content(
      HttpRequest request, Locale locale, TransportMessages messages, String id)
      throws SnapshotStoreException, RequestProblem {
    if (!SNAPSHOT_ID.matcher(id).matches()) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, id);
    }
    SnapshotRecord record = snapshots.read(id);
    if (!record.kind().hasContent() || record.hash() == null) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, id);
    }
    byte[] bytes = snapshots.readContent(id);
    String etag = etag(record.hash());
    if (matches(request.header("if-none-match"), etag)) {
      return withRequestId(HttpResponse.status(304).withHeader("ETag", etag), request);
    }
    boolean partial = request.header("range") != null;
    ReadRange range = parseRange(request.header("range"), bytes.length);
    int start = (int) range.offset();
    int length = range.bounded() ? (int) range.length() : bytes.length - start;
    byte[] body = Arrays.copyOfRange(bytes, start, start + length);
    HttpResponse response =
        HttpResponse.of(partial ? 206 : 200, "application/octet-stream", body)
            .withHeader("ETag", etag)
            .withHeader("Accept-Ranges", "bytes");
    if (partial) {
      response = response.withHeader("Content-Range", contentRange(start, length, bytes.length));
    }
    return withRequestId(response, request);
  }

  private HttpResponse diff(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, SnapshotStoreException {
    String a = requiredQuery(query, "a", MAX_SNAPSHOT_ID_LENGTH);
    String b = requiredQuery(query, "b", MAX_SNAPSHOT_ID_LENGTH);
    String requestedMode = optionalQuery(query, "mode", 16);
    String modeText = requestedMode == null ? "auto" : requestedMode;
    DiffMode mode = diffMode(modeText);
    if (!SNAPSHOT_ID.matcher(a).matches()) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, a);
    }
    if (!SNAPSHOT_ID.matcher(b).matches()) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, b);
    }
    SnapshotRecord beforeRecord = snapshots.read(a);
    SnapshotRecord afterRecord = snapshots.read(b);
    if (mode == DiffMode.AUTO
        && (beforeRecord.kind() == SnapshotKind.JAR || afterRecord.kind() == SnapshotKind.JAR)) {
      mode = DiffMode.JAR;
    }
    if (beforeRecord.size() > maximumDiffBytes || afterRecord.size() > maximumDiffBytes) {
      return problem(request, messages, locale, 413, "diff.too-large", false, null);
    }
    byte[] before = snapshots.readContent(a);
    byte[] after = snapshots.readContent(b);
    DiffResult result = DiffEngine.diff(new DiffRequest(before, after, mode));
    if (result.status() == DiffStatus.INVALID_INPUT) {
      return problem(request, messages, locale, 400, "diff.invalid-input", false, null);
    }
    if (result.status() == DiffStatus.TOO_LARGE) {
      return problem(request, messages, locale, 413, "diff.too-large", false, null);
    }
    return json(request, 200, DiffJson.toSnapshotDiffJson(a, b, result));
  }

  private HttpResponse restore(
      HttpRequest request, Locale locale, TransportMessages messages, String id)
      throws SnapshotStoreException, RequestProblem, FileOperationException {
    Identity identity = request.identity();
    if (identity == null || identity.id().trim().isEmpty()) {
      return problem(request, messages, locale, 500, "server.internal", true, null);
    }
    if (!SNAPSHOT_ID.matcher(id).matches()) {
      throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, id);
    }
    SnapshotRecord record = snapshots.read(id);
    if (record.kind() == SnapshotKind.DIRECTORY) {
      throw new SnapshotStoreException(
          SnapshotStoreException.Reason.CORRUPT, "directory marker has no restore target");
    }
    resolve(record.path(), SandboxAccessMode.WRITE);
    String expectedRevision = revision(request.header("if-match"));
    if (expectedRevision == null) {
      if (exists(record.path())) {
        throw RequestProblem.preconditionRequired();
      }
      expectedRevision = SnapshotStore.ABSENT_REVISION;
    }
    RestoreResult result = snapshots.restore(id, record.path(), expectedRevision, identity.id());
    String body =
        "{\"restoredSnapshotId\":"
            + quote(result.restoredSnapshotId())
            + ",\"preRestoreSnapshotId\":"
            + quote(result.preRestoreSnapshotId())
            + '}';
    return json(request, 200, body);
  }

  private HttpResponse prune(
      HttpRequest request, Locale locale, TransportMessages messages, byte[] body)
      throws RequestProblem, SnapshotStoreException, FileOperationException {
    requireMediaType(request, "application/json");
    io.github.woxlo1.deploymc.core.config.YamlValue root =
        RequestJson.parse(body, maximumBodyBytes);
    RequestJson.rejectUnknownFields(root, "path", "id");
    String path = RequestJson.optionalString(root, "path", MAX_PATH_LENGTH);
    String id = RequestJson.optionalString(root, "id", MAX_SNAPSHOT_ID_LENGTH);
    if ((path == null) == (id == null)) {
      throw RequestProblem.invalid();
    }
    RetentionReport report;
    if (path != null) {
      resolve(path, SandboxAccessMode.READ);
      report = snapshots.prunePath(path);
    } else {
      if (!SNAPSHOT_ID.matcher(id).matches()) {
        throw new SnapshotStoreException(SnapshotStoreException.Reason.NOT_FOUND, id);
      }
      snapshots.read(id);
      report = snapshots.pruneSnapshot(id);
    }
    return json(request, 200, retentionJson(report));
  }

  private void resolve(String path, SandboxAccessMode mode) throws FileOperationException {
    try {
      resolver.resolve(path, mode);
    } catch (SandboxRejectionException rejection) {
      throw FileOperationException.fromSandbox(rejection, path);
    }
  }

  private boolean exists(String path) throws FileOperationException {
    try {
      return Files.exists(resolver.resolve(path, SandboxAccessMode.READ).absolutePath());
    } catch (SandboxRejectionException rejection) {
      throw FileOperationException.fromSandbox(rejection, path);
    }
  }

  private HttpResponse requestProblem(
      HttpRequest request, Locale locale, TransportMessages messages, RequestProblem failure) {
    return problem(request, messages, locale, failure.status(), failure.code(), false, null);
  }

  private HttpResponse storeProblem(
      HttpRequest request,
      Locale locale,
      TransportMessages messages,
      SnapshotStoreException failure) {
    int status = status(failure.reason());
    return problem(
        request, messages, locale, status, failure.code(), retryable(failure.reason()), null);
  }

  private HttpResponse sandboxProblem(
      HttpRequest request,
      Locale locale,
      TransportMessages messages,
      FileOperationException failure) {
    int status = failure.reason() == FileOperationException.Reason.PROTECTED ? 403 : 400;
    return problem(request, messages, locale, status, failure.code(), false, null);
  }

  private HttpResponse problem(
      HttpRequest request,
      TransportMessages messages,
      Locale locale,
      int status,
      String code,
      boolean retryable,
      java.util.List<HttpResponse.ProblemIssue> issues) {
    HttpResponse response =
        HttpResponse.problem(
            status,
            code,
            messages.format(titleKey(code)),
            messages.format(detailKey(code)),
            request.id(),
            retryable,
            issues);
    return withRequestId(response, request);
  }

  private HttpResponse methodNotAllowed(
      HttpRequest request, Locale locale, TransportMessages messages, String allow) {
    return problem(request, messages, locale, 405, "request.method-not-allowed", false, null)
        .withHeader("Allow", allow);
  }

  private static HttpResponse json(HttpRequest request, int status, String body) {
    return withRequestId(HttpResponse.json(status, body), request);
  }

  private static HttpResponse withRequestId(HttpResponse response, HttpRequest request) {
    return response.withHeader("X-Request-Id", request.id());
  }

  private static int status(SnapshotStoreException.Reason reason) {
    switch (reason) {
      case NOT_FOUND:
        return 404;
      case CORRUPT:
        return 409;
      case RESTORE_CONFLICT:
        return 412;
      case STORE_UNAVAILABLE:
        return 503;
      case PATH_REJECTED:
        return 400;
      case IO:
      default:
        return 500;
    }
  }

  private static boolean retryable(SnapshotStoreException.Reason reason) {
    return reason == SnapshotStoreException.Reason.STORE_UNAVAILABLE
        || reason == SnapshotStoreException.Reason.IO;
  }

  private static String titleKey(String code) {
    if ("request.invalid".equals(code)) {
      return MessageKeys.REQUEST_INVALID_TITLE;
    }
    if ("request.unsupported-media-type".equals(code)) {
      return MessageKeys.REQUEST_UNSUPPORTED_MEDIA_TYPE_TITLE;
    }
    if ("request.too-large".equals(code)) {
      return MessageKeys.REQUEST_TOO_LARGE_TITLE;
    }
    if ("request.invalid-cursor".equals(code)) {
      return MessageKeys.REQUEST_INVALID_CURSOR_TITLE;
    }
    if ("request.method-not-allowed".equals(code)) {
      return MessageKeys.REQUEST_METHOD_NOT_ALLOWED_TITLE;
    }
    if ("request.precondition-required".equals(code)) {
      return MessageKeys.REQUEST_PRECONDITION_REQUIRED_TITLE;
    }
    if ("files.invalid-range".equals(code)) {
      return MessageKeys.FILES_INVALID_RANGE_TITLE;
    }
    if ("files.path-rejected".equals(code)) {
      return MessageKeys.FILES_PATH_REJECTED_TITLE;
    }
    if ("files.protected".equals(code)) {
      return MessageKeys.FILES_PROTECTED_TITLE;
    }
    if ("files.io".equals(code)) {
      return MessageKeys.FILES_IO_TITLE;
    }
    if ("snapshots.not-found".equals(code)) {
      return MessageKeys.SNAPSHOTS_NOT_FOUND_TITLE;
    }
    if ("snapshots.corrupt".equals(code)) {
      return MessageKeys.SNAPSHOTS_CORRUPT_TITLE;
    }
    if ("snapshots.restore-conflict".equals(code)) {
      return MessageKeys.SNAPSHOTS_RESTORE_CONFLICT_TITLE;
    }
    if ("snapshots.store-unavailable".equals(code)) {
      return MessageKeys.SNAPSHOTS_STORE_UNAVAILABLE_TITLE;
    }
    if ("diff.invalid-input".equals(code)) {
      return MessageKeys.DIFF_INVALID_INPUT_TITLE;
    }
    if ("diff.too-large".equals(code)) {
      return MessageKeys.DIFF_TOO_LARGE_TITLE;
    }
    return MessageKeys.SERVER_INTERNAL_TITLE;
  }

  private static String detailKey(String code) {
    if ("request.invalid".equals(code)) {
      return MessageKeys.REQUEST_INVALID_DETAIL;
    }
    if ("request.unsupported-media-type".equals(code)) {
      return MessageKeys.REQUEST_UNSUPPORTED_MEDIA_TYPE_DETAIL;
    }
    if ("request.too-large".equals(code)) {
      return MessageKeys.REQUEST_TOO_LARGE_DETAIL;
    }
    if ("request.invalid-cursor".equals(code)) {
      return MessageKeys.REQUEST_INVALID_CURSOR_DETAIL;
    }
    if ("request.method-not-allowed".equals(code)) {
      return MessageKeys.REQUEST_METHOD_NOT_ALLOWED_DETAIL;
    }
    if ("request.precondition-required".equals(code)) {
      return MessageKeys.REQUEST_PRECONDITION_REQUIRED_DETAIL;
    }
    if ("files.invalid-range".equals(code)) {
      return MessageKeys.FILES_INVALID_RANGE_DETAIL;
    }
    if ("files.path-rejected".equals(code)) {
      return MessageKeys.FILES_PATH_REJECTED_DETAIL;
    }
    if ("files.protected".equals(code)) {
      return MessageKeys.FILES_PROTECTED_DETAIL;
    }
    if ("files.io".equals(code)) {
      return MessageKeys.FILES_IO_DETAIL;
    }
    if ("snapshots.not-found".equals(code)) {
      return MessageKeys.SNAPSHOTS_NOT_FOUND_DETAIL;
    }
    if ("snapshots.corrupt".equals(code)) {
      return MessageKeys.SNAPSHOTS_CORRUPT_DETAIL;
    }
    if ("snapshots.restore-conflict".equals(code)) {
      return MessageKeys.SNAPSHOTS_RESTORE_CONFLICT_DETAIL;
    }
    if ("snapshots.store-unavailable".equals(code)) {
      return MessageKeys.SNAPSHOTS_STORE_UNAVAILABLE_DETAIL;
    }
    if ("diff.invalid-input".equals(code)) {
      return MessageKeys.DIFF_INVALID_INPUT_DETAIL;
    }
    if ("diff.too-large".equals(code)) {
      return MessageKeys.DIFF_TOO_LARGE_DETAIL;
    }
    return MessageKeys.SERVER_INTERNAL_DETAIL;
  }

  private static String recordJson(SnapshotRecord record) {
    StringBuilder body = new StringBuilder();
    body.append("{\"id\":").append(quote(record.id()));
    body.append(",\"capture\":").append(quote(record.capture()));
    body.append(",\"path\":").append(quote(record.path()));
    body.append(",\"kind\":").append(quote(record.kind().value()));
    body.append(",\"hash\":").append(record.hash() == null ? "null" : quote(record.hash()));
    body.append(",\"size\":").append(record.size());
    body.append(",\"mode\":").append(record.mode() == null ? "null" : quote(record.mode()));
    body.append(",\"createdAt\":").append(quote(record.createdAt().toString()));
    body.append(",\"actor\":").append(quote(record.actor()));
    body.append(",\"reason\":").append(quote(record.reason().value()));
    body.append(",\"deploymentId\":")
        .append(record.deploymentId() == null ? "null" : quote(record.deploymentId()));
    body.append(",\"parent\":").append(record.parent() == null ? "null" : quote(record.parent()));
    body.append(",\"pluginMetadata\":").append(metadataJson(record.pluginMetadata()));
    return body.append('}').toString();
  }

  private static String metadataJson(Map<String, String> metadata) {
    if (metadata == null) {
      return "null";
    }
    StringBuilder body = new StringBuilder("{");
    boolean first = true;
    for (String field : new String[] {"name", "version", "descriptor-summary"}) {
      String value = metadata.get(field);
      if (value == null) {
        continue;
      }
      if (!first) {
        body.append(',');
      }
      body.append(quote(field)).append(':').append(quote(value));
      first = false;
    }
    return body.append('}').toString();
  }

  private static String retentionJson(RetentionReport report) {
    StringBuilder body = new StringBuilder();
    body.append("{\"considered\":").append(report.considered());
    body.append(",\"protectedRecords\":").append(report.protectedRecords());
    body.append(",\"pruned\":").append(report.pruned());
    body.append(",\"failed\":").append(report.failed());
    body.append(",\"storeBytesBefore\":").append(report.storeBytesBefore());
    body.append(",\"storeBytesAfter\":").append(report.storeBytesAfter()).append('}');
    return body.toString();
  }

  private static DiffMode diffMode(String value) throws RequestProblem {
    if ("auto".equals(value)) {
      return DiffMode.AUTO;
    }
    if ("text".equals(value)) {
      return DiffMode.TEXT;
    }
    if ("yaml".equals(value)) {
      return DiffMode.YAML;
    }
    if ("json".equals(value)) {
      return DiffMode.JSON;
    }
    if ("jar".equals(value)) {
      return DiffMode.JAR;
    }
    throw RequestProblem.invalid();
  }

  private static void requireMediaType(HttpRequest request, String expected) throws RequestProblem {
    String contentType = request.header("content-type");
    if (contentType == null
        || !expected.equals(contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
      throw RequestProblem.unsupportedMediaType();
    }
    if ("application/json".equals(expected)) {
      String[] parts = contentType.split(";", 2);
      if (parts.length > 1) {
        String parameter = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!parameter.isEmpty() && !parameter.equals("charset=utf-8")) {
          throw RequestProblem.unsupportedMediaType();
        }
      }
    }
  }

  private static String revision(String header) throws RequestProblem {
    if (header == null) {
      return null;
    }
    String value = header.trim();
    if (value.isEmpty() || value.startsWith("W/")) {
      throw RequestProblem.invalid();
    }
    if (value.length() > 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
      value = value.substring(1, value.length() - 1);
    }
    if (!value.matches("^sha256:[0-9a-f]{64}$")) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static boolean matches(String header, String etag) {
    if (header == null) {
      return false;
    }
    String value = header.trim();
    return "*".equals(value) || etag.equals(value);
  }

  private static ReadRange parseRange(String header, long size) throws RequestProblem {
    if (header == null) {
      return ReadRange.full();
    }
    Matcher matcher = RANGE.matcher(header.trim());
    if (!matcher.matches()) {
      throw RequestProblem.invalidRange();
    }
    String startText = matcher.group(1);
    String endText = matcher.group(2);
    if (startText.isEmpty()) {
      throw RequestProblem.invalidRange();
    }
    long start = parseLong(startText);
    boolean bounded = !endText.isEmpty();
    long end = bounded ? parseLong(endText) : size;
    if (start < 0L || end < 0L || start >= size || (bounded && end <= start) || end > size) {
      throw RequestProblem.invalidRange();
    }
    return bounded ? ReadRange.between(start, end - start) : ReadRange.from(start);
  }

  private static long parseLong(String value) throws RequestProblem {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      throw RequestProblem.invalidRange();
    }
  }

  private static String contentRange(int start, int length, long total) {
    return "bytes " + start + '-' + (start + length - 1L) + '/' + total;
  }

  private static String etag(String value) {
    return '"' + value + '"';
  }

  private static String encodeCursor(String path, int limit, int offset) {
    String payload = path + '\u0000' + limit + '\u0000' + offset;
    byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
    byte[] encoded = new byte[8 + payloadBytes.length];
    System.arraycopy(digest(payloadBytes), 0, encoded, 0, 8);
    System.arraycopy(payloadBytes, 0, encoded, 8, payloadBytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
  }

  private static int decodeCursor(String cursor, String path, int limit) throws RequestProblem {
    if (cursor.isEmpty() || cursor.length() > 512) {
      throw RequestProblem.invalidCursor();
    }
    byte[] encoded;
    try {
      encoded = Base64.getUrlDecoder().decode(cursor);
    } catch (IllegalArgumentException invalid) {
      throw RequestProblem.invalidCursor();
    }
    if (encoded.length < 9) {
      throw RequestProblem.invalidCursor();
    }
    byte[] payload = Arrays.copyOfRange(encoded, 8, encoded.length);
    byte[] digest = digest(payload);
    for (int index = 0; index < 8; index++) {
      if (digest[index] != encoded[index]) {
        throw RequestProblem.invalidCursor();
      }
    }
    String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\u0000", -1);
    if (parts.length != 3 || !parts[0].equals(path) || !parts[1].equals(String.valueOf(limit))) {
      throw RequestProblem.invalidCursor();
    }
    try {
      int offset = Integer.parseInt(parts[2]);
      if (offset < 0) {
        throw RequestProblem.invalidCursor();
      }
      return offset;
    } catch (NumberFormatException invalid) {
      throw RequestProblem.invalidCursor();
    }
  }

  private static byte[] digest(byte[] input) {
    try {
      return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(input), 8);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Map<String, String> query(String raw) throws RequestProblem {
    Map<String, String> values = new LinkedHashMap<String, String>();
    if (raw == null || raw.isEmpty()) {
      return values;
    }
    if (raw.length() > MAX_QUERY_LENGTH) {
      throw RequestProblem.invalid();
    }
    for (String pair : raw.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int separator = pair.indexOf('=');
      String key = separator < 0 ? pair : pair.substring(0, separator);
      String value = separator < 0 ? "" : pair.substring(separator + 1);
      String decodedKey = decodeQuery(key);
      String decodedValue = decodeQuery(value);
      if (decodedKey.isEmpty() || values.containsKey(decodedKey)) {
        throw RequestProblem.invalid();
      }
      values.put(decodedKey, decodedValue);
    }
    return values;
  }

  private static String decodeQuery(String value) throws RequestProblem {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '%') {
        if (index + 2 >= value.length()) {
          throw RequestProblem.invalid();
        }
        String hex = value.substring(index + 1, index + 3);
        if (!HEX.matcher(hex).matches()) {
          throw RequestProblem.invalid();
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
          throw RequestProblem.invalid();
        }
      }
      return decoded;
    } catch (CharacterCodingException invalid) {
      throw RequestProblem.invalid();
    }
  }

  private static String requiredQuery(Map<String, String> query, String name, int maximum)
      throws RequestProblem {
    String value = query.get(name);
    if (value == null || value.length() > maximum) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static String optionalQuery(Map<String, String> query, String name, int maximum)
      throws RequestProblem {
    String value = query.get(name);
    if (value != null && value.length() > maximum) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static int intQuery(
      Map<String, String> query, String name, int fallback, int minimum, int maximum)
      throws RequestProblem {
    String value = query.get(name);
    if (value == null) {
      return fallback;
    }
    try {
      int number = Integer.parseInt(value);
      if (number < minimum) {
        throw RequestProblem.invalid();
      }
      return Math.min(number, maximum);
    } catch (NumberFormatException invalid) {
      throw RequestProblem.invalid();
    }
  }

  private static String quote(String value) {
    StringBuilder result = new StringBuilder(value.length() + 2);
    result.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\').append(character);
      } else if (character < 0x20) {
        result.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character)));
      } else {
        result.append(character);
      }
    }
    return result.append('"').toString();
  }

  private Locale negotiateLocale(HttpRequest request) {
    String header = request.header("accept-language");
    if (header == null || header.trim().isEmpty()) {
      return defaultLocale;
    }
    List<Language> languages = new ArrayList<Language>();
    String[] choices = header.split(",", -1);
    for (int index = 0; index < choices.length; index++) {
      String choice = choices[index].trim();
      if (choice.isEmpty()) {
        continue;
      }
      String[] parts = choice.split(";", 2);
      String tag = parts[0].trim();
      double weight = 1.0d;
      if (parts.length > 1) {
        String[] parameter = parts[1].trim().split("=", 2);
        if (parameter.length == 2 && "q".equalsIgnoreCase(parameter[0])) {
          try {
            weight = Double.parseDouble(parameter[1]);
          } catch (NumberFormatException invalid) {
            continue;
          }
        }
      }
      if (weight > 0.0d) {
        languages.add(new Language(tag, weight, index));
      }
    }
    Collections.sort(
        languages,
        new Comparator<Language>() {
          @Override
          public int compare(Language left, Language right) {
            int result = Double.compare(right.weight, left.weight);
            return result != 0 ? result : Integer.compare(left.order, right.order);
          }
        });
    for (Language language : languages) {
      Locale parsed = Locale.forLanguageTag(language.tag);
      if ("ja".equals(parsed.getLanguage())) {
        return Locale.JAPANESE;
      }
      if ("en".equals(parsed.getLanguage())) {
        return Locale.ENGLISH;
      }
    }
    return defaultLocale;
  }

  private static final class Language {

    private final String tag;
    private final double weight;
    private final int order;

    private Language(String tag, double weight, int order) {
      this.tag = tag;
      this.weight = weight;
      this.order = order;
    }
  }
}
