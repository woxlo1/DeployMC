package io.github.woxlo1.deploymc.core.validation;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Replaces one single-line YAML scalar token without rewriting any other byte. */
public final class YamlScalarEditor {

  private static final Set<Tag> STANDARD_TAGS =
      Collections.unmodifiableSet(
          new HashSet<Tag>(
              Arrays.asList(
                  Tag.STR,
                  Tag.INT,
                  Tag.FLOAT,
                  Tag.BOOL,
                  Tag.NULL,
                  Tag.SEQ,
                  Tag.MAP,
                  Tag.BINARY,
                  Tag.TIMESTAMP,
                  Tag.SET,
                  Tag.OMAP,
                  Tag.PAIRS,
                  Tag.MERGE)));

  private static final String PLAIN_FORBIDDEN_START = "{}[],&*!|>'\"%@`#";

  private YamlScalarEditor() {}

  /**
   * Returns a new byte array. The source is never mutated and no file is opened. Only the selected
   * scalar token is replaced; comments, indentation, key spelling, and line endings stay byte for
   * byte identical.
   */
  public static byte[] update(byte[] source, String pointer, String value, ValidationLimits limits)
      throws ValidationException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(limits, "limits");
    TextInput input = TextInput.decode(source, limits);
    if (input.hasEncodingIssue()) {
      throw new CommentEditException("invalid-source");
    }
    List<String> tokens = parsePointer(pointer);
    Node root = compose(input.text(), limits);
    if (root == null) {
      throw new CommentEditException("missing-target");
    }
    Node current = root;
    IdentityHashMap<Node, Boolean> visited = new IdentityHashMap<Node, Boolean>();
    for (String token : tokens) {
      requireUniqueNode(current, visited);
      if ("<<".equals(token)) {
        throw new CommentEditException("unsupported-construct");
      }
      if (!(current instanceof MappingNode)) {
        throw new CommentEditException("non-mapping-path");
      }
      Node selected = null;
      int matches = 0;
      for (NodeTuple tuple : ((MappingNode) current).getValue()) {
        if (!(tuple.getKeyNode() instanceof ScalarNode)) {
          throw new CommentEditException("unsupported-construct");
        }
        String key = ((ScalarNode) tuple.getKeyNode()).getValue();
        if (token.equals(key)) {
          matches++;
          if (selected == null) {
            selected = tuple.getValueNode();
          }
        }
      }
      if (matches == 0) {
        throw new CommentEditException("missing-target");
      }
      if (matches > 1) {
        throw new CommentEditException("duplicate-key");
      }
      current = selected;
    }
    requireUniqueNode(current, visited);
    if (!(current instanceof ScalarNode)) {
      throw new CommentEditException("non-scalar-target");
    }
    ScalarNode scalar = (ScalarNode) current;
    if (scalar.getTag() == null || !STANDARD_TAGS.contains(scalar.getTag())) {
      throw new CommentEditException("unsupported-construct");
    }
    int start = scalar.getStartMark().getIndex();
    int end = scalar.getEndMark().getIndex();
    String text = input.text();
    if (start < 0 || end > text.length() || end <= start) {
      throw new CommentEditException("invalid-target");
    }
    while (end > start && isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    String original = text.substring(start, end);
    if (original.indexOf('\n') >= 0 || original.indexOf('\r') >= 0) {
      throw new CommentEditException("multiline-value");
    }
    ScalarStyle style = scalar.getScalarStyle();
    char quote;
    if (style == ScalarStyle.PLAIN) {
      quote = '\0';
    } else if (style == ScalarStyle.SINGLE_QUOTED) {
      quote = '\'';
    } else if (style == ScalarStyle.DOUBLE_QUOTED) {
      quote = '"';
    } else {
      throw new CommentEditException("unsupported-scalar-style");
    }
    byte[] replacement;
    if (quote == '\0') {
      requireSafePlainScalar(value);
      replacement = value.getBytes(StandardCharsets.UTF_8);
    } else if (quote == '\'') {
      requireSafeQuotedScalar(value);
      replacement = ("'" + value.replace("'", "''") + "'").getBytes(StandardCharsets.UTF_8);
    } else if (quote == '"') {
      replacement = ("\"" + escapeDoubleQuoted(value) + "\"").getBytes(StandardCharsets.UTF_8);
    } else {
      throw new CommentEditException("unsupported-scalar-style");
    }
    long fromLong = input.byteOffset(start);
    long toLong = input.byteOffset(end);
    if (fromLong < 0
        || toLong > source.length
        || toLong < fromLong
        || toLong > Integer.MAX_VALUE
        || fromLong > Integer.MAX_VALUE) {
      throw new CommentEditException("invalid-target");
    }
    int from = (int) fromLong;
    int to = (int) toLong;
    byte[] result = new byte[source.length - (to - from) + replacement.length];
    System.arraycopy(source, 0, result, 0, from);
    System.arraycopy(replacement, 0, result, from, replacement.length);
    System.arraycopy(source, to, result, from + replacement.length, source.length - to);
    return result;
  }

