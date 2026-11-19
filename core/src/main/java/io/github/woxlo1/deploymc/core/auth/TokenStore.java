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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
  private static final Set<String> ALLOWED_KEYS =
      Collections.unmodifiableSet(
          new HashSet<String>(Arrays.asList("id", "label", "secretHash", "expiresAt")));
  private static final int MAX_FILE_BYTES = 16 * 1024;
  private static final int MAX_FILES = 1024;

  private final Path directory;
  private final CopyOnWriteArrayList<TokenRecord> records;

  private TokenStore(Path directory, List<TokenRecord> records) {
    this.directory = directory;
    this.records = new CopyOnWriteArrayList<TokenRecord>(records);
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

    List<TokenRecord> records = new ArrayList<TokenRecord>();
    Set<String> ids = new HashSet<String>();
    Set<String> hashes = new HashSet<String>();
    for (Path file : files) {
      String name = file.getFileName().toString();
      if (!FILE_NAME.matcher(name).matches()) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "unexpected file name in token directory: " + name);
      }
      TokenRecord record = parse(file, name.substring(0, name.length() - 5));
      if (!ids.add(record.id()) || !hashes.add(hex(record.secretHash()))) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "duplicate token id or hash: " + name);
      }
      records.add(record);
    }
    return new TokenStore(absolute, records);
  }

  public Path directory() {
    return directory;
  }

  /** Snapshot of valid records. Expired records remain so callers can report expiry. */
  public List<TokenRecord> records() {
    return Collections.unmodifiableList(new ArrayList<TokenRecord>(records));
  }

  /** Creates a new record file. Fails if the id already exists; never overwrites a token. */
  public void write(TokenRecord record) throws TokenStoreException {
    Objects.requireNonNull(record, "record");
    if (!ID.matcher(record.id()).matches()) {
      throw new TokenStoreException(TokenStoreException.Reason.RECORD, "invalid token id");
    }
    for (TokenRecord existing : records) {
      if (existing.id().equals(record.id()) || existing.matches(record.secretHash())) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "duplicate token id or hash");
      }
    }
    Path target = directory.resolve(record.id() + ".json");
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"id\": ").append(quote(record.id())).append(",\n");
    json.append("  \"label\": ").append(quote(record.label())).append(",\n");
    json.append("  \"secretHash\": ").append(quote(hex(record.secretHash())));
    if (record.expiresAt() != null) {
      json.append(",\n  \"expiresAt\": ").append(quote(record.expiresAt().toString()));
    }
    json.append("\n}\n");
    try {
      Files.write(
          target,
          json.toString().getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      restrictPermissions(target, "rw-------");
      records.add(record);
    } catch (IOException exception) {
      throw new TokenStoreException(
          TokenStoreException.Reason.IO, "cannot write token record", exception);
    }
  }

  private static TokenRecord parse(Path file, String expectedId) throws TokenStoreException {
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
    if (!expectedId.equals(id.raw()) || !ID.matcher(id.raw().toString()).matches()) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token id must match its file name: " + name);
    }
    if (label.raw().toString().length() > 80) {
      throw new TokenStoreException(
          TokenStoreException.Reason.RECORD, "token label is too long: " + name);
    }
    byte[] digest = decodeHash(hash.raw().toString(), name);
    YamlValue expiryValue = root.get("expiresAt");
    Instant expiresAt = null;
    if (expiryValue != null && !expiryValue.isNull()) {
      if (!expiryValue.isString()) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "expiresAt must be a quoted string: " + name);
      }
      try {
        expiresAt = Instant.parse(expiryValue.raw().toString());
      } catch (DateTimeParseException exception) {
        throw new TokenStoreException(
            TokenStoreException.Reason.RECORD, "invalid expiresAt timestamp: " + name, exception);
      }
    }
    return new TokenRecord(id.raw().toString(), label.raw().toString(), digest, expiresAt);
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

  private static void restrictPermissions(Path path, String mode) throws IOException {
    if (!Files.getFileStore(path).supportsFileAttributeView("posix")) {
      return;
    }
    Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(mode);
    Files.setPosixFilePermissions(path, permissions);
  }
}
