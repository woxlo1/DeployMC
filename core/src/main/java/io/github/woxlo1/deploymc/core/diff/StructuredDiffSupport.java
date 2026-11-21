package io.github.woxlo1.deploymc.core.diff;

import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.diff.DiffResult.SemanticChange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Key-path semantic comparison of hardened YAML and JSON values. */
final class StructuredDiffSupport {

  private StructuredDiffSupport() {}

  static List<SemanticChange> diff(String before, String after, DiffLimits limits) {
    int[] nodeCount = new int[] {0};
    ValueNode beforeNode = parseAndCount(before, limits, nodeCount);
    ValueNode afterNode = parseAndCount(after, limits, nodeCount);
    List<SemanticChange> changes = new ArrayList<SemanticChange>();
    compare(beforeNode, afterNode, "", changes);
    Collections.sort(
        changes,
        new Comparator<SemanticChange>() {
          @Override
          public int compare(SemanticChange left, SemanticChange right) {
            String leftPath = left.path() == null ? "" : left.path();
            String rightPath = right.path() == null ? "" : right.path();
            int byPath = leftPath.compareTo(rightPath);
            return byPath != 0 ? byPath : left.kind().value().compareTo(right.kind().value());
          }
        });
    return changes;
  }

  private static ValueNode parseAndCount(String source, DiffLimits limits, int[] count) {
    YamlValue value;
    try {
      value = ConfigYamlParser.parseSingleDocument(source, limits.maxBytesPerSide());
    } catch (ConfigException exception) {
      throw new ParseFallbackException();
    }
    return toNode(value, limits, count);
  }

  private static ValueNode toNode(YamlValue value, DiffLimits limits, int[] count) {
    count[0]++;
    if (count[0] > limits.maxSemanticNodes()) {
      throw new LimitExceededException("semantic-nodes", count[0]);
    }
    if (value.isObject()) {
      LinkedHashMap<String, ValueNode> object = new LinkedHashMap<String, ValueNode>();
      for (String key : value.keys()) {
        object.put(key, toNode(value.get(key), limits, count));
      }
      return new ValueNode("object", canonicalObject(object), object, null);
    }
    if (value.isArray()) {
      List<ValueNode> array = new ArrayList<ValueNode>();
      for (YamlValue item : value.items()) {
        array.add(toNode(item, limits, count));
      }
      return new ValueNode("array", canonicalArray(array), null, array);
    }
    return new ValueNode(value.jsonType(), canonicalScalar(value), null, null);
  }

  private static String canonicalScalar(YamlValue value) {
    if (value.isNull()) {
      return "null";
    }
    Object raw = value.raw();
    if (raw instanceof Boolean) {
      return ((Boolean) raw).booleanValue() ? "true" : "false";
    }
    if (raw instanceof Double) {
      return Double.toString(((Double) raw).doubleValue());
    }
    if (raw instanceof Float) {
      return Double.toString(((Float) raw).doubleValue());
    }
    return String.valueOf(raw);
  }

  private static String canonicalObject(Map<String, ValueNode> object) {
    StringBuilder result = new StringBuilder("{");
    for (String key : new TreeSet<String>(object.keySet())) {
      result.append(key.length()).append(':').append(key).append('=');
      result.append(object.get(key).canonical).append(';');
    }
    return result.append('}').toString();
  }

  private static String canonicalArray(List<ValueNode> array) {
    StringBuilder result = new StringBuilder("[");
    for (ValueNode item : array) {
      result.append(item.canonical.length()).append(':').append(item.canonical).append(';');
    }
    return result.append(']').toString();
  }

  private static void compare(
      ValueNode before, ValueNode after, String path, List<SemanticChange> changes) {
    if (before.object != null && after.object != null) {
      compareObjects(before.object, after.object, path, changes);
      return;
    }
    if (before.array != null && after.array != null) {
      compareArrays(before.array, after.array, path, changes);
      return;
    }
    if (!before.type.equals(after.type) || !before.canonical.equals(after.canonical)) {
      changes.add(
          SemanticChange.changed(path, before.canonical, before.type, after.canonical, after.type));
    }
  }

  private static void compareObjects(
      Map<String, ValueNode> before,
      Map<String, ValueNode> after,
      String path,
      List<SemanticChange> changes) {
    int changesBeforeOrder = changes.size();
    for (String key : before.keySet()) {
      if (after.containsKey(key)) {
        compare(before.get(key), after.get(key), child(path, key), changes);
      }
    }

    TreeSet<String> removed = new TreeSet<String>();
    TreeSet<String> added = new TreeSet<String>();
    for (String key : before.keySet()) {
      if (!after.containsKey(key)) {
        removed.add(key);
      }
    }
    for (String key : after.keySet()) {
      if (!before.containsKey(key)) {
        added.add(key);
      }
    }

    TreeSet<String> pairedBefore = new TreeSet<String>();
    TreeSet<String> pairedAfter = new TreeSet<String>();
    for (String oldKey : removed) {
      for (String newKey : added) {
        if (pairedAfter.contains(newKey)) {
          continue;
        }
        ValueNode oldValue = before.get(oldKey);
        ValueNode newValue = after.get(newKey);
        if (oldValue.canonical.equals(newValue.canonical) && oldValue.type.equals(newValue.type)) {
          changes.add(
              SemanticChange.renamed(
                  child(path, oldKey), child(path, newKey), oldValue.canonical, oldValue.type));
          pairedBefore.add(oldKey);
          pairedAfter.add(newKey);
          break;
        }
      }
    }
    for (String key : removed) {
      if (!pairedBefore.contains(key)) {
        ValueNode value = before.get(key);
        changes.add(SemanticChange.removed(child(path, key), value.canonical, value.type));
      }
    }
    for (String key : added) {
      if (!pairedAfter.contains(key)) {
        ValueNode value = after.get(key);
        changes.add(SemanticChange.added(child(path, key), value.canonical, value.type));
      }
    }

    if (before.keySet().equals(after.keySet()) && changes.size() == changesBeforeOrder) {
      List<String> beforeOrder = new ArrayList<String>(before.keySet());
      List<String> afterOrder = new ArrayList<String>(after.keySet());
      if (!beforeOrder.equals(afterOrder)) {
        changes.add(SemanticChange.reordered(path, beforeOrder, afterOrder));
      }
    }
  }

  private static void compareArrays(
      List<ValueNode> before, List<ValueNode> after, String path, List<SemanticChange> changes) {
    int shared = Math.min(before.size(), after.size());
    for (int i = 0; i < shared; i++) {
      compare(before.get(i), after.get(i), child(path, Integer.toString(i)), changes);
    }
    for (int i = shared; i < before.size(); i++) {
      ValueNode value = before.get(i);
      changes.add(
          SemanticChange.removed(child(path, Integer.toString(i)), value.canonical, value.type));
    }
    for (int i = shared; i < after.size(); i++) {
      ValueNode value = after.get(i);
      changes.add(
          SemanticChange.added(child(path, Integer.toString(i)), value.canonical, value.type));
    }
  }

  private static String child(String path, String segment) {
    String escaped = segment.replace("~", "~0").replace("/", "~1");
    return path + "/" + escaped;
  }

  /** Signals that structured parsing failed and the caller should use the line fallback. */
  static final class ParseFallbackException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private static final class ValueNode {
    private final String type;
    private final String canonical;
    private final Map<String, ValueNode> object;
    private final List<ValueNode> array;

    ValueNode(String type, String canonical, Map<String, ValueNode> object, List<ValueNode> array) {
      this.type = type;
      this.canonical = canonical;
      this.object = object;
      this.array = array;
    }
  }
}
