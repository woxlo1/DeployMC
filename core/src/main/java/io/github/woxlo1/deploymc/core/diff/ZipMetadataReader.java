package io.github.woxlo1.deploymc.core.diff;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Reads ZIP central-directory metadata without extracting entries. */
final class ZipMetadataReader {

  private static final long LOCAL_FILE_SIGNATURE = 0x04034b50L;
  private static final long CENTRAL_FILE_SIGNATURE = 0x02014b50L;
  private static final long END_SIGNATURE = 0x06054b50L;
  private static final long ZIP64_LOCATOR_SIGNATURE = 0x07064b50L;
  private static final long ZIP64_MARKER = 0xffffffffL;
  private static final int ENCRYPTED_FLAGS = 0x0041;
  private static final Set<String> DESCRIPTORS =
      Collections.unmodifiableSet(
          new LinkedHashSet<String>(
              Arrays.asList(
                  "plugin.yml", "paper-plugin.yml", "bungee.yml", "velocity-plugin.json")));

  private ZipMetadataReader() {}

  static ZipMetadata read(byte[] bytes, DiffLimits limits) {
    if (!hasZipSignature(bytes)) {
      throw new InvalidInputException(null);
    }
    int eocd = findEndRecord(bytes);
    if (eocd < 0) {
      throw new InvalidInputException(DiffNotes.ZIP_TRUNCATED);
    }
    int disk = u16(bytes, eocd + 4);
    int centralDisk = u16(bytes, eocd + 6);
    int entriesOnDisk = u16(bytes, eocd + 8);
    int entryCount = u16(bytes, eocd + 10);
    long centralSize = u32(bytes, eocd + 12);
    long centralOffset = u32(bytes, eocd + 16);
    boolean zip64Locator = eocd >= 20 && hasSignature(bytes, eocd - 20, ZIP64_LOCATOR_SIGNATURE);
    Set<String> notes = new TreeSet<String>();
    if (disk != 0
        || centralDisk != 0
        || entriesOnDisk != entryCount
        || zip64Locator
        || centralSize == ZIP64_MARKER
        || centralOffset == ZIP64_MARKER) {
      notes.add(DiffNotes.ZIP64_UNSUPPORTED);
      throw new InvalidInputException(DiffNotes.ZIP64_UNSUPPORTED, notes);
    }
    if (entryCount > limits.maxJarEntries()) {
      throw new LimitExceededException("jar-entries", entryCount);
    }
    long centralEnd = centralOffset + centralSize;
    if (centralEnd > eocd || centralOffset > eocd) {
      throw new InvalidInputException(DiffNotes.ZIP_TRUNCATED);
    }

    Set<String> entries = new TreeSet<String>();
    LinkedHashMap<String, String> descriptors = new LinkedHashMap<String, String>();
    int[] descriptorTotals = new int[] {0};
    int position = (int) centralOffset;
    for (int index = 0; index < entryCount; index++) {
      if (position + 46L > centralEnd
          || position + 46L > bytes.length
          || !hasSignature(bytes, position, CENTRAL_FILE_SIGNATURE)) {
        throw new InvalidInputException(DiffNotes.ZIP_TRUNCATED);
      }
      int flags = u16(bytes, position + 8);
      int method = u16(bytes, position + 10);
      long crc = u32(bytes, position + 16);
      long compressedSize = u32(bytes, position + 20);
      long uncompressedSize = u32(bytes, position + 24);
      int nameLength = u16(bytes, position + 28);
      int extraLength = u16(bytes, position + 30);
      int entryCommentLength = u16(bytes, position + 32);
      long localOffset = u32(bytes, position + 42);
      long next = (long) position + 46L + nameLength + extraLength + entryCommentLength;
      if (next > centralEnd || next > bytes.length) {
        throw new InvalidInputException(DiffNotes.ZIP_TRUNCATED);
      }
      String name = strictEntryName(bytes, position + 46, nameLength);
      entries.add(name);
      if ((flags & ENCRYPTED_FLAGS) != 0) {
        notes.add(DiffNotes.ZIP_ENCRYPTED);
        throw new InvalidInputException(DiffNotes.ZIP_ENCRYPTED, notes);
      }
      if (DESCRIPTORS.contains(name)) {
        if (descriptors.containsKey(name)) {
          notes.add(DiffNotes.DESCRIPTOR_DUPLICATE);
          throw new InvalidInputException(DiffNotes.DESCRIPTOR_DUPLICATE, notes);
        }
        String descriptor =
            readDescriptor(
                bytes,
                localOffset,
                method,
                crc,
                compressedSize,
                uncompressedSize,
                limits,
                descriptorTotals,
                notes);
        descriptors.put(name, descriptor);
      }
      position = (int) next;
    }
    if (position != centralEnd) {
      throw new InvalidInputException(DiffNotes.ZIP_TRUNCATED);
    }

    return new ZipMetadata(entries, descriptors, notes);
  }