  private static void requireUniqueNode(Node node, IdentityHashMap<Node, Boolean> visited)
      throws CommentEditException {
    if (visited.put(node, Boolean.TRUE) != null) {
      throw new CommentEditException("unsupported-construct");
    }
    if (node.getAnchor() != null && !node.getAnchor().isEmpty()) {
      throw new CommentEditException("unsupported-construct");
    }
  }

  private static List<String> parsePointer(String pointer) throws CommentEditException {
    if (pointer == null || pointer.isEmpty() || pointer.charAt(0) != '/') {
      throw new CommentEditException("invalid-pointer");
    }
    String[] parts = pointer.substring(1).split("/", -1);
    List<String> tokens = new ArrayList<String>(parts.length);
    for (String part : parts) {
      if (part.isEmpty()) {
        throw new CommentEditException("invalid-pointer");
      }
      try {
        tokens.add(Pointers.unescape(part));
      } catch (IllegalArgumentException invalid) {
        throw new CommentEditException("invalid-pointer");
      }
    }
    return tokens;
  }

  private static Node compose(String text, ValidationLimits limits) throws ValidationException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(true);
    options.setAllowRecursiveKeys(false);
    options.setMaxAliasesForCollections(limits.maxAliasExpansionsPerNode());
    options.setNestingDepthLimit(limits.maxDepth());
    options.setCodePointLimit(limits.maxCodePoints());
    options.setTagInspector(tag -> STANDARD_TAGS.contains(tag));
    try {
      Yaml yaml = new Yaml(new SafeConstructor(options));
      Iterator<Node> documents = yaml.composeAll(new StringReader(text)).iterator();
      Node first = null;
      while (documents.hasNext()) {
        if (first != null) {
          throw new CommentEditException("multiple-documents");
        }
        first = documents.next();
      }
      return first;
    } catch (CommentEditException editFailure) {
      throw editFailure;
    } catch (RuntimeException invalid) {
      throw new CommentEditException("invalid-document");
    }
  }

  private static void requireSafePlainScalar(String value) throws CommentEditException {
    if (value.isEmpty() || !value.equals(value.trim())) {
      throw new CommentEditException("unsafe-plain-value");
    }
    if (value.contains(": ") || value.contains(" #") || value.endsWith(":")) {
      throw new CommentEditException("unsafe-plain-value");
    }
    if ("---".equals(value) || "...".equals(value)) {
      throw new CommentEditException("unsafe-plain-value");
    }
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current < 0x20 || current == 0x7f) {
        throw new CommentEditException("unsafe-plain-value");
      }
      if (i == 0 && PLAIN_FORBIDDEN_START.indexOf(current) >= 0) {
        throw new CommentEditException("unsafe-plain-value");
      }
      if (current == '[' || current == ']' || current == '{' || current == '}' || current == ',') {
        throw new CommentEditException("unsafe-plain-value");
      }
      if (i == 0
          && (current == '-' || current == '?' || current == ':')
          && (value.length() == 1 || isWhitespace(value.charAt(1)))) {
        throw new CommentEditException("unsafe-plain-value");
      }
    }
  }

  private static void requireSafeQuotedScalar(String value) throws CommentEditException {
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current < 0x20 || current == 0x7f) {
        throw new CommentEditException("unsafe-quoted-value");
      }
    }
  }

  private static String escapeDoubleQuoted(String value) {
    StringBuilder result = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      switch (current) {
        case '\\':
          result.append("\\\\");
          break;
        case '"':
          result.append("\\\"");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        default:
          if (current < 0x20 || current == 0x7f) {
            result.append("\\u");
            String hex = Integer.toHexString(current);
            for (int padding = hex.length(); padding < 4; padding++) {
              result.append('0');
            }
            result.append(hex);
          } else {
            result.append(current);
          }
      }
    }
    return result.toString();
  }

  private static boolean isWhitespace(char value) {
    return value == ' ' || value == '\t' || value == '\n' || value == '\r';
  }
}
