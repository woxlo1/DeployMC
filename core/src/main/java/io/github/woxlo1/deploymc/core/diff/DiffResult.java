package io.github.woxlo1.deploymc.core.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of a diff operation. */
public final class DiffResult {

  private final DiffStatus status;
  private final DiffStrategy strategy;
  private final String reason;
  private final String limit;
  private final Long observed;
  private final ContentInfo before;
  private final ContentInfo after;
  private final List<String> notes;
  private final List<LineHunk> lineHunks;
  private final List<WordDiff> wordDiffs;
  private final List<SemanticChange> semanticChanges;
  private final JarMetadataDiff jarMetadata;

  DiffResult(
      DiffStatus status,
      DiffStrategy strategy,
      String reason,
      String limit,
      Long observed,
      ContentInfo before,
      ContentInfo after,
      List<String> notes,
      List<LineHunk> lineHunks,
      List<WordDiff> wordDiffs,
      List<SemanticChange> semanticChanges,
      JarMetadataDiff jarMetadata) {
    this.status = status;
    this.strategy = strategy;
    this.reason = reason;
    this.limit = limit;
    this.observed = observed;
    this.before = before;
    this.after = after;
    this.notes = immutable(notes);
    this.lineHunks = immutable(lineHunks);
    this.wordDiffs = immutable(wordDiffs);
    this.semanticChanges = immutable(semanticChanges);
    this.jarMetadata = jarMetadata;
  }

  public DiffStatus status() {
    return status;
  }

  public DiffStrategy strategy() {
    return strategy;
  }

  public String reason() {
    return reason;
  }

  public String limit() {
    return limit;
  }

  public Long observed() {
    return observed;
  }

  public ContentInfo before() {
    return before;
  }

  public ContentInfo after() {
    return after;
  }

  public List<String> notes() {
    return notes;
  }

  public List<LineHunk> lineHunks() {
    return lineHunks;
  }

  public List<WordDiff> wordDiffs() {
    return wordDiffs;
  }

  public List<SemanticChange> semanticChanges() {
    return semanticChanges;
  }

  public JarMetadataDiff jarMetadata() {
    return jarMetadata;
  }

  private static <T> List<T> immutable(List<T> source) {
    return source == null
        ? Collections.<T>emptyList()
        : Collections.unmodifiableList(new ArrayList<T>(source));
  }

  /** Size and hash of one input side. */
  public static final class ContentInfo {
    private final long size;
    private final String sha256;

    public ContentInfo(long size, String sha256) {
      this.size = size;
      this.sha256 = sha256;
    }

    public long size() {
      return size;
    }

    public String sha256() {
      return sha256;
    }
  }

  /** Kind of one line entry in a hunk. */
  public enum LineChangeKind {
    CONTEXT("context"),
    REMOVED("removed"),
    ADDED("added");

    private final String value;

