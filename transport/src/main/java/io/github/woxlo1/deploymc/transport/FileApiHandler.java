package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.files.DeleteRequest;
import io.github.woxlo1.deploymc.core.files.FileContent;
import io.github.woxlo1.deploymc.core.files.FileInfo;
import io.github.woxlo1.deploymc.core.files.FileOperationException;
import io.github.woxlo1.deploymc.core.files.FileOperationsService;
import io.github.woxlo1.deploymc.core.files.ReadRange;
import io.github.woxlo1.deploymc.core.files.ValidatedFileResult;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.validation.ValidationException;
import io.github.woxlo1.deploymc.core.validation.ValidationIssue;
import io.github.woxlo1.deploymc.core.validation.ValidationLimitException;
import io.github.woxlo1.deploymc.core.validation.ValidationMessages;
import io.github.woxlo1.deploymc.core.validation.ValidationResult;
import io.github.woxlo1.deploymc.core.validation.ValidatorRegistry;
import io.github.woxlo1.deploymc.transport.HttpResponse.ProblemIssue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticated HTTP adapter for the normative file and validation endpoints.
 *
 * <p>Authentication is enforced by the transport before this handler runs. Every path is resolved
 * by the injected sandbox boundary, every write goes through the host-configured validation gate,
 * and no host path, token, or file content is placed in an error detail.
 */
public final class FileApiHandler implements HttpHandler {

  private static final String FILES_PATH = "/api/v1/files";
  private static final String CONTENT_PATH = "/api/v1/files/content";
  private static final String MOVE_PATH = "/api/v1/files/move";
  private static final String UPLOAD_PATH = "/api/v1/files/upload";
  private static final String VALIDATE_PATH = "/api/v1/files/validate";
  private static final int MAX_PATH_LENGTH = 1024;
  private static final int MAX_QUERY_LENGTH = 8192;
  private static final int MAX_LIST_ENTRIES = 5000;
  private static final int DEFAULT_LIST_LIMIT = 50;
  private static final int MAX_LIST_LIMIT = 500;
  private static final int DEFAULT_DEPTH = 1;
  private static final int MAX_DEPTH = 64;
  private static final Pattern RANGE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");
  private static final Pattern REVISION = Pattern.compile("^sha256:[0-9a-f]{64}$");
  private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{2}$");

  private final SandboxResolver resolver;
  private final FileOperationsService files;
  private final ValidatorRegistry validators;
  private final Locale defaultLocale;
  private final int maximumBodyBytes;

