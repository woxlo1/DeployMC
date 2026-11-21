package io.github.woxlo1.deploymc.core.validation;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Strict UTF-8 decoding with byte/character position mapping and bounded input checks. */
final class TextInput {

  private final String text;
  private final ValidationIssue encodingIssue;
  private final int bomBytes;
  private int[] lineStarts;
  private long[] byteOffsets;

  private TextInput(String text, ValidationIssue encodingIssue, int bomBytes) {
    this.text = text;
    this.encodingIssue = encodingIssue;
    this.bomBytes = bomBytes;
  }

  String text() {
    return text;
  }

  ValidationIssue encodingIssue() {
    return encodingIssue;
  }

  boolean hasEncodingIssue() {
    return encodingIssue != null;
  }

  int bomBytes() {
    return bomBytes;
  }

  int[] position(int charIndex) {
    if (lineStarts == null) {
      int[] starts = new int[2];
      int count = 0;
      starts[count++] = 0;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n' && count < starts.length) {
          starts[count++] = i + 1;
        } else if (text.charAt(i) == '\n') {
          starts = Arrays.copyOf(starts, starts.length * 2);
          starts[count++] = i + 1;
        }
      }
      lineStarts = Arrays.copyOf(starts, count);
    }
    int index = Math.max(0, Math.min(charIndex, text.length()));
    int low = 0;
    int high = lineStarts.length - 1;
    while (low < high) {
      int middle = (low + high + 1) >>> 1;
      if (lineStarts[middle] <= index) {
        low = middle;
      } else {
        high = middle - 1;
      }
    }
    return new int[] {low + 1, index - lineStarts[low] + 1};
  }

  long byteOffset(int charIndex) {
    ensureByteOffsets();
    int index = Math.max(0, Math.min(charIndex, text.length()));
    return byteOffsets[index];
  }

  private void ensureByteOffsets() {
    if (byteOffsets != null) {
      return;
    }
    long[] offsets = new long[text.length() + 1];
    offsets[0] = bomBytes;
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      int chars = Character.charCount(codePoint);
      offsets[Math.min(index + 1, text.length())] = offsets[index] + utf8Length(codePoint);
      if (chars == 2) {
        offsets[Math.min(index + 2, text.length())] = offsets[index] + utf8Length(codePoint);
      }
      index += chars;
    }
    byteOffsets = offsets;
  }

  private static int utf8Length(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    if (codePoint < 0x10000) {
      return 3;
    }
    return 4;
  }

  static TextInput decode(byte[] content, ValidationLimits limits) throws ValidationLimitException {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(limits, "limits");
    if (content.length > limits.maxBytes()) {
      throw new ValidationLimitException("maxBytes", content.length, limits.maxBytes());
    }
    int start = hasBom(content) ? 3 : 0;
    int codePoints = 0;
    int lines = 1;
    int index = start;
    while (index < content.length) {
      int first = content[index] & 0xff;
      if (first == 0) {
        return encodingIssue(content, index, "nul", limits);
      }
      if (first < 0x80) {
        if (first == '\n') {
          lines++;
        }
        index++;
      } else {
        int continuationCount;
        int minimum;
        int maximum;
        if (first >= 0xc2 && first <= 0xdf) {
          continuationCount = 1;
          minimum = 0x80;
          maximum = 0xbf;
        } else if (first == 0xe0) {
          continuationCount = 2;
          minimum = 0xa0;
          maximum = 0xbf;
        } else if ((first >= 0xe1 && first <= 0xec) || (first >= 0xee && first <= 0xef)) {
          continuationCount = 2;
          minimum = 0x80;
          maximum = 0xbf;
        } else if (first == 0xed) {
          continuationCount = 2;
          minimum = 0x80;
          maximum = 0x9f;
        } else if (first == 0xf0) {
          continuationCount = 3;
          minimum = 0x90;
          maximum = 0xbf;
        } else if (first >= 0xf1 && first <= 0xf3) {
          continuationCount = 3;
          minimum = 0x80;
          maximum = 0xbf;
        } else if (first == 0xf4) {
          continuationCount = 3;
          minimum = 0x80;
          maximum = 0x8f;
        } else {
          return encodingIssue(content, index, "invalid-utf8", limits);
        }
        if (index + continuationCount >= content.length) {
          return encodingIssue(content, index, "invalid-utf8", limits);
        }
        int second = content[index + 1] & 0xff;
        if (second < minimum || second > maximum) {
          return encodingIssue(content, index, "invalid-utf8", limits);
        }
        for (int offset = 2; offset <= continuationCount; offset++) {
          int next = content[index + offset] & 0xff;
          if (next < 0x80 || next > 0xbf) {
            return encodingIssue(content, index, "invalid-utf8", limits);
          }
        }
        index += continuationCount + 1;
      }
      if (++codePoints > limits.maxCodePoints()) {
        throw new ValidationLimitException("maxCodePoints", codePoints, limits.maxCodePoints());
      }
    }
    if (lines > limits.maxLines()) {
      throw new ValidationLimitException("maxLines", lines, limits.maxLines());
    }
    String text = new String(content, start, content.length - start, StandardCharsets.UTF_8);
    return new TextInput(text, null, start);
  }

  private static boolean hasBom(byte[] content) {
    return content.length >= 3
        && (content[0] & 0xff) == 0xef
        && (content[1] & 0xff) == 0xbb
        && (content[2] & 0xff) == 0xbf;
  }

  private static TextInput encodingIssue(
      byte[] content, int offset, String reason, ValidationLimits limits) {
    ValidationIssue issue =
        new ValidationIssue(
            Severity.ERROR,
            "validation.encoding",
            "",
            0,
            0,
            offset,
            Collections.singletonList(reason));
    return new TextInput("", issue, hasBom(content) ? 3 : 0);
  }
}