  static boolean hasZipSignature(byte[] bytes) {
    return hasSignature(bytes, 0, LOCAL_FILE_SIGNATURE) || hasSignature(bytes, 0, END_SIGNATURE);
  }

  private static int findEndRecord(byte[] bytes) {
    int minimum = Math.max(0, bytes.length - 65_557);
    for (int candidate = bytes.length - 22; candidate >= minimum; candidate--) {
      if (hasSignature(bytes, candidate, END_SIGNATURE)) {
        int commentLength = u16(bytes, candidate + 20);
        if (candidate + 22L + commentLength == bytes.length) {
          return candidate;
        }
      }
    }
    return -1;
  }

  private static String readDescriptor(
      byte[] bytes,
      long localOffset,
      int method,
      long expectedCrc,
      long compressedSize,
      long uncompressedSize,
      DiffLimits limits,
      int[] descriptorTotals,
      Set<String> notes) {
    if (localOffset < 0 || localOffset + 30 > bytes.length) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }
    int local = (int) localOffset;
    if (!hasSignature(bytes, local, LOCAL_FILE_SIGNATURE)) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }
    int nameLength = u16(bytes, local + 26);
    int extraLength = u16(bytes, local + 28);
    long dataStart = local + 30L + nameLength + extraLength;
    long dataEnd = dataStart + compressedSize;
    if (dataEnd > bytes.length) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }
    byte[] content;
    if (method == 0) {
      if (compressedSize != uncompressedSize || uncompressedSize > limits.maxJarDescriptorBytes()) {
        notes.add(DiffNotes.DESCRIPTOR_INVALID);
        return "";
      }
      content = Arrays.copyOfRange(bytes, (int) dataStart, (int) dataEnd);
    } else if (method == 8) {
      content =
          inflate(bytes, (int) dataStart, (int) compressedSize, uncompressedSize, limits, notes);
    } else {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }

    descriptorTotals[0] += content.length;
    if (descriptorTotals[0] > limits.maxJarDescriptorTotalBytes()) {
      throw new LimitExceededException("jar-descriptor-total-bytes", descriptorTotals[0]);
    }
    CRC32 crc = new CRC32();
    crc.update(content);
    if (expectedCrc != ZIP64_MARKER && crc.getValue() != expectedCrc) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }
    String decoded = strictUtf8(content, notes);
    if (decoded.startsWith("\ufeff")) {
      notes.add(DiffNotes.BOM);
      decoded = decoded.substring(1);
    }
    return decoded;
  }

  private static byte[] inflate(
      byte[] bytes,
      int start,
      int length,
      long expectedSize,
      DiffLimits limits,
      Set<String> notes) {
    Inflater inflater = new Inflater(true);
    inflater.setInput(bytes, start, length);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    try {
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        if (count == 0) {
          if (inflater.needsInput() || inflater.needsDictionary()) {
            break;
          }
          continue;
        }
        output.write(buffer, 0, count);
        if (output.size() > limits.maxJarDescriptorBytes()) {
          throw new LimitExceededException("jar-descriptor-bytes", output.size());
        }
      }
    } catch (DataFormatException exception) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return new byte[0];
    } finally {
      inflater.end();
    }
    byte[] content = output.toByteArray();
    if (expectedSize != ZIP64_MARKER && expectedSize != content.length) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return new byte[0];
    }
    return content;
  }

  private static String strictUtf8(byte[] content, Set<String> notes) {
    CharsetDecoder decoder = strictDecoder();
    try {
      CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
      return decoded.toString();
    } catch (CharacterCodingException exception) {
      notes.add(DiffNotes.DESCRIPTOR_INVALID);
      return "";
    }
  }

  private static String strictEntryName(byte[] bytes, int offset, int length) {
    CharsetDecoder decoder = strictDecoder();
    try {
      return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
    } catch (CharacterCodingException exception) {
      throw new InvalidInputException(null);
    }
  }

  private static CharsetDecoder strictDecoder() {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  private static boolean hasSignature(byte[] bytes, int offset, long signature) {
    return offset >= 0 && offset + 4 <= bytes.length && u32(bytes, offset) == signature;
  }

  private static int u16(byte[] bytes, int offset) {
    return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
  }

  private static long u32(byte[] bytes, int offset) {
    return (bytes[offset] & 0xffL)
        | ((bytes[offset + 1] & 0xffL) << 8)
        | ((bytes[offset + 2] & 0xffL) << 16)
        | ((bytes[offset + 3] & 0xffL) << 24);
  }

  /** Metadata read from one ZIP archive. */
  static final class ZipMetadata {
    private final Set<String> entries;
    private final Map<String, String> descriptors;
    private final Set<String> notes;

    ZipMetadata(Set<String> entries, Map<String, String> descriptors, Set<String> notes) {
      this.entries = entries;
      this.descriptors = descriptors;
      this.notes = notes;
    }

    Set<String> entries() {
      return entries;
    }

    Map<String, String> descriptors() {
      return descriptors;
    }

    Set<String> notes() {
      return notes;
    }
  }
}
