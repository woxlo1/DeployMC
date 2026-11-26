package io.github.woxlo1.deploymc.core.audit;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, bounded reader for retained audit files. It never follows links or traverses paths. */
public final class AuditReader {

  public static final String ACTIVE_FILE = "audit.jsonl";
  static final String FORMAT_VERSION_FILE = "format-version";

  private static final int FORMAT_VERSION = 1;
  private static final int MAX_LINE_BYTES = 1024 * 1024;
  private static final int MAX_FILE_NAME = 512;
  private static final Pattern ROTATED_FILE =
      Pattern.compile(
          "^audit-([0-9]{1,19})-([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}-[0-9]{2}-[0-9]{2}Z)\\.jsonl$");

  private final Path directory;

  public AuditReader(Path directory) {
    this.directory = Objects.requireNonNull(directory, "directory");
  }

  /** Host-controlled directory used by this reader. */
  public Path directory() {
    return directory;
  }

  /** Reads records and defects without throwing for content-level corruption. */
  public AuditReadResult readWithDefects() throws AuditException {
    List<AuditVerificationFailure> failures = new ArrayList<AuditVerificationFailure>();
    List<AuditVerificationWarning> warnings = new ArrayList<AuditVerificationWarning>();
    List<AuditRecord> records = new ArrayList<AuditRecord>();
    List<AuditFileInfo> files = new ArrayList<AuditFileInfo>();

    int formatVersion = readFormatVersion(failures);
    List<Path> rotated = new ArrayList<Path>();
    Path active = null;
    if (!Files.isDirectory(directory)) {
      throw AuditException.unavailable("audit directory is unavailable");
    }
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        String name = entry.getFileName().toString();
        BasicFileAttributes attributes;
        try {
          attributes = Files.readAttributes(entry, BasicFileAttributes.class);
        } catch (IOException unreadable) {
          throw AuditException.unavailable("audit entry is unreadable");
        }
        if (!attributes.isRegularFile()) {
          continue;
        }
        if (name.length() > MAX_FILE_NAME) {
          failures.add(new AuditVerificationFailure(name, 1, 0L, "invalid-record"));
        } else if (ACTIVE_FILE.equals(name)) {
          active = entry;
        } else if (FORMAT_VERSION_FILE.equals(name)) {
          continue;
        } else if (ROTATED_FILE.matcher(name).matches()) {
          rotated.add(entry);
        } else {
          failures.add(new AuditVerificationFailure(name, 1, 0L, "invalid-record"));
        }
      }
    } catch (IOException exception) {
      throw AuditException.unavailable("audit directory cannot be listed");
    }
    if (active == null) {
      failures.add(new AuditVerificationFailure(ACTIVE_FILE, 1, 0L, "format-version"));
    }

    try {
      Collections.sort(
          rotated,
          new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
              long a = sequenceOf(left.getFileName().toString());
              long b = sequenceOf(right.getFileName().toString());
              return a < b ? -1 : a > b ? 1 : 0;
            }
          });
    } catch (IllegalArgumentException invalid) {
      failures.add(new AuditVerificationFailure(ACTIVE_FILE, 1, 0L, "invalid-record"));
    }
    for (int index = 1; index < rotated.size(); index++) {
      long previous = sequenceOf(rotated.get(index - 1).getFileName().toString());
      long current = sequenceOf(rotated.get(index).getFileName().toString());
      if (previous == current) {
        failures.add(
            new AuditVerificationFailure(
                rotated.get(index).getFileName().toString(), 1, current, "sequence-duplicate"));
      }
    }

    for (Path file : rotated) {
      readFile(file, records, files, failures, warnings, false);
    }
    if (active != null) {
      readFile(active, records, files, failures, warnings, true);
    }
    return new AuditReadResult(formatVersion, records, files, warnings, failures);
  }

  /** Reads records and fails closed if any format defect is present. */
  public AuditReadResult read() throws AuditException {
    AuditReadResult result = readWithDefects();
    if (result.formatVersion() != FORMAT_VERSION || !result.failures().isEmpty()) {
      throw AuditException.unavailable("audit log has format defects");
    }
    return result;
  }

  /** Returns the highest sequence in the current log, or zero when the log is empty. */
  public long lastSequence() throws AuditException {
    long last = 0L;
    for (AuditRecord record : read().records()) {
      if (record.seq() > last) {
        last = record.seq();
      }
    }
    return last;
  }

  private int readFormatVersion(List<AuditVerificationFailure> failures) throws AuditException {
    Path file = directory.resolve(FORMAT_VERSION_FILE);
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    } catch (NoSuchFileException missing) {
      failures.add(new AuditVerificationFailure(FORMAT_VERSION_FILE, 1, 0L, "format-version"));
      return 0;
    } catch (IOException exception) {
      throw AuditException.unavailable("audit format version is unreadable");
    }
    if (bytes.length > 32) {
      failures.add(new AuditVerificationFailure(FORMAT_VERSION_FILE, 1, 0L, "format-version"));
      return 0;
    }
    String text = strictDecode(bytes, FORMAT_VERSION_FILE, 1, failures);
    if (!"1\n".equals(text)) {
      failures.add(new AuditVerificationFailure(FORMAT_VERSION_FILE, 1, 0L, "format-version"));
      return 0;
    }
    return FORMAT_VERSION;
  }

  private void readFile(
      Path file,
      List<AuditRecord> records,
      List<AuditFileInfo> files,
      List<AuditVerificationFailure> failures,
      List<AuditVerificationWarning> warnings,
      boolean active)
      throws AuditException {
    String name = file.getFileName().toString();
    ByteArrayOutputStream current = new ByteArrayOutputStream();
    int lineNumber = 0;
    long lastSeq = 0L;
    java.time.Instant lastTimestamp = null;
    try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
      int value;
      while ((value = input.read()) != -1) {
        if (value == '\n') {
          lineNumber++;
          processLine(current.toByteArray(), name, lineNumber, records, failures);
          AuditRecord parsed = lastParsed(records, name, lineNumber);
          if (parsed != null) {
            lastSeq = parsed.seq();
            lastTimestamp = parsed.timestamp();
          }
          current.reset();
          continue;
        }
        if (current.size() >= MAX_LINE_BYTES) {
          failures.add(new AuditVerificationFailure(name, lineNumber + 1, 0L, "json"));
          while ((value = input.read()) != -1 && value != '\n') {
            // Skip the remainder of an over-long line without allocating it.
          }
          current.reset();
          continue;
        }
        current.write(value);
      }
      if (current.size() > 0) {
        warnings.add(
            new AuditVerificationWarning(
                AuditVerificationWarning.PARTIAL_LINE, "partial line in " + name));
      }
    } catch (IOException exception) {
      throw AuditException.unavailable("audit file is unreadable");
    }
    files.add(new AuditFileInfo(name, lastSeq, lastTimestamp, active));
  }

  private void processLine(
      byte[] line,
      String file,
      int lineNumber,
      List<AuditRecord> records,
      List<AuditVerificationFailure> failures) {
    String text = strictDecode(line, file, lineNumber, failures);
    if (text == null) {
      return;
    }
    Map<String, Object> parsed;
    try {
      parsed = AuditJson.parseObject(text);
    } catch (IllegalArgumentException invalid) {
      failures.add(new AuditVerificationFailure(file, lineNumber, 0L, "json"));
      return;
    }
    try {
      records.add(AuditRecord.parse(text, file, lineNumber));
    } catch (IllegalArgumentException invalid) {
      long seq = parsed.get("seq") instanceof Long ? ((Long) parsed.get("seq")).longValue() : 0L;
      failures.add(new AuditVerificationFailure(file, lineNumber, seq, "invalid-record"));
    }
  }

  private AuditRecord lastParsed(List<AuditRecord> records, String file, int lineNumber) {
    if (records.isEmpty()) {
      return null;
    }
    AuditRecord last = records.get(records.size() - 1);
    return last.fileName().equals(file) && last.lineNumber() == lineNumber ? last : null;
  }

  private String strictDecode(
      byte[] bytes, String file, int lineNumber, List<AuditVerificationFailure> failures) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException invalid) {
      failures.add(new AuditVerificationFailure(file, lineNumber, 0L, "encoding"));
      return null;
    }
  }

  static long sequenceOf(String fileName) {
    Matcher matcher = ROTATED_FILE.matcher(fileName);
    if (!matcher.matches()) {
      return Long.MAX_VALUE;
    }
    try {
      return Long.parseLong(matcher.group(1));
    } catch (NumberFormatException overflow) {
      return Long.MAX_VALUE;
    }
  }
}
