package io.github.woxlo1.deploymc.core.config;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Computes deterministic dotted-path differences between two validated configurations. */
final class ConfigDiff {

  private ConfigDiff() {}

  static Set<String> changedPaths(Map<String, Object> before, Map<String, Object> after) {
    TreeSet<String> result = new TreeSet<String>();
    collect(before, after, "", result);
    return result;
  }

  private static void collect(
      Map<String, Object> before, Map<String, Object> after, String prefix, Set<String> result) {
    TreeSet<String> keys = new TreeSet<String>();
    keys.addAll(before.keySet());
    keys.addAll(after.keySet());
    for (String key : keys) {
      String path = prefix.isEmpty() ? key : prefix + "." + key;
      boolean inBefore = before.containsKey(key);
      boolean inAfter = after.containsKey(key);
      if (!inBefore || !inAfter) {
        result.add(path);
        continue;
      }
      compare(before.get(key), after.get(key), path, result);
    }
  }

  private static void compare(Object before, Object after, String path, Set<String> result) {
    if (before instanceof Map && after instanceof Map) {
      collect(castMap(before), castMap(after), path, result);
      return;
    }
    if (before instanceof java.util.List && after instanceof java.util.List) {
      java.util.List<?> left = (java.util.List<?>) before;
      java.util.List<?> right = (java.util.List<?>) after;
      if (left.size() != right.size()) {
        result.add(path);
        return;
      }
      for (int index = 0; index < left.size(); index++) {
        compare(left.get(index), right.get(index), path + "[" + index + "]", result);
      }
      return;
    }
    if (before == null ? after != null : !before.equals(after)) {
      result.add(path);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }
}