    LineChangeKind(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /** One line of a hunk. */
  public static final class LineChange {
    private final LineChangeKind kind;
    private final Integer oldLine;
    private final Integer newLine;
    private final String text;
    private final LineEnding ending;

    public LineChange(
        LineChangeKind kind, Integer oldLine, Integer newLine, String text, LineEnding ending) {
      this.kind = kind;
      this.oldLine = oldLine;
      this.newLine = newLine;
      this.text = text;
      this.ending = ending;
    }

    public LineChangeKind kind() {
      return kind;
    }

    public Integer oldLine() {
      return oldLine;
    }

    public Integer newLine() {
      return newLine;
    }

    public String text() {
      return text;
    }

    public LineEnding ending() {
      return ending;
    }
  }

  /** A changed region with up to three context lines on each side. */
  public static final class LineHunk {
    private final int oldStart;
    private final int oldLines;
    private final int newStart;
    private final int newLines;
    private final List<LineChange> lines;

    public LineHunk(
        int oldStart, int oldLines, int newStart, int newLines, List<LineChange> lines) {
      this.oldStart = oldStart;
      this.oldLines = oldLines;
      this.newStart = newStart;
      this.newLines = newLines;
      this.lines = immutable(lines);
    }

    public int oldStart() {
      return oldStart;
    }

    public int oldLines() {
      return oldLines;
    }

    public int newStart() {
      return newStart;
    }

    public int newLines() {
      return newLines;
    }

    public List<LineChange> lines() {
      return lines;
    }
  }

  /** Kind of one word segment. */
  public enum WordSegmentKind {
    UNCHANGED("unchanged"),
    REMOVED("removed"),
    ADDED("added");

    private final String value;

    WordSegmentKind(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /** One merged word-level segment. */
  public static final class WordSegment {
    private final WordSegmentKind kind;
    private final String text;

    public WordSegment(WordSegmentKind kind, String text) {
      this.kind = kind;
      this.text = text;
    }

    public WordSegmentKind kind() {
      return kind;
    }

    public String text() {
      return text;
    }
  }

  /** Word-level detail for one paired changed line. */
  public static final class WordDiff {
    private final int oldLine;
    private final int newLine;
    private final List<WordSegment> oldSegments;
    private final List<WordSegment> newSegments;

    public WordDiff(
        int oldLine, int newLine, List<WordSegment> oldSegments, List<WordSegment> newSegments) {
      this.oldLine = oldLine;
      this.newLine = newLine;
      this.oldSegments = immutable(oldSegments);
      this.newSegments = immutable(newSegments);
    }

    public int oldLine() {
      return oldLine;
    }

    public int newLine() {
      return newLine;
    }

    public List<WordSegment> oldSegments() {
      return oldSegments;
    }

    public List<WordSegment> newSegments() {
      return newSegments;
    }
  }

  /** Kind of one semantic change. */
  public enum SemanticChangeKind {
    ADDED("added"),
    REMOVED("removed"),
    CHANGED("changed"),
    RENAMED("renamed"),
    REORDERED("reordered");

    private final String value;

    SemanticChangeKind(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /** One key-path semantic change. */
  public static final class SemanticChange {
    private final SemanticChangeKind kind;
    private final String path;
    private final String before;
    private final String beforeType;
    private final String after;
    private final String afterType;
    private final String from;
    private final String to;
    private final String value;
    private final String valueType;
    private final List<String> beforeOrder;
    private final List<String> afterOrder;

    private SemanticChange(
        SemanticChangeKind kind,
        String path,
        String before,
        String beforeType,
        String after,
        String afterType,
        String from,
        String to,
        String value,
        String valueType,
        List<String> beforeOrder,
        List<String> afterOrder) {
      this.kind = kind;
      this.path = path;
      this.before = before;
      this.beforeType = beforeType;
      this.after = after;
      this.afterType = afterType;
      this.from = from;
      this.to = to;
      this.value = value;
      this.valueType = valueType;
      this.beforeOrder = immutable(beforeOrder);
      this.afterOrder = immutable(afterOrder);
    }

    static SemanticChange added(String path, String after, String afterType) {
      return new SemanticChange(
          SemanticChangeKind.ADDED,
          path,
          null,
          null,
          after,
          afterType,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    static SemanticChange removed(String path, String before, String beforeType) {
      return new SemanticChange(
          SemanticChangeKind.REMOVED,
          path,
          before,
          beforeType,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    static SemanticChange changed(
        String path, String before, String beforeType, String after, String afterType) {
      return new SemanticChange(
          SemanticChangeKind.CHANGED,
          path,
          before,
          beforeType,
          after,
          afterType,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    static SemanticChange renamed(String from, String to, String value, String valueType) {
      return new SemanticChange(
          SemanticChangeKind.RENAMED,
          null,
          null,
          null,
          null,
          null,
          from,
          to,
          value,
          valueType,
          null,
          null);
    }

    static SemanticChange reordered(
        String path, List<String> beforeOrder, List<String> afterOrder) {
      return new SemanticChange(
          SemanticChangeKind.REORDERED,
          path,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          beforeOrder,
          afterOrder);
    }

    public SemanticChangeKind kind() {
      return kind;
    }

    public String path() {
      return path;
    }

    public String before() {
      return before;
    }

    public String beforeType() {
      return beforeType;
    }

    public String after() {
      return after;
    }

    public String afterType() {
      return afterType;
    }

    public String from() {
      return from;
    }

    public String to() {
      return to;
    }

    public String value() {
      return value;
    }

    public String valueType() {
      return valueType;
    }

    public List<String> beforeOrder() {
      return beforeOrder;
    }

    public List<String> afterOrder() {
      return afterOrder;
    }
  }

  /** Kind of one JAR dependency change. */
  public enum JarDependencyChangeKind {
    ADDED("added"),
    REMOVED("removed"),
    SCOPE_CHANGED("scope-changed");

    private final String value;

    JarDependencyChangeKind(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /** One normalized JAR dependency change. */
  public static final class JarDependencyChange {
    private final String name;
    private final JarDependencyChangeKind kind;
    private final String scope;

    public JarDependencyChange(String name, JarDependencyChangeKind kind, String scope) {
      this.name = name;
      this.kind = kind;
      this.scope = scope;
    }

    public String name() {
      return name;
    }

    public JarDependencyChangeKind kind() {
      return kind;
    }

    public String scope() {
      return scope;
    }
  }

  /** Normalized descriptor changes for one descriptor path. */
  public static final class JarDescriptorDiff {
    private final String name;
    private final String status;
    private final List<SemanticChange> fieldChanges;
    private final List<JarDependencyChange> dependencyChanges;

    public JarDescriptorDiff(
        String name,
        String status,
        List<SemanticChange> fieldChanges,
        List<JarDependencyChange> dependencyChanges) {
      this.name = name;
      this.status = status;
      this.fieldChanges = immutable(fieldChanges);
      this.dependencyChanges = immutable(dependencyChanges);
    }

    public String name() {
      return name;
    }

    public String status() {
      return status;
    }

    public List<SemanticChange> fieldChanges() {
      return fieldChanges;
    }

    public List<JarDependencyChange> dependencyChanges() {
      return dependencyChanges;
    }
  }

  /** Metadata-only comparison of two JARs. */
  public static final class JarMetadataDiff {
    private final boolean hashChanged;
    private final long sizeDelta;
    private final List<String> entriesAdded;
    private final List<String> entriesRemoved;
    private final List<JarDescriptorDiff> descriptors;

    public JarMetadataDiff(
        boolean hashChanged,
        long sizeDelta,
        List<String> entriesAdded,
        List<String> entriesRemoved,
        List<JarDescriptorDiff> descriptors) {
      this.hashChanged = hashChanged;
      this.sizeDelta = sizeDelta;
      this.entriesAdded = immutable(entriesAdded);
      this.entriesRemoved = immutable(entriesRemoved);
      this.descriptors = immutable(descriptors);
    }

    public boolean hashChanged() {
      return hashChanged;
    }

    public long sizeDelta() {
      return sizeDelta;
    }

    public List<String> entriesAdded() {
      return entriesAdded;
    }

    public List<String> entriesRemoved() {
      return entriesRemoved;
    }

    public List<JarDescriptorDiff> descriptors() {
      return descriptors;
    }
  }
}
