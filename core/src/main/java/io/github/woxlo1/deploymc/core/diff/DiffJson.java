package io.github.woxlo1.deploymc.core.diff;

import io.github.woxlo1.deploymc.core.diff.DiffResult.ContentInfo;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDependencyChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDescriptorDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarMetadataDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineHunk;
import io.github.woxlo1.deploymc.core.diff.DiffResult.SemanticChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordSegment;
import java.util.List;

/** Deterministic JSON projection used by contract tests and later transport adapters. */
final class DiffJson {

  private DiffJson() {}

  static String toJson(DiffResult result) {
    StringBuilder json = new StringBuilder();
    json.append('{');
    field(json, "status", result.status().value(), true);
    field(json, "strategy", result.strategy().value(), true);
    nullableField(json, "reason", result.reason(), true);
    nullableField(json, "limit", result.limit(), true);
    nullableNumber(json, "observed", result.observed(), true);
    key(json, "before");
    contentInfo(json, result.before());
    json.append(',');
    key(json, "after");
    contentInfo(json, result.after());
    json.append(',');
    key(json, "notes");
    strings(json, result.notes());
    json.append(',');
    key(json, "lineHunks");
    hunks(json, result.lineHunks());
    json.append(',');
    key(json, "wordDiffs");
    wordDiffs(json, result.wordDiffs());
    json.append(',');
    key(json, "semanticChanges");
    semanticChanges(json, result.semanticChanges());
    json.append(',');
    key(json, "jarMetadata");
    if (result.jarMetadata() == null) {
      json.append("null");
    } else {
      jarMetadata(json, result.jarMetadata());
    }
    return json.append('}').toString();
  }

  private static void contentInfo(StringBuilder json, ContentInfo info) {
    json.append('{');
    numberField(json, "size", info.size(), true);
    field(json, "sha256", info.sha256(), false);
    json.append('}');
  }

  private static void hunks(StringBuilder json, List<LineHunk> hunks) {
    json.append('[');
    for (int i = 0; i < hunks.size(); i++) {
      LineHunk hunk = hunks.get(i);
      json.append('{');
      numberField(json, "oldStart", hunk.oldStart(), true);
      numberField(json, "oldLines", hunk.oldLines(), true);
      numberField(json, "newStart", hunk.newStart(), true);
      numberField(json, "newLines", hunk.newLines(), true);
      key(json, "lines");
      json.append('[');
      for (int j = 0; j < hunk.lines().size(); j++) {
        LineChange line = hunk.lines().get(j);
        json.append('{');
        field(json, "kind", line.kind().value(), true);
        nullableNumber(json, "oldLine", line.oldLine(), true);
        nullableNumber(json, "newLine", line.newLine(), true);
        field(json, "text", line.text(), true);
        field(json, "ending", line.ending().value(), false);
        json.append('}');
        if (j + 1 < hunk.lines().size()) {
          json.append(',');
        }
      }
      json.append("]}");
      if (i + 1 < hunks.size()) {
        json.append(',');
      }
    }
    json.append(']');
  }

  private static void wordDiffs(StringBuilder json, List<WordDiff> diffs) {
    json.append('[');
    for (int i = 0; i < diffs.size(); i++) {
      WordDiff diff = diffs.get(i);
      json.append('{');
      numberField(json, "oldLine", diff.oldLine(), true);
      numberField(json, "newLine", diff.newLine(), true);
      key(json, "oldSegments");
      segments(json, diff.oldSegments());
      json.append(',');
      key(json, "newSegments");
      segments(json, diff.newSegments());
      json.append('}');
      if (i + 1 < diffs.size()) {
        json.append(',');
      }
    }
    json.append(']');
  }

  private static void segments(StringBuilder json, List<WordSegment> segments) {
    json.append('[');
    for (int i = 0; i < segments.size(); i++) {
      WordSegment segment = segments.get(i);
      json.append('{');
      field(json, "kind", segment.kind().value(), true);
      field(json, "text", segment.text(), false);
      json.append('}');
      if (i + 1 < segments.size()) {
        json.append(',');
      }
    }
    json.append(']');
  }

