package io.github.woxlo1.deploymc.core.diff;

import io.github.woxlo1.deploymc.core.diff.DiffResult.ContentInfo;
import io.github.woxlo1.deploymc.core.diff.StructuredDiffSupport.ParseFallbackException;
import io.github.woxlo1.deploymc.core.files.FileRevision;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Pure Java 8 diff engine over two in-memory byte arrays. */
public final class DiffEngine {

  private static final String REASON_TOO_LARGE = "diff.too-large";
  private static final String REASON_INVALID_INPUT = "diff.invalid-input";

  private DiffEngine() {}

  /** Computes a bounded diff. The request bytes are never read from a path or modified. */
  public static DiffResult diff(DiffRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    byte[] before = request.beforeInternal();
    byte[] after = request.afterInternal();
    ContentInfo beforeInfo = info(before);
    ContentInfo afterInfo = info(after);

    if (Arrays.equals(before, after)) {
      return new DiffResult(
          DiffStatus.IDENTICAL,
          DiffStrategy.NONE,
          null,
          null,
          null,
          beforeInfo,
          afterInfo,
          Collections.<String>emptyList(),
          Collections.<DiffResult.LineHunk>emptyList(),
          Collections.<DiffResult.WordDiff>emptyList(),
          Collections.<DiffResult.SemanticChange>emptyList(),
          null);
    }

    DiffLimits limits = request.limits();
    DiffMode requested = request.mode();
    DiffMode mode = requested;
    if (mode == DiffMode.AUTO) {
      mode =
          ZipMetadataReader.hasZipSignature(before) || ZipMetadataReader.hasZipSignature(after)
              ? DiffMode.JAR
              : DiffMode.YAML;
    }

    if (mode == DiffMode.JAR) {
      return jar(before, after, beforeInfo, afterInfo, limits, new TreeSet<String>());
    }

    long bytes = Math.max((long) before.length, (long) after.length);
    if (bytes > limits.maxBytesPerSide()) {
      return tooLarge("bytes-per-side", bytes, beforeInfo, afterInfo, new TreeSet<String>());
    }

    TreeSet<String> notes = new TreeSet<String>();
    if (TextDiffSupport.hasBom(before) || TextDiffSupport.hasBom(after)) {
      notes.add(DiffNotes.BOM);
    }
    String beforeText;
    String afterText;
    try {
      beforeText = TextDiffSupport.decode(before, true);
      afterText = TextDiffSupport.decode(after, true);
    } catch (InvalidInputException exception) {
      addInvalidNotes(notes, exception);
      return invalidInput(beforeInfo, afterInfo, notes);
    }

    boolean structured = mode == DiffMode.YAML || mode == DiffMode.JSON;
    if (structured) {
      try {
        List<DiffResult.SemanticChange> changes =
            StructuredDiffSupport.diff(beforeText, afterText, limits);
        return new DiffResult(
            DiffStatus.CHANGED,
            DiffStrategy.SEMANTIC,
            null,
            null,
            null,
            beforeInfo,
            afterInfo,
            sorted(notes),
            Collections.<DiffResult.LineHunk>emptyList(),
            Collections.<DiffResult.WordDiff>emptyList(),
            changes,
            null);
      } catch (ParseFallbackException exception) {
        notes.add(DiffNotes.PARSE_FALLBACK);
      } catch (LimitExceededException exception) {
        return tooLarge(exception.limit(), exception.observed(), beforeInfo, afterInfo, notes);
      }
    }

    try {
      return text(beforeText, afterText, beforeInfo, afterInfo, limits, notes);
    } catch (LimitExceededException exception) {
      return tooLarge(exception.limit(), exception.observed(), beforeInfo, afterInfo, notes);
    } catch (InvalidInputException exception) {
      addInvalidNotes(notes, exception);
      return invalidInput(beforeInfo, afterInfo, notes);
    }
  }

  private static ContentInfo info(byte[] bytes) {
    return new ContentInfo(bytes.length, FileRevision.of(bytes));
  }