  public FileApiHandler(
      SandboxResolver resolver,
      FileOperationsService files,
      ValidatorRegistry validators,
      Locale defaultLocale,
      int maximumBodyBytes) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.files = Objects.requireNonNull(files, "files");
    this.validators = Objects.requireNonNull(validators, "validators");
    this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
    if (maximumBodyBytes < 1024) {
      throw new IllegalArgumentException("maximumBodyBytes must be at least 1024");
    }
    this.maximumBodyBytes = maximumBodyBytes;
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
      if (FILES_PATH.equals(path)) {
        if ("GET".equals(method)) {
          return list(request, locale, messages, query);
        }
        if ("POST".equals(method)) {
          return create(request, locale, messages, request.body());
        }
        if ("DELETE".equals(method)) {
          return delete(request, locale, messages, query);
        }
        return methodNotAllowed(request, locale, messages, "GET, POST, DELETE");
      }
      if (CONTENT_PATH.equals(path)) {
        if ("GET".equals(method)) {
          return readContent(request, locale, messages, query);
        }
        if ("PUT".equals(method)) {
          return writeContent(request, locale, messages, query);
        }
        return methodNotAllowed(request, locale, messages, "GET, PUT");
      }
      if (MOVE_PATH.equals(path)) {
        if ("POST".equals(method)) {
          return move(request, locale, messages, request.body());
        }
        return methodNotAllowed(request, locale, messages, "POST");
      }
      if (UPLOAD_PATH.equals(path)) {
        if ("POST".equals(method)) {
          return upload(request, locale, messages, query);
        }
        return methodNotAllowed(request, locale, messages, "POST");
      }
      if (VALIDATE_PATH.equals(path)) {
        if ("POST".equals(method)) {
          return validate(request, locale, messages, request.body());
        }
        return methodNotAllowed(request, locale, messages, "POST");
      }
      return problem(request, messages, locale, 404, "files.not-found", false, null);
    } catch (RequestProblem problem) {
      return requestProblem(request, locale, messages, problem);
    } catch (FileOperationException failure) {
      return fileProblem(request, locale, messages, failure);
    } catch (RuntimeException failure) {
      return problem(request, messages, locale, 500, "server.internal", true, null);
    }
  }

  private HttpResponse list(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws FileOperationException, RequestProblem {
    String path = requiredQuery(query, "path");
    int depth = intQuery(query, "depth", DEFAULT_DEPTH, 1, MAX_DEPTH);
    int limit = clampedIntQuery(query, "limit", DEFAULT_LIST_LIMIT, 1, MAX_LIST_LIMIT);
    int offset = 0;
    String cursor = optionalQuery(query, "cursor");
    if (cursor != null) {
      offset = decodeCursor(cursor, path, depth, limit);
    }
    List<FileInfo> entries = collect(path, depth);
    int end = Math.min(entries.size(), offset + limit);
    List<FileInfo> page = entries.subList(Math.min(offset, entries.size()), end);
    String nextCursor = end < entries.size() ? encodeCursor(path, depth, limit, end) : null;
    StringBuilder body = new StringBuilder();
    body.append("{\"path\":").append(quote(path));
    body.append(",\"items\":[");
    for (int index = 0; index < page.size(); index++) {
      if (index > 0) {
        body.append(',');
      }
      body.append(fileEntryJson(page.get(index)));
    }
    body.append("],\"nextCursor\":").append(nextCursor == null ? "null" : quote(nextCursor));
    body.append(",\"total\":").append(entries.size()).append('}');
    return json(request, 200, body.toString());
  }

  private HttpResponse create(
      HttpRequest request, Locale locale, TransportMessages messages, byte[] body)
      throws RequestProblem, FileOperationException {
    requireMediaType(request, "application/json", messages, locale);
    Object parsed = RequestJson.parse(body, maximumBodyBytes);
    io.github.woxlo1.deploymc.core.config.YamlValue root =
        (io.github.woxlo1.deploymc.core.config.YamlValue) parsed;
    RequestJson.rejectUnknownFields(root, "path", "kind", "content");
    String path = RequestJson.requiredString(root, "path", MAX_PATH_LENGTH);
    String kind = RequestJson.requiredString(root, "kind", 16);
    ValidatedFileResult result;
    if ("file".equals(kind)) {
      String content = RequestJson.requiredString(root, "content", maximumBodyBytes);
      result = files.createValidated(path, content.getBytes(StandardCharsets.UTF_8));
    } else if ("directory".equals(kind)) {
      if (root.get("content") != null) {
        throw RequestProblem.invalid();
      }
      result = new ValidatedFileResult(files.mkdir(path), null);
    } else {
      throw RequestProblem.invalid();
    }
    return writeResult(request, locale, 201, result);
  }

  private HttpResponse delete(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, FileOperationException {
    String path = requiredQuery(query, "path");
    boolean recursive = booleanQuery(query, "recursive", false);
    Long expectedCount = null;
    String count = optionalQuery(query, "expectedEntryCount");
    if (count != null) {
      expectedCount = parsePositiveLong(count);
      if (expectedCount == null) {
        throw RequestProblem.invalid();
      }
    }
    if (recursive && expectedCount == null) {
      throw RequestProblem.invalid();
    }
    String expectedRevision = revision(request.header("if-match"));
    DeleteRequest deletion =
        recursive
            ? DeleteRequest.recursive(expectedCount.longValue(), expectedRevision)
            : DeleteRequest.single(expectedRevision);
    files.delete(path, deletion);
    return withRequestId(HttpResponse.status(204), request);
  }

  private HttpResponse readContent(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, FileOperationException {
    String path = requiredQuery(query, "path");
    FileInfo info = files.stat(path);
    String etag = etag(info.revision());
    String noneMatch = request.header("if-none-match");
    if (noneMatch != null && matchesIfNoneMatch(noneMatch, info.revision())) {
      return withRequestId(HttpResponse.status(304).withHeader("ETag", etag), request);
    }
    boolean partial = request.header("range") != null;
    ReadRange range = parseRange(request.header("range"), info.size());
    FileContent content = files.read(path, range);
    HttpResponse response =
        HttpResponse.of(partial ? 206 : 200, "application/octet-stream", content.content())
            .withHeader("ETag", etag(content.revision()))
            .withHeader("Accept-Ranges", "bytes");
    if (partial) {
      response = response.withHeader("Content-Range", contentRange(content));
    }
    return withRequestId(response, request);
  }

  private HttpResponse writeContent(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, FileOperationException {
    requireMediaType(request, "application/octet-stream", messages, locale);
    String path = requiredQuery(query, "path");
    boolean existed = exists(path);
    String expectedRevision = revision(request.header("if-match"));
    byte[] body = request.body();
    if (body.length > maximumBodyBytes) {
      throw RequestProblem.tooLarge();
    }
    ValidatedFileResult result = files.writeValidated(path, body, expectedRevision);
    return writeResult(request, locale, existed ? 200 : 201, result);
  }

  private HttpResponse upload(
      HttpRequest request, Locale locale, TransportMessages messages, Map<String, String> query)
      throws RequestProblem, FileOperationException {
    requireMediaType(request, "application/octet-stream", messages, locale);
    String path = requiredQuery(query, "path");
    if (request.body().length > maximumBodyBytes) {
      throw RequestProblem.tooLarge();
    }
    boolean existed = exists(path);
    String expectedRevision = revision(request.header("if-match"));
    byte[] body = request.body();
    ValidatedFileResult result =
        files.uploadValidated(path, new ByteArrayInputStream(body), body.length, expectedRevision);
    return writeResult(request, locale, existed ? 200 : 201, result);
  }

  private HttpResponse move(
      HttpRequest request, Locale locale, TransportMessages messages, byte[] body)
      throws RequestProblem, FileOperationException {
    requireMediaType(request, "application/json", messages, locale);
    io.github.woxlo1.deploymc.core.config.YamlValue root =
        RequestJson.parse(body, maximumBodyBytes);
    RequestJson.rejectUnknownFields(root, "from", "to", "overwrite");
    String from = RequestJson.requiredString(root, "from", MAX_PATH_LENGTH);
    String to = RequestJson.requiredString(root, "to", MAX_PATH_LENGTH);
    boolean overwrite = RequestJson.optionalBoolean(root, "overwrite", false);
    FileInfo moved = files.move(from, to, overwrite, revision(request.header("if-match")));
    HttpResponse response = withRequestId(HttpResponse.json(200, fileEntryJson(moved)), request);
    if (moved.revision() != null) {
      response = response.withHeader("ETag", etag(moved.revision()));
    }
    return response;
  }

  private HttpResponse validate(
      HttpRequest request, Locale locale, TransportMessages messages, byte[] body)
      throws RequestProblem, FileOperationException {
    requireMediaType(request, "application/json", messages, locale);
    io.github.woxlo1.deploymc.core.config.YamlValue root =
        RequestJson.parse(body, maximumBodyBytes);
    RequestJson.rejectUnknownFields(root, "path", "content");
    String path = RequestJson.requiredString(root, "path", MAX_PATH_LENGTH);
    String content = RequestJson.requiredString(root, "content", maximumBodyBytes);
    try {
      resolver.resolve(path, SandboxAccessMode.READ);
    } catch (SandboxRejectionException rejected) {
      throw sandboxFailure(path, rejected);
    }
    try {
      ValidationResult result = validators.validate(path, content.getBytes(StandardCharsets.UTF_8));
      if (!result.valid()) {
        return problem(
            request,
            messages,
            locale,
            422,
            "validation.failed",
            false,
            findings(locale, result.issues()));
      }
      return json(request, 200, validationJson(locale, result));
    } catch (ValidationLimitException limit) {
      return validationLimitProblem(request, locale, messages, limit);
    } catch (ValidationException failure) {
      return problem(
          request, messages, locale, 503, "validation.validator-unavailable", true, null);
    }
  }

  private HttpResponse writeResult(
      HttpRequest request, Locale locale, int status, ValidatedFileResult result) {
    FileInfo entry = result.file();
    HttpResponse response =
        HttpResponse.json(
            status,
            "{\"entry\":"
                + fileEntryJson(entry)
                + ",\"validation\":"
                + validationJson(locale, result.validation())
                + "}");
    response = withRequestId(response, request);
    String revision = entry.revision();
    if (revision != null) {
      response = response.withHeader("ETag", etag(revision));
    }
    if (status == 201) {
      response = response.withHeader("Location", location(entry));
    }
    return response;
  }

  private HttpResponse requestProblem(
      HttpRequest request, Locale locale, TransportMessages messages, RequestProblem failure) {
    return problem(request, messages, locale, failure.status(), failure.code(), false, null);
  }

  private HttpResponse fileProblem(
      HttpRequest request,
      Locale locale,
      TransportMessages messages,
      FileOperationException failure) {
    int status = status(failure.reason());
    String pointer = failure.parameters().isEmpty() ? "" : failure.parameters().get(0);
    List<ProblemIssue> issues = null;
    if (failure.issueCode() != null) {
      issues =
          Collections.singletonList(
              ProblemIssue.of(
                  pointer,
                  failure.issueCode(),
                  issueDetail(locale, messages, failure.issueCode())));
    } else if (failure.reason() == FileOperationException.Reason.VALIDATION_FAILED) {
      issues = gateIssues(locale, failure, pointer);
    }
    return problem(
        request, messages, locale, status, failure.code(), retryable(failure.reason()), issues);
  }

  private HttpResponse validationLimitProblem(
      HttpRequest request,
      Locale locale,
      TransportMessages messages,
      ValidationLimitException limit) {
    List<ProblemIssue> issues = null;
    if (limit.issueCode() != null) {
      issues =
          Collections.singletonList(
              ProblemIssue.of(
                  "", limit.issueCode(), issueDetail(locale, messages, limit.issueCode())));
    }
    return problem(request, messages, locale, 413, "validation.limit-exceeded", false, issues);
  }

  private List<ProblemIssue> gateIssues(
      Locale locale, FileOperationException failure, String pointer) {
    List<String> parameters = failure.parameters();
    if (parameters.size() < 2) {
      return null;
    }
    List<ProblemIssue> issues = new ArrayList<ProblemIssue>();
    for (String code : parameters.get(1).split(",")) {
      if (code.isEmpty()) {
        continue;
      }
      issues.add(
          ProblemIssue.of(
              pointer,
              code,
              messages(locale)
                  .format(
                      MessageKeys.ISSUE_VALIDATION_FAILED,
                      Collections.<String, Object>singletonMap("code", code))));
    }
    return issues.isEmpty() ? null : issues;
  }

  private List<ProblemIssue> findings(Locale locale, List<ValidationIssue> issues) {
    List<ProblemIssue> result = new ArrayList<ProblemIssue>();
    for (ValidationIssue issue : issues) {
      result.add(
          ProblemIssue.finding(
              issue.pointer(),
              issue.code(),
              ValidationMessages.detail(locale, issue),
              issue.severity().name(),
              issue.line(),
              issue.column(),
              issue.offset(),
              issue.parameters()));
    }
    return result;
  }

  private static String issueDetail(Locale locale, TransportMessages messages, String issueCode) {
    return messages.format(
        MessageKeys.ISSUE_VALIDATION_LIMIT,
        Collections.<String, Object>singletonMap("code", issueCode));
  }

  private HttpResponse problem(
      HttpRequest request,
      TransportMessages messages,
      Locale locale,
      int status,
      String code,
      boolean retryable,
      List<ProblemIssue> issues) {
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
    HttpResponse response =
        problem(request, messages, locale, 405, "request.method-not-allowed", false, null);
    return response.withHeader("Allow", allow);
  }

  private static HttpResponse json(HttpRequest request, int status, String body) {
    return withRequestId(HttpResponse.json(status, body), request);
  }

  private static HttpResponse withRequestId(HttpResponse response, HttpRequest request) {
    return response.withHeader("X-Request-Id", request.id());
  }

  private List<FileInfo> collect(String path, int maximumDepth) throws FileOperationException {
    List<FileInfo> entries = new ArrayList<FileInfo>();
    Set<String> visited = new LinkedHashSet<String>();
    collect(path, 1, maximumDepth, entries, visited);
    return Collections.unmodifiableList(entries);
  }

  private void collect(
      String path, int depth, int maximumDepth, List<FileInfo> entries, Set<String> visited)
      throws FileOperationException {
    if (!visited.add(path)) {
      return;
    }
    for (FileInfo entry : files.list(path)) {
      entries.add(entry);
      if (entries.size() > MAX_LIST_ENTRIES) {
        throw new FileOperationException(
            FileOperationException.Reason.TOO_LARGE, path, Integer.valueOf(MAX_LIST_ENTRIES));
      }
      if (entry.directory() && depth < maximumDepth) {
        collect(entry.relativePath(), depth + 1, maximumDepth, entries, visited);
      }
    }
  }

  private boolean exists(String path) throws FileOperationException {
    try {
      files.stat(path);
      return true;
    } catch (FileOperationException failure) {
      if (failure.reason() == FileOperationException.Reason.NOT_FOUND) {
        return false;
      }
      throw failure;
    }
  }

  private static String fileEntryJson(FileInfo entry) {
    StringBuilder body = new StringBuilder();
    body.append("{\"path\":").append(quote(entry.relativePath()));
    body.append(",\"kind\":").append(quote(entry.directory() ? "directory" : "file"));
    body.append(",\"size\":").append(entry.directory() ? 0L : entry.size());
    body.append(",\"revision\":")
        .append(entry.revision() == null ? "null" : quote(entry.revision()));
    body.append(",\"lastModified\":")
        .append(quote(Instant.ofEpochMilli(entry.lastModifiedMillis()).toString()));
    return body.append('}').toString();
  }

  private static String validationJson(Locale locale, ValidationResult result) {
    if (result == null) {
      return "null";
    }
    StringBuilder body = new StringBuilder();
    body.append("{\"path\":").append(quote(result.path()));
    body.append(",\"matched\":").append(result.matched());
    body.append(",\"format\":").append(result.format() == null ? "null" : quote(result.format()));
    body.append(",\"valid\":").append(result.valid());
    body.append(",\"issues\":[");
    for (int index = 0; index < result.issues().size(); index++) {
      if (index > 0) {
        body.append(',');
      }
      ValidationIssue issue = result.issues().get(index);
      body.append("{\"severity\":").append(quote(issue.severity().name()));
      body.append(",\"code\":").append(quote(issue.code()));
      body.append(",\"pointer\":").append(quote(issue.pointer()));
      body.append(",\"line\":").append(issue.line());
      body.append(",\"column\":").append(issue.column());
      body.append(",\"offset\":").append(issue.offset());
      body.append(",\"detail\":").append(quote(ValidationMessages.detail(locale, issue)));
      body.append(",\"parameters\":[");
      for (int parameter = 0; parameter < issue.parameters().size(); parameter++) {
        if (parameter > 0) {
          body.append(',');
        }
        body.append(quote(issue.parameters().get(parameter)));
      }
      body.append("]}");
    }
    return body.append("]}").toString();
  }

  private static String location(FileInfo entry) {
    String path =
        entry.directory()
            ? FILES_PATH + "?path=" + encodeQuery(entry.relativePath())
            : CONTENT_PATH + "?path=" + encodeQuery(entry.relativePath());
    return path;
  }

  private static String encodeQuery(String value) {
    StringBuilder result = new StringBuilder();
    for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
      int octet = item & 0xff;
      if ((octet >= 'a' && octet <= 'z')
          || (octet >= 'A' && octet <= 'Z')
          || (octet >= '0' && octet <= '9')
          || octet == '-'
          || octet == '_'
          || octet == '.'
          || octet == '~') {
        result.append((char) octet);
      } else {
        result.append('%');
        result.append(Character.forDigit(octet >> 4, 16));
        result.append(Character.forDigit(octet & 0x0f, 16));
      }
    }
    return result.toString();
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

  private static String requiredQuery(Map<String, String> query, String name)
      throws RequestProblem {
    String value = query.get(name);
    if (value == null || value.length() > MAX_PATH_LENGTH) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static String optionalQuery(Map<String, String> query, String name)
      throws RequestProblem {
    String value = query.get(name);
    if (value != null && value.length() > MAX_PATH_LENGTH) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static int clampedIntQuery(
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

  private static int intQuery(
      Map<String, String> query, String name, int fallback, int minimum, int maximum)
      throws RequestProblem {
    String value = query.get(name);
    if (value == null) {
      return fallback;
    }
    try {
      int number = Integer.parseInt(value);
      if (number < minimum || number > maximum) {
        throw RequestProblem.invalid();
      }
      return number;
    } catch (NumberFormatException invalid) {
      throw RequestProblem.invalid();
    }
  }

  private static boolean booleanQuery(Map<String, String> query, String name, boolean fallback)
      throws RequestProblem {
    String value = query.get(name);
    if (value == null) {
      return fallback;
    }
    if ("true".equals(value)) {
      return true;
    }
    if ("false".equals(value)) {
      return false;
    }
    throw RequestProblem.invalid();
  }

  private static Long parsePositiveLong(String value) throws RequestProblem {
    try {
      long number = Long.parseLong(value);
      return number > 0L ? Long.valueOf(number) : null;
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private static void requireMediaType(
      HttpRequest request, String expected, TransportMessages messages, Locale locale)
      throws RequestProblem {
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
    if (!REVISION.matcher(value).matches()) {
      throw RequestProblem.invalid();
    }
    return value;
  }

  private static boolean matchesIfNoneMatch(String header, String revision) throws RequestProblem {
    if ("*".equals(header.trim())) {
      return true;
    }
    return revision.equals(revision(header));
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

  private static String contentRange(FileContent content) {
    long last = content.rangeStart() + content.content().length - 1L;
    return "bytes " + content.rangeStart() + "-" + last + "/" + content.totalSize();
  }

  private static String etag(String revision) {
    return revision == null ? null : '"' + revision + '"';
  }

  private static String encodeCursor(String path, int depth, int limit, int offset) {
    String payload = path + '\u0000' + depth + '\u0000' + limit + '\u0000' + offset;
    byte[] digest = digest(payload.getBytes(StandardCharsets.UTF_8));
    byte[] encoded = new byte[8 + payload.getBytes(StandardCharsets.UTF_8).length];
    System.arraycopy(digest, 0, encoded, 0, 8);
    byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(payloadBytes, 0, encoded, 8, payloadBytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
  }

  private static int decodeCursor(String cursor, String path, int depth, int limit)
      throws RequestProblem {
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
    String text = new String(payload, StandardCharsets.UTF_8);
    String[] parts = text.split("\\u0000", -1);
    if (parts.length != 4
        || !parts[0].equals(path)
        || !parts[1].equals(String.valueOf(depth))
        || !parts[2].equals(String.valueOf(limit))) {
      throw RequestProblem.invalidCursor();
    }
    try {
      int offset = Integer.parseInt(parts[3]);
      if (offset < 0 || offset > MAX_LIST_ENTRIES) {
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

  private static FileOperationException sandboxFailure(
      String path, SandboxRejectionException rejection) {
    return FileOperationException.fromSandbox(rejection, path);
  }

  private static int status(FileOperationException.Reason reason) {
    switch (reason) {
      case PATH_REJECTED:
      case NOT_DIRECTORY:
      case INVALID_RANGE:
      case INVALID_OPERATION:
        return 400;
      case PROTECTED:
        return 403;
      case NOT_FOUND:
        return 404;
      case ALREADY_EXISTS:
      case DIRECTORY_NOT_EMPTY:
      case DELETE_CONFIRMATION:
      case SNAPSHOT_REQUIRED:
        return 409;
      case REVISION_CONFLICT:
        return 412;
      case TOO_LARGE:
      case DELETE_LIMIT:
      case VALIDATION_LIMIT:
        return 413;
      case PRECONDITION_REQUIRED:
        return 428;
      case VALIDATION_FAILED:
        return 422;
      case SNAPSHOT_FAILED:
      case VALIDATOR_UNAVAILABLE:
        return 503;
      case ATOMIC_MOVE_UNSUPPORTED:
      case IO:
      default:
        return 500;
    }
  }

  private static boolean retryable(FileOperationException.Reason reason) {
    return reason == FileOperationException.Reason.SNAPSHOT_FAILED
        || reason == FileOperationException.Reason.VALIDATOR_UNAVAILABLE
        || reason == FileOperationException.Reason.IO;
  }

  private static String titleKey(String code) {
    switch (code) {
      case "request.invalid":
        return MessageKeys.REQUEST_INVALID_TITLE;
      case "request.unsupported-media-type":
        return MessageKeys.REQUEST_UNSUPPORTED_MEDIA_TYPE_TITLE;
      case "request.too-large":
        return MessageKeys.REQUEST_TOO_LARGE_TITLE;
      case "request.invalid-cursor":
        return MessageKeys.REQUEST_INVALID_CURSOR_TITLE;
      case "request.method-not-allowed":
        return MessageKeys.REQUEST_METHOD_NOT_ALLOWED_TITLE;
      case "request.precondition-required":
        return MessageKeys.REQUEST_PRECONDITION_REQUIRED_TITLE;
      case "files.path-rejected":
        return MessageKeys.FILES_PATH_REJECTED_TITLE;
      case "files.not-found":
        return MessageKeys.FILES_NOT_FOUND_TITLE;
      case "files.not-directory":
        return MessageKeys.FILES_NOT_DIRECTORY_TITLE;
      case "files.already-exists":
        return MessageKeys.FILES_ALREADY_EXISTS_TITLE;
      case "files.revision-conflict":
        return MessageKeys.FILES_REVISION_CONFLICT_TITLE;
      case "files.too-large":
        return MessageKeys.FILES_TOO_LARGE_TITLE;
      case "files.protected":
        return MessageKeys.FILES_PROTECTED_TITLE;
      case "files.invalid-range":
        return MessageKeys.FILES_INVALID_RANGE_TITLE;
      case "files.directory-not-empty":
        return MessageKeys.FILES_DIRECTORY_NOT_EMPTY_TITLE;
      case "files.delete-confirmation-required":
        return MessageKeys.FILES_DELETE_CONFIRMATION_TITLE;
      case "files.delete-limit-exceeded":
        return MessageKeys.FILES_DELETE_LIMIT_TITLE;
      case "files.snapshot-required":
        return MessageKeys.FILES_SNAPSHOT_REQUIRED_TITLE;
      case "files.snapshot-failed":
        return MessageKeys.FILES_SNAPSHOT_FAILED_TITLE;
      case "files.atomic-move-unsupported":
        return MessageKeys.FILES_ATOMIC_MOVE_UNSUPPORTED_TITLE;
      case "files.invalid-operation":
        return MessageKeys.FILES_INVALID_OPERATION_TITLE;
      case "files.io":
        return MessageKeys.FILES_IO_TITLE;
      case "validation.failed":
        return MessageKeys.VALIDATION_FAILED_TITLE;
      case "validation.limit-exceeded":
        return MessageKeys.VALIDATION_LIMIT_TITLE;
      case "validation.validator-unavailable":
        return MessageKeys.VALIDATOR_UNAVAILABLE_TITLE;
      case "server.internal":
      default:
        return MessageKeys.SERVER_INTERNAL_TITLE;
    }
  }

  private static String detailKey(String code) {
    switch (code) {
      case "request.invalid":
        return MessageKeys.REQUEST_INVALID_DETAIL;
      case "request.unsupported-media-type":
        return MessageKeys.REQUEST_UNSUPPORTED_MEDIA_TYPE_DETAIL;
      case "request.too-large":
        return MessageKeys.REQUEST_TOO_LARGE_DETAIL;
      case "request.invalid-cursor":
        return MessageKeys.REQUEST_INVALID_CURSOR_DETAIL;
      case "request.method-not-allowed":
        return MessageKeys.REQUEST_METHOD_NOT_ALLOWED_DETAIL;
      case "request.precondition-required":
        return MessageKeys.REQUEST_PRECONDITION_REQUIRED_DETAIL;
      case "files.path-rejected":
        return MessageKeys.FILES_PATH_REJECTED_DETAIL;
      case "files.not-found":
        return MessageKeys.FILES_NOT_FOUND_DETAIL;
      case "files.not-directory":
        return MessageKeys.FILES_NOT_DIRECTORY_DETAIL;
      case "files.already-exists":
        return MessageKeys.FILES_ALREADY_EXISTS_DETAIL;
      case "files.revision-conflict":
        return MessageKeys.FILES_REVISION_CONFLICT_DETAIL;
      case "files.too-large":
        return MessageKeys.FILES_TOO_LARGE_DETAIL;
      case "files.protected":
        return MessageKeys.FILES_PROTECTED_DETAIL;
      case "files.invalid-range":
        return MessageKeys.FILES_INVALID_RANGE_DETAIL;
      case "files.directory-not-empty":
        return MessageKeys.FILES_DIRECTORY_NOT_EMPTY_DETAIL;
      case "files.delete-confirmation-required":
        return MessageKeys.FILES_DELETE_CONFIRMATION_DETAIL;
      case "files.delete-limit-exceeded":
        return MessageKeys.FILES_DELETE_LIMIT_DETAIL;
      case "files.snapshot-required":
        return MessageKeys.FILES_SNAPSHOT_REQUIRED_DETAIL;
      case "files.snapshot-failed":
        return MessageKeys.FILES_SNAPSHOT_FAILED_DETAIL;
      case "files.atomic-move-unsupported":
        return MessageKeys.FILES_ATOMIC_MOVE_UNSUPPORTED_DETAIL;
      case "files.invalid-operation":
        return MessageKeys.FILES_INVALID_OPERATION_DETAIL;
      case "files.io":
        return MessageKeys.FILES_IO_DETAIL;
      case "validation.failed":
        return MessageKeys.VALIDATION_FAILED_DETAIL;
      case "validation.limit-exceeded":
        return MessageKeys.VALIDATION_LIMIT_DETAIL;
      case "validation.validator-unavailable":
        return MessageKeys.VALIDATOR_UNAVAILABLE_DETAIL;
      case "server.internal":
      default:
        return MessageKeys.SERVER_INTERNAL_DETAIL;
    }
  }

  private static TransportMessages messages(Locale locale) {
    return TransportMessages.of(locale);
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