  private static void semanticChanges(StringBuilder json, List<SemanticChange> changes) {
    json.append('[');
    for (int i = 0; i < changes.size(); i++) {
      SemanticChange change = changes.get(i);
      json.append('{');
      field(json, "kind", change.kind().value(), true);
      nullableField(json, "path", change.path(), true);
      nullableField(json, "before", change.before(), true);
      nullableField(json, "beforeType", change.beforeType(), true);
      nullableField(json, "after", change.after(), true);
      nullableField(json, "afterType", change.afterType(), true);
      nullableField(json, "from", change.from(), true);
      nullableField(json, "to", change.to(), true);
      nullableField(json, "value", change.value(), true);
      nullableField(json, "valueType", change.valueType(), true);
      nullableStrings(json, "beforeOrder", change.beforeOrder(), true);
      nullableStrings(json, "afterOrder", change.afterOrder(), false);
      json.append('}');
      if (i + 1 < changes.size()) {
        json.append(',');
      }
    }
    json.append(']');
  }

  private static void jarMetadata(StringBuilder json, JarMetadataDiff metadata) {
    json.append('{');
    booleanField(json, "hashChanged", metadata.hashChanged(), true);
    numberField(json, "sizeDelta", metadata.sizeDelta(), true);
    key(json, "entriesAdded");
    strings(json, metadata.entriesAdded());
    json.append(',');
    key(json, "entriesRemoved");
    strings(json, metadata.entriesRemoved());
    json.append(',');
    key(json, "descriptors");
    json.append('[');
    for (int i = 0; i < metadata.descriptors().size(); i++) {
      JarDescriptorDiff descriptor = metadata.descriptors().get(i);
      json.append('{');
      field(json, "name", descriptor.name(), true);
      field(json, "status", descriptor.status(), true);
      key(json, "fieldChanges");
      semanticChanges(json, descriptor.fieldChanges());
      json.append(',');
      key(json, "dependencyChanges");
      json.append('[');
      for (int j = 0; j < descriptor.dependencyChanges().size(); j++) {
        JarDependencyChange change = descriptor.dependencyChanges().get(j);
        json.append('{');
        field(json, "name", change.name(), true);
        field(json, "kind", change.kind().value(), true);
        field(json, "scope", change.scope(), false);
        json.append('}');
        if (j + 1 < descriptor.dependencyChanges().size()) {
          json.append(',');
        }
      }
      json.append("]}");
      if (i + 1 < metadata.descriptors().size()) {
        json.append(',');
      }
    }
    json.append("]}");
  }

  private static void strings(StringBuilder json, List<String> values) {
    json.append('[');
    for (int i = 0; i < values.size(); i++) {
      string(json, values.get(i));
      if (i + 1 < values.size()) {
        json.append(',');
      }
    }
    json.append(']');
  }

  private static void nullableStrings(
      StringBuilder json, String key, List<String> values, boolean comma) {
    key(json, key);
    if (values == null) {
      json.append("null");
    } else {
      strings(json, values);
    }
    trailing(json, comma);
  }

  private static void field(StringBuilder json, String key, String value, boolean comma) {
    key(json, key);
    string(json, value);
    trailing(json, comma);
  }

  private static void nullableField(StringBuilder json, String key, String value, boolean comma) {
    key(json, key);
    if (value == null) {
      json.append("null");
    } else {
      string(json, value);
    }
    trailing(json, comma);
  }

  private static void numberField(StringBuilder json, String key, long value, boolean comma) {
    key(json, key);
    json.append(value);
    trailing(json, comma);
  }

  private static void nullableNumber(StringBuilder json, String key, Integer value, boolean comma) {
    nullableNumber(json, key, value == null ? null : Long.valueOf(value.longValue()), comma);
  }

  private static void nullableNumber(StringBuilder json, String key, Long value, boolean comma) {
    key(json, key);
    if (value == null) {
      json.append("null");
    } else {
      json.append(value.longValue());
    }
    trailing(json, comma);
  }

  private static void booleanField(StringBuilder json, String key, boolean value, boolean comma) {
    key(json, key);
    json.append(value ? "true" : "false");
    trailing(json, comma);
  }

  private static void key(StringBuilder json, String key) {
    string(json, key);
    json.append(':');
  }

  private static void trailing(StringBuilder json, boolean comma) {
    if (comma) {
      json.append(',');
    }
  }

  private static void string(StringBuilder json, String value) {
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          json.append("\\\"");
          break;
        case '\\':
          json.append("\\\\");
          break;
        case '\b':
          json.append("\\b");
          break;
        case '\f':
          json.append("\\f");
          break;
        case '\n':
          json.append("\\n");
          break;
        case '\r':
          json.append("\\r");
          break;
        case '\t':
          json.append("\\t");
          break;
        default:
          if (c < 0x20
              || c == 0x7f
              || (c >= 0x202a && c <= 0x202e)
              || (c >= 0x2066 && c <= 0x2069)) {
            json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
          } else {
            json.append(c);
          }
      }
    }
    json.append('"');
  }
}
