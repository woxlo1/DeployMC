package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.List;

/** Bounded glob matcher for '/' separated paths: '?', '*', and '**'. */
final class GlobMatcher {

  private static final int LITERAL = 0;
  private static final int ONE = 1;
  private static final int ANY_SEGMENT = 2;
  private static final int ANY_DEEP = 3;

  private final List<Integer> kinds = new ArrayList<Integer>();
  private final List<Character> literals = new ArrayList<Character>();
  private final int literalCount;

  GlobMatcher(String pattern) {
    if (pattern == null || pattern.isEmpty() || pattern.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("invalid path pattern");
    }
    int literalsSeen = 0;
    for (int i = 0; i < pattern.length(); i++) {
      char current = pattern.charAt(i);
      if (current == '*') {
        if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
          kinds.add(Integer.valueOf(ANY_DEEP));
          literals.add(Character.valueOf('\0'));
          i++;
        } else {
          kinds.add(Integer.valueOf(ANY_SEGMENT));
          literals.add(Character.valueOf('\0'));
        }
      } else if (current == '?') {
        kinds.add(Integer.valueOf(ONE));
        literals.add(Character.valueOf('\0'));
      } else {
        kinds.add(Integer.valueOf(LITERAL));
        literals.add(Character.valueOf(current));
        literalsSeen++;
      }
    }
    literalCount = literalsSeen;
  }

  int literalCount() {
    return literalCount;
  }

  boolean matches(String text) {
    boolean[][] reachable = new boolean[kinds.size() + 1][text.length() + 1];
    reachable[0][0] = true;
    for (int token = 0; token < kinds.size(); token++) {
      int kind = kinds.get(token).intValue();
      for (int position = 0; position <= text.length(); position++) {
        if (!reachable[token][position]) {
          continue;
        }
        if (kind == LITERAL) {
          if (position < text.length()
              && text.charAt(position) == literals.get(token).charValue()) {
            reachable[token + 1][position + 1] = true;
          }
        } else if (kind == ONE) {
          if (position < text.length() && text.charAt(position) != '/') {
            reachable[token + 1][position + 1] = true;
          }
        } else if (kind == ANY_SEGMENT) {
          for (int next = position;
              next <= text.length() && (next == position || text.charAt(next - 1) != '/');
              next++) {
            reachable[token + 1][next] = true;
          }
        } else {
          for (int next = position; next <= text.length(); next++) {
            reachable[token + 1][next] = true;
          }
        }
      }
    }
    return reachable[kinds.size()][text.length()];
  }
}
