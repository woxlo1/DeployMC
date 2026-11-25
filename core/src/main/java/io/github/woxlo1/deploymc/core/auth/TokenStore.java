package io.github.woxlo1.deploymc.core.auth;

import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Loads and writes token records under a host-controlled directory.
 *
 * <p>The directory is reached only through an explicit {@link Path} supplied by the platform
 * adapter. No API request can choose, read, or write a provisioning file. An empty but valid
 * directory means authentication is configured and no token has been created yet.
 */
public final class TokenStore {

  private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
  private static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}\\.json$");
  private static final Pattern TEMP_FILE =
      Pattern.compile("^\\.token-[A-Za-z0-9_-]{1,64}-[A-Za-z0-9_-]+\\.tmp$");
  private static final Set<String> ALLOWED_KEYS =
      Collections.unmodifiableSet(
          new HashSet<String>(
              Arrays.asList(
                  "id", "label", "secretHash", "expiresAt", "scopes", "revokedAt", "lastUsedAt")));
  private static final int MAX_FILE_BYTES = 16 * 1024;
  private static final int MAX_FILES = 1024;

  private final Path directory;
  private final Object writeLock = new Object();
  private final CopyOnWriteArrayList<TokenRecord> records;
  private final Map<String, byte[]> contents = new HashMap<String, byte[]>();

  private TokenStore(Path directory, List<ParsedRecord> parsedRecords) {
    this.directory = directory;
    List<TokenRecord> loaded = new ArrayList<TokenRecord>();
    for (ParsedRecord parsed : parsedRecords) {
      loaded.add(parsed.record);
      contents.put(parsed.record.id(), parsed.bytes);
    }
    this.records = new CopyOnWriteArrayList<TokenRecord>(loaded);
  }

  /** Ensures the provisioning directory exists and loads every record in it. */
  public static TokenStore load(Path directory) throws TokenStoreException {
    Objects.requireNonNull(directory, "directory");
    Path absolute = directory.toAbsolutePath().normalize();
    try {
      if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
          throw new TokenStoreException(
              TokenStoreException.Reason.DIRECTORY, "token directory is not a directory");
        }
      } else {
        Files.createDirectories(absolute);
        restrictPermissions(absolute, "rwx------");
      }
    } catch (IOException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.DIRECTORY, "cannot prepare token directory", exception);
    }

    List<Path> files = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(absolute)) {
      for (Path entry : stream) {
        String name = entry.getFileName().toString();
        if (TEMP_FILE.matcher(name).matches()) {
          if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(entry);
          }
          continue;
        }
        if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
          files.add(entry);
        }
      }
    } catch (IOException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.DIRECTORY, "cannot list token directory", exception);
    }
    Collections.sort(files);
    if (files.size() > MAX_FILES) {
      throw new TokenStoreException(
          TokenStoreException.Reason.DIRECTORY, "token directory contains too many files");
    }

    List<ParsedRecord> parsedRecords = new ArrayList<ParsedRecord>();
    Set<String> ids = new HashSet<String>();
    Set<String> hashes = new HashSet<String>();
    for (Path file : files) {
      String name = file.getFileName().toString();
      if (!FILE_NAME.matcher(name).matches()) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "unexpected file name in token directory: " + name);
      }
      ParsedRecord parsed = parse(file, name.substring(0, name.length() - 5));
      if (!ids.add(parsed.record.id()) || !hashes.add(hex(parsed.record.secretHash()))) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "duplicate token id or hash: " + name);
      }
      parsedRecords.add(parsed);
    }
    return new TokenStore(absolute, parsedRecords);
  }

  public Path directory() {
    return directory;
  }

  /** Snapshot of records. Expired and revoked records remain so callers can report their state. */
  public List<TokenRecord> records() {
    return Collections.unmodifiableList(new ArrayList<TokenRecord>(records));
  }

  /** Creates a new record file. Fails if the id already exists; never overwrites a token. */
  public void write(TokenRecord record) throws TokenStoreException {
    Objects.requireNonNull(record, "record");
    synchronized (writeLock) {
      if (!ID.matcher(record.id()).matches()) {
        throw new TokenStoreException(TokenStoreException.Reason.RECORD, "invalid token id");
      }
      for (TokenRecord existing : records) {
        if (existing.id().equals(record.id()) || existing.matches(record.secretHash())) {
          throw new TokenStoreException(
              TokenStoreException.Reason.RECORD, "duplicate token id or hash");
        }
      }
      byte[] bytes = serialize(record);
      Path target = directory.resolve(record.id() + ".json");
      Path temporary = null;
      try {
        temporary = Files.createTempFile(directory, ".token-" + record.id() + "-", ".tmp");
        restrictPermissions(temporary, "rw-------");
        Files.write(
            temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.createLink(target, temporary);
        records.add(record);
        contents.put(record.id(), bytes);
      } catch (IOException exception) {
        throw new TokenStoreException(
            TokenStoreException.Reason.IO, "cannot write token record", exception);
      } finally {
        deleteTemporary(temporary);
      }
    }
  }

  /** Marks a token revoked. Revocation is idempotent and never clears an existing timestamp. */
  public TokenRecord revoke(String id, Instant timestamp) throws TokenStoreException {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(timestamp, "timestamp");
    synchronized (writeLock) {
      TokenRecord stored = requireRecord(id);
      if (stored.isRevoked()) {
        return stored;
      }
      if (stored.lastUsedAt() != null && timestamp.isBefore(stored.lastUsedAt())) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "revocation cannot precede last use");
      }
      return replace(stored, stored.withRevokedAt(timestamp));
    }
  }

  /** Persists a successful authentication time. Earlier or unchanged timestamps are ignored. */
  public TokenRecord updateLastUsed(TokenRecord current, Instant timestamp)
      throws TokenStoreException {
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(timestamp, "timestamp");
    synchronized (writeLock) {
      TokenRecord stored = requireRecord(current.id());
      if (!stored.matches(current.secretHash())) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "token record changed before last-use write");
      }
      if (stored.isRevoked()
          || stored.lastUsedAt() != null && !timestamp.isAfter(stored.lastUsedAt())) {
        return stored;
      }
      return replace(stored, stored.withLastUsedAt(timestamp));
    }
  }

  private TokenRecord requireRecord(String id) throws TokenStoreException {
    for (TokenRecord record : records) {
      if (record.id().equals(id)) {
        return record;
      }
    }
    throw new TokenStoreException(TokenStoreException.Reason.RECORD, "unknown token id");
  }

  /** Re-reads and compares the target before replacing it with a bounded atomic move. */
  private TokenRecord replace(TokenRecord expected, TokenRecord updated)
      throws TokenStoreException {
    int index = -1;
    for (int position = 0; position < records.size(); position++) {
      if (records.get(position).id().equals(expected.id())) {
        index = position;
        break;
      }
    }
    if (index < 0) {
      throw new TokenStoreException(TokenStoreException.Reason.RECORD, "unknown token id");
    }
    byte[] expectedBytes = contents.get(expected.id());
    if (expectedBytes == null) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "missing token record contents");
    }
    Path target = directory.resolve(expected.id() + ".json");
    Path temporary = null;
    try {
      ParsedRecord current = parse(target, expected.id());
      if (!Arrays.equals(expectedBytes, current.bytes)) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "token record changed concurrently");
      }
      byte[] updatedBytes = serialize(updated);
      temporary = Files.createTempFile(directory, ".token-" + expected.id() + "-", ".tmp");
      restrictPermissions(temporary, "rw-------");
      Files.write(
          temporary, updatedBytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      if (!Arrays.equals(expectedBytes, Files.readAllBytes(target))) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "token record changed during metadata write");
      }
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      records.set(index, updated);
      contents.put(updated.id(), updatedBytes);
      return updated;
    } catch (IOException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.IO, "cannot update token record", exception);
    } finally {
      deleteTemporary(temporary);
    }
  }

  private static ParsedRecord parse(Path file, String expectedId) throws TokenStoreException {
    String name = file.getFileName().toString();
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    } catch (IOException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.IO, "cannot read token record: " + name, exception);
    }
    if (bytes.length > MAX_FILE_BYTES) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token record is too large: " + name);
    }
    YamlValue root;
    try {
      root = ConfigYamlParser.parse(new String(bytes, StandardCharsets.UTF_8));
    } catch (ConfigException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "invalid token record JSON: " + name, exception);
    }
    if (!root.isObject()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token record must be an object: " + name);
    }
    for (String key : root.keys()) {
      if (!ALLOWED_KEYS.contains(key)) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "unknown field in token record: " + name);
      }
    }
    YamlValue id = required(root, "id", name);
    YamlValue label = required(root, "label", name);
    YamlValue hash = required(root, "secretHash", name);
    if (!id.isString() || !label.isString() || !hash.isString()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "id, label, and secretHash must be strings: " + name);
    }
    if (!expectedId.equals(id.raw().toString()) || !ID.matcher(id.raw().toString()).matches()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token id must match its file name: " + name);
    }
    if (label.raw().toString().length() > 80) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token label is too long: " + name);
    }
    byte[] digest = decodeHash(hash.raw().toString(), name);
    Instant expiresAt = parseTimestamp(root.get("expiresAt"), "expiresAt", name);
    Instant revokedAt = parseTimestamp(root.get("revokedAt"), "revokedAt", name);
    Instant lastUsedAt = parseTimestamp(root.get("lastUsedAt"), "lastUsedAt", name);
    List<String> scopes = parseScopes(root.get("scopes"), name);
    TokenRecord record =
        new TokenRecord(
            id.raw().toString(),
            label.raw().toString(),
            digest,
            expiresAt,
            scopes,
            revokedAt,
            lastUsedAt);
    return new ParsedRecord(record, bytes);
  }

  private static List<String> parseScopes(YamlValue value, String name) throws TokenStoreException {
    if (value == null || value.isNull()) {
      return Collections.emptyList();
    }
    if (!value.isArray()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "scopes must be an array: " + name);
    }
    List<YamlValue> items = value.items();
    if (items.size() > 16) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "too many token scopes: " + name);
    }
    Set<String> unique = new LinkedHashSet<String>();
    for (YamlValue item : items) {
      if (!item.isString() || !TokenRecord.isValidScope(item.raw().toString())) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "invalid token scope: " + name);
      }
      if (!unique.add(item.raw().toString())) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "duplicate token scope: " + name);
      }
    }
    return new ArrayList<String>(unique);
  }

  private static Instant parseTimestamp(YamlValue value, String field, String name)
      throws TokenStoreException {
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isString()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, field + " must be a quoted string: " + name);
    }
    String text = value.raw().toString();
    if (!text.endsWith("Z")) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, field + " must be UTC: " + name);
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "invalid " + field + " timestamp: " + name, exception);
    }
  }

  private static YamlValue required(YamlValue root, String key, String name)
      throws TokenStoreException {
    YamlValue value = root.get(key);
    if (value == null || value.isNull()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "missing " + key + " in token record: " + name);
    }
    return value;
  }

  private static byte[] decodeHash(String text, String name) throws TokenStoreException {
    if (text.length() != 64 || !text.equals(text.toLowerCase(Locale.ROOT))) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "secretHash must be 64 lowercase hex digits: " + name);
    }
    byte[] result = new byte[32];
    for (int index = 0; index < result.length; index++) {
      int high = Character.digit(text.charAt(index * 2), 16);
      int low = Character.digit(text.charAt(index * 2 + 1), 16);
      if (high < 0 || low < 0) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "secretHash is not hexadecimal: " + name);
      }
      result[index] = (byte) ((high << 4) | low);
    }
    return result;
  }

  private static byte[] serialize(TokenRecord record) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"id\": ").append(quote(record.id())).append(",\n");
    json.append("  \"label\": ").append(quote(record.label())).append(",\n");
    json.append("  \"secretHash\": ").append(quote(hex(record.secretHash())));
    if (record.expiresAt() != null) {
      json.append(",\n  \"expiresAt\": ").append(quote(record.expiresAt().toString()));
    }
    if (!record.scopes().isEmpty()) {
      json.append(",\n  \"scopes\": [");
      for (int index = 0; index < record.scopes().size(); index++) {
        if (index > 0) {
          json.append(", ");
        }
        json.append(quote(record.scopes().get(index)));
      }
      json.append(']');
    }
    if (record.revokedAt() != null) {
      json.append(",\n  \"revokedAt\": ").append(quote(record.revokedAt().toString()));
    }
    if (record.lastUsedAt() != null) {
      json.append(",\n  \"lastUsedAt\": ").append(quote(record.lastUsedAt().toString()));
    }
    json.append("\n}\n");
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value >> 4) & 0xf, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }

  private static String quote(String value) {
    StringBuilder result = new StringBuilder(value.length() + 2);
    result.append('"');
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
    return result.append('"').toString();
  }

  private static void deleteTemporary(Path temporary) {
    if (temporary == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException ignored) {
      // A leftover metadata-only temp file is removed by the next load.
    }
  }

  private static void restrictPermissions(Path path, String mode) throws IOException {
    if (!Files.getFileStore(path).supportsFileAttributeView("posix")) {
      return;
    }
    Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(mode);
    Files.setPosixFilePermissions(path, permissions);
  }

  private static final class ParsedRecord {
    private final TokenRecord record;
    private final byte[] bytes;

    private ParsedRecord(TokenRecord record, byte[] bytes) {
      this.record = record;
      this.bytes = bytes;
    }
  }
}
