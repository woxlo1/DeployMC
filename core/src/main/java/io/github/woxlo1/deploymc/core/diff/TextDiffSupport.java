package io.github.woxlo1.deploymc.core.diff;

import io.github.woxlo1.deploymc.core.diff.BoundedSequenceDiff.Edit;
import io.github.woxlo1.deploymc.core.diff.BoundedSequenceDiff.EditKind;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineChangeKind;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineHunk;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordSegment;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordSegmentKind;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Text decoding, line matching, hunk grouping, and word segmentation. */
final class TextDiffSupport {

  private static final int CONTEXT_LINES = 3;
  private static final byte[] UTF8_BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

  private TextDiffSupport() {}

  static boolean hasBom(byte[] bytes) {
    if (bytes.length < 3) {
      return false;
    }
    return bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
  }

  static String decode(byte[] bytes, boolean stripBom) {
    int offset = stripBom && hasBom(bytes) ? 3 : 0;
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
      return decoded.toString();
    } catch (CharacterCodingException exception) {
      throw new InvalidInputException(null);
    }
  }

  static List<Line> splitLines(String text, DiffLimits limits) {
    if (text.isEmpty()) {
      return Collections.emptyList();
    }
    List<Line> result = new ArrayList<Line>();
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        result.add(new Line(text.substring(start, i), LineEnding.LF));
        start = i + 1;
      } else if (c == '\r') {
        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          result.add(new Line(text.substring(start, i), LineEnding.CRLF));
          i++;
        } else {
          result.add(new Line(text.substring(start, i), LineEnding.CR));
        }
        start = i + 1;
      }
      if (result.size() > limits.maxLinesPerSide()) {
        throw new LimitExceededException("lines-per-side", result.size());
      }
    }
    if (start < text.length()) {
      result.add(new Line(text.substring(start), LineEnding.EOF));
      if (result.size() > limits.maxLinesPerSide()) {
        throw new LimitExceededException("lines-per-side", result.size());
      }
    }
    return result;
  }

  static boolean hasLineEndingDifference(List<Line> before, List<Line> after) {
    int shared = Math.min(before.size(), after.size());
    for (int i = 0; i < shared; i++) {
      Line left = before.get(i);
      Line right = after.get(i);
      if (left.text.equals(right.text) && left.ending != right.ending) {
        return true;
      }
    }
    return false;
  }

  static Result diff(List<Line> before, List<Line> after, WorkBudget budget, DiffLimits limits) {
    List<Edit<Line>> edits = BoundedSequenceDiff.diff(before, after, budget);
    List<Item> items = new ArrayList<Item>(edits.size());
    int oldLine = 1;
    int newLine = 1;
    for (Edit<Line> edit : edits) {
      if (edit.kind() == EditKind.SAME) {
        Line line = before.get(edit.beforeIndex());
        items.add(
            new Item(
                new LineChange(LineChangeKind.CONTEXT, oldLine, newLine, line.text, line.ending),
                false));
        oldLine++;
        newLine++;
      } else if (edit.kind() == EditKind.REMOVED) {
        Line line = before.get(edit.beforeIndex());
        items.add(
            new Item(
                new LineChange(LineChangeKind.REMOVED, oldLine, null, line.text, line.ending),
                true));
        oldLine++;
      } else {
        Line line = after.get(edit.afterIndex());
        items.add(
            new Item(
                new LineChange(LineChangeKind.ADDED, null, newLine, line.text, line.ending), true));
        newLine++;
      }
    }

    List<LineHunk> hunks = hunks(items);
    List<WordDiff> words = words(items, budget, limits);
    return new Result(hunks, words);
  }

  private static List<LineHunk> hunks(List<Item> items) {
    List<int[]> ranges = new ArrayList<int[]>();
    for (int i = 0; i < items.size(); i++) {
      if (!items.get(i).changed) {
        continue;
      }
      int start = Math.max(0, i - CONTEXT_LINES);
      int end = Math.min(items.size() - 1, i + CONTEXT_LINES);
      if (!ranges.isEmpty() && start <= ranges.get(ranges.size() - 1)[1] + 1) {
        int[] last = ranges.get(ranges.size() - 1);
        last[1] = Math.max(last[1], end);
      } else {
        ranges.add(new int[] {start, end});
      }
    }

    List<LineHunk> result = new ArrayList<LineHunk>(ranges.size());
    for (int[] range : ranges) {
      List<LineChange> lines = new ArrayList<LineChange>();
      for (int i = range[0]; i <= range[1]; i++) {
        lines.add(items.get(i).change);
      }
      Integer firstOld = null;
      Integer lastOld = null;
      Integer firstNew = null;
      Integer lastNew = null;
      for (LineChange line : lines) {
        if (line.oldLine() != null) {
          if (firstOld == null) {
            firstOld = line.oldLine();
          }
          lastOld = line.oldLine();
        }
        if (line.newLine() != null) {
          if (firstNew == null) {
            firstNew = line.newLine();
          }
          lastNew = line.newLine();
        }
      }
      if (firstOld == null) {
        firstOld = previousOldLine(items, range[0]) + 1;
      }
      if (firstNew == null) {
        firstNew = previousNewLine(items, range[0]) + 1;
      }
      int oldLines = lastOld == null ? 0 : lastOld - firstOld + 1;
      int newLines = lastNew == null ? 0 : lastNew - firstNew + 1;
      result.add(new LineHunk(firstOld, oldLines, firstNew, newLines, lines));
    }
    return result;
  }

  private static int previousOldLine(List<Item> items, int index) {
    int result = 0;
    for (int i = 0; i < index; i++) {
      Integer line = items.get(i).change.oldLine();
      if (line != null) {
        result = line;
      }
    }
    return result;
  }

  private static int previousNewLine(List<Item> items, int index) {
    int result = 0;
    for (int i = 0; i < index; i++) {
      Integer line = items.get(i).change.newLine();
      if (line != null) {
        result = line;
      }
    }
    return result;
  }

  private static List<WordDiff> words(List<Item> items, WorkBudget budget, DiffLimits limits) {
    List<LineChange> changes = new ArrayList<LineChange>();
    for (Item item : items) {
      changes.add(item.change);
    }
    List<WordDiff> result = new ArrayList<WordDiff>();
    int index = 0;
    while (index < changes.size()) {
      if (changes.get(index).kind() != LineChangeKind.REMOVED) {
        index++;
        continue;
      }
      int removedStart = index;
      while (index < changes.size() && changes.get(index).kind() == LineChangeKind.REMOVED) {
        index++;
      }
      int removedEnd = index;
      int addedStart = index;
      while (index < changes.size() && changes.get(index).kind() == LineChangeKind.ADDED) {
        index++;
      }
      int addedEnd = index;
      if (addedEnd == addedStart) {
        continue;
      }
      int pairs = Math.min(removedEnd - removedStart, addedEnd - addedStart);
      for (int i = 0; i < pairs; i++) {
        LineChange removed = changes.get(removedStart + i);
        LineChange added = changes.get(addedStart + i);
        result.add(wordDiff(removed, added, budget, limits));
      }
    }
    return result;
  }

  private static WordDiff wordDiff(
      LineChange removed, LineChange added, WorkBudget budget, DiffLimits limits) {
    List<String> before = tokenize(removed.text(), limits);
    List<String> after = tokenize(added.text(), limits);
    List<Edit<String>> edits = BoundedSequenceDiff.diff(before, after, budget);
    List<WordSegment> oldSegments = new ArrayList<WordSegment>();
    List<WordSegment> newSegments = new ArrayList<WordSegment>();
    for (Edit<String> edit : edits) {
      if (edit.kind() == EditKind.SAME) {
        String token = before.get(edit.beforeIndex());
        append(oldSegments, WordSegmentKind.UNCHANGED, token);
        append(newSegments, WordSegmentKind.UNCHANGED, token);
      } else if (edit.kind() == EditKind.REMOVED) {
        append(oldSegments, WordSegmentKind.REMOVED, before.get(edit.beforeIndex()));
      } else {
        append(newSegments, WordSegmentKind.ADDED, after.get(edit.afterIndex()));
      }
    }
    return new WordDiff(removed.oldLine(), added.newLine(), oldSegments, newSegments);
  }

  private static List<String> tokenize(String text, DiffLimits limits) {
    List<String> tokens = new ArrayList<String>();
    int i = 0;
    while (i < text.length()) {
      int codePoint = text.codePointAt(i);
      int kind = tokenKind(codePoint);
      int start = i;
      i += Character.charCount(codePoint);
      if (kind != 2) {
        while (i < text.length()) {
          int next = text.codePointAt(i);
          if (tokenKind(next) != kind) {
            break;
          }
          i += Character.charCount(next);
        }
      }
      tokens.add(text.substring(start, i));
      if (tokens.size() > limits.maxWordTokensPerLine()) {
        throw new LimitExceededException("word-tokens-per-line", tokens.size());
      }
    }
    return tokens;
  }

  private static int tokenKind(int codePoint) {
    if (codePoint == '_' || Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
      return 0;
    }
    if (Character.isWhitespace(codePoint)) {
      return 1;
    }
    return 2;
  }

  private static void append(List<WordSegment> segments, WordSegmentKind kind, String text) {
    if (!segments.isEmpty() && segments.get(segments.size() - 1).kind() == kind) {
      WordSegment last = segments.get(segments.size() - 1);
      segments.set(segments.size() - 1, new WordSegment(kind, last.text() + text));
      return;
    }
    segments.add(new WordSegment(kind, text));
  }

  /** One decoded and normalized line. */
  static final class Line {
    private final String text;
    private final LineEnding ending;

    Line(String text, LineEnding ending) {
      this.text = text;
      this.ending = ending;
    }

    String text() {
      return text;
    }

    LineEnding ending() {
      return ending;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Line)) {
        return false;
      }
      Line line = (Line) other;
      return text.equals(line.text) && ending == line.ending;
    }

    @Override
    public int hashCode() {
      return 31 * text.hashCode() + ending.hashCode();
    }
  }

  /** Result of one text diff. */
  static final class Result {
    private final List<LineHunk> hunks;
    private final List<WordDiff> words;

    Result(List<LineHunk> hunks, List<WordDiff> words) {
      this.hunks = hunks;
      this.words = words;
    }

    List<LineHunk> hunks() {
      return hunks;
    }

    List<WordDiff> words() {
      return words;
    }
  }

  private static final class Item {
    private final LineChange change;
    private final boolean changed;

    Item(LineChange change, boolean changed) {
      this.change = change;
      this.changed = changed;
    }
  }
}
