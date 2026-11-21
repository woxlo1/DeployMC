package io.github.woxlo1.deploymc.core.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic bounded Myers shortest-edit script for two sequences. */
final class BoundedSequenceDiff {

  private BoundedSequenceDiff() {}

  static <T> List<Edit<T>> diff(List<T> before, List<T> after, WorkBudget budget) {
    int n = before.size();
    int m = after.size();
    List<Map<Integer, Integer>> trace = new ArrayList<Map<Integer, Integer>>();
    Map<Integer, Integer> v = new HashMap<Integer, Integer>();
    v.put(1, 0);

    for (int depth = 0; depth <= n + m; depth++) {
      trace.add(new HashMap<Integer, Integer>(v));
      for (int k = -depth; k <= depth; k += 2) {
        budget.consume(1);
        boolean down = chooseDown(depth, k, v);
        int x;
        if (down) {
          Integer value = v.get(k + 1);
          x = value == null ? 0 : value;
        } else {
          Integer value = v.get(k - 1);
          x = value == null ? 1 : value + 1;
        }
        int y = x - k;
        while (x < n && y < m && before.get(x).equals(after.get(y))) {
          x++;
          y++;
          budget.consume(1);
        }
        v.put(k, x);
        if (x >= n && y >= m) {
          return backtrack(trace, depth, n, m);
        }
      }
    }
    throw new IllegalStateException("Myers search did not terminate");
  }

  private static <T> boolean chooseDown(int depth, int k, Map<Integer, Integer> v) {
    if (k == -depth) {
      return true;
    }
    if (k == depth) {
      return false;
    }
    Integer leftValue = v.get(k - 1);
    Integer rightValue = v.get(k + 1);
    int left = leftValue == null ? -1 : leftValue;
    int right = rightValue == null ? -1 : rightValue;
    return left < right;
  }

  private static <T> List<Edit<T>> backtrack(
      List<Map<Integer, Integer>> trace, int finalDepth, int n, int m) {
    List<Edit<T>> reversed = new ArrayList<Edit<T>>();
    int x = n;
    int y = m;
    for (int depth = finalDepth; depth >= 0; depth--) {
      Map<Integer, Integer> v = trace.get(depth);
      int k = x - y;
      boolean down = chooseDown(depth, k, v);
      int previousK = down ? k + 1 : k - 1;
      Integer previousXValue = v.get(previousK);
      int previousX = previousXValue == null ? 0 : previousXValue;
      int previousY = previousX - previousK;
      while (x > previousX && y > previousY) {
        reversed.add(new Edit<T>(EditKind.SAME, x - 1, y - 1));
        x--;
        y--;
      }
      if (depth > 0) {
        if (x == previousX) {
          reversed.add(new Edit<T>(EditKind.ADDED, -1, y - 1));
          y--;
        } else {
          reversed.add(new Edit<T>(EditKind.REMOVED, x - 1, -1));
          x--;
        }
      }
      x = previousX;
      y = previousY;
    }
    while (x > 0) {
      reversed.add(new Edit<T>(EditKind.REMOVED, x - 1, -1));
      x--;
    }
    while (y > 0) {
      reversed.add(new Edit<T>(EditKind.ADDED, -1, y - 1));
      y--;
    }
    Collections.reverse(reversed);
    return reversed;
  }

  /** One edit operation. */
  static final class Edit<T> {
    private final EditKind kind;
    private final int beforeIndex;
    private final int afterIndex;

    Edit(EditKind kind, int beforeIndex, int afterIndex) {
      this.kind = kind;
      this.beforeIndex = beforeIndex;
      this.afterIndex = afterIndex;
    }

    EditKind kind() {
      return kind;
    }

    int beforeIndex() {
      return beforeIndex;
    }

    int afterIndex() {
      return afterIndex;
    }
  }

  enum EditKind {
    SAME,
    REMOVED,
    ADDED
  }
}