  private static DiffResult jar(
      byte[] before,
      byte[] after,
      ContentInfo beforeInfo,
      ContentInfo afterInfo,
      DiffLimits limits,
      TreeSet<String> notes) {
    try {
      JarDiffSupport.Result result =
          JarDiffSupport.diff(
              before, after, limits, !beforeInfo.sha256().equals(afterInfo.sha256()), notes);
      return new DiffResult(
          result.changed() ? DiffStatus.CHANGED : DiffStatus.IDENTICAL,
          DiffStrategy.JAR_METADATA,
          null,
          null,
          null,
          beforeInfo,
          afterInfo,
          sorted(notes),
          Collections.<DiffResult.LineHunk>emptyList(),
          Collections.<DiffResult.WordDiff>emptyList(),
          Collections.<DiffResult.SemanticChange>emptyList(),
          result.metadata());
    } catch (InvalidInputException exception) {
      addInvalidNotes(notes, exception);
      return invalidInput(beforeInfo, afterInfo, notes);
    } catch (LimitExceededException exception) {
      return tooLarge(exception.limit(), exception.observed(), beforeInfo, afterInfo, notes);
    }
  }

  private static DiffResult text(
      String beforeText,
      String afterText,
      ContentInfo beforeInfo,
      ContentInfo afterInfo,
      DiffLimits limits,
      TreeSet<String> notes) {
    List<TextDiffSupport.Line> beforeLines = TextDiffSupport.splitLines(beforeText, limits);
    List<TextDiffSupport.Line> afterLines = TextDiffSupport.splitLines(afterText, limits);
    if (TextDiffSupport.hasLineEndingDifference(beforeLines, afterLines)) {
      notes.add(DiffNotes.LINE_ENDING);
    }
    WorkBudget budget = new WorkBudget(limits.maxEditOperations());
    TextDiffSupport.Result result = TextDiffSupport.diff(beforeLines, afterLines, budget, limits);
    List<DiffResult.LineHunk> hunks = sortedHunks(result.hunks());
    return new DiffResult(
        DiffStatus.CHANGED,
        notes.contains(DiffNotes.PARSE_FALLBACK) ? DiffStrategy.TEXT_FALLBACK : DiffStrategy.LINE,
        null,
        null,
        null,
        beforeInfo,
        afterInfo,
        sorted(notes),
        hunks,
        result.words(),
        Collections.<DiffResult.SemanticChange>emptyList(),
        null);
  }

  private static List<DiffResult.LineHunk> sortedHunks(List<DiffResult.LineHunk> hunks) {
    List<DiffResult.LineHunk> result = new ArrayList<DiffResult.LineHunk>(hunks);
    Collections.sort(
        result,
        new Comparator<DiffResult.LineHunk>() {
          @Override
          public int compare(DiffResult.LineHunk left, DiffResult.LineHunk right) {
            int byOld = Integer.compare(left.oldStart(), right.oldStart());
            return byOld != 0 ? byOld : Integer.compare(left.newStart(), right.newStart());
          }
        });
    return result;
  }

  private static DiffResult tooLarge(
      String limit,
      long observed,
      ContentInfo beforeInfo,
      ContentInfo afterInfo,
      Set<String> notes) {
    return new DiffResult(
        DiffStatus.TOO_LARGE,
        DiffStrategy.NONE,
        REASON_TOO_LARGE,
        limit,
        observed,
        beforeInfo,
        afterInfo,
        sorted(notes),
        Collections.<DiffResult.LineHunk>emptyList(),
        Collections.<DiffResult.WordDiff>emptyList(),
        Collections.<DiffResult.SemanticChange>emptyList(),
        null);
  }

  private static DiffResult invalidInput(
      ContentInfo beforeInfo, ContentInfo afterInfo, Set<String> notes) {
    return new DiffResult(
        DiffStatus.INVALID_INPUT,
        DiffStrategy.NONE,
        REASON_INVALID_INPUT,
        null,
        null,
        beforeInfo,
        afterInfo,
        sorted(notes),
        Collections.<DiffResult.LineHunk>emptyList(),
        Collections.<DiffResult.WordDiff>emptyList(),
        Collections.<DiffResult.SemanticChange>emptyList(),
        null);
  }

  private static void addInvalidNotes(Set<String> notes, InvalidInputException exception) {
    if (exception.note() != null) {
      notes.add(exception.note());
    }
    notes.addAll(exception.notes());
  }

  private static List<String> sorted(Set<String> notes) {
    return Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(notes)));
  }
}
