package io.github.woxlo1.deploymc.core.validation;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Bounded, side-effect-free YAML validator built on a validator-specific hardened loader. */
public final class YamlValidator implements ContentValidator {

  private static final Set<Tag> STANDARD_TAGS = standardTags();
  private static final int MAX_TAG_PARAMETER_LENGTH = 256;
  private static final String ALIAS_ISSUE = "validation.yaml-alias-limit";
  private static final String NODE_ISSUE = "validation.yaml-node-limit";
  private static final String DEPTH_ISSUE = "validation.yaml-depth";

  @Override
  public String id() {
    return "yaml";
  }

  @Override
  public ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException {
    String path = PathRules.normalize(relativePath);
    TextInput input = TextInput.decode(content, limits);
    if (input.hasEncodingIssue()) {
      return ValidationResult.matched(
          path, id(), Collections.singletonList(input.encodingIssue()), limits);
    }

    List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
    scanTabs(input, issues);

    final Set<String> rejectedTags = new LinkedHashSet<String>();
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(true);
    options.setAllowRecursiveKeys(false);
    options.setMaxAliasesForCollections(limits.maxAliasExpansionsPerNode());
    options.setNestingDepthLimit(limits.maxDepth());
    options.setCodePointLimit(limits.maxCodePoints());
    options.setTagInspector(
        new TagInspector() {
          @Override
          public boolean isGlobalTagAllowed(Tag tag) {
            if (isStandardTag(tag)) {
              return true;
            }
            rejectedTags.add(tagText(tag));
            return false;
          }
        });
    Yaml yaml = new Yaml(new SafeConstructor(options));

    List<Node> documents = new ArrayList<Node>();
    try {
      Iterator<Node> composed = yaml.composeAll(new StringReader(input.text())).iterator();
      while (composed.hasNext()) {
        Node document = composed.next();
        if (documents.size() == 1) {
          issues.add(
              issue(
                  "validation.yaml-multiple-documents",
                  "",
                  document.getStartMark(),
                  input,
                  Collections.<String>emptyList()));
        }
        documents.add(document);
      }
      WalkState state = new WalkState();
      for (Node document : documents) {
        visit(document, "", 0, input, limits, state, issues);
      }
    } catch (YAMLException failure) {
      addFailure(failure, rejectedTags, input, limits, issues);
    } catch (RuntimeException failure) {
      if (failure.getClass().getName().startsWith("org.yaml.snakeyaml")) {
        issues.add(
            issue("validation.yaml-syntax", "", null, input, Collections.<String>emptyList()));
      } else {
        throw failure;
      }
    }
    return ValidationResult.matched(path, id(), issues, limits);
  }

  private static void visit(
      Node node,
      String pointer,
      int depth,
      TextInput input,
      ValidationLimits limits,
      WalkState state,
      List<ValidationIssue> issues)
      throws ValidationLimitException {
    if (++state.visits > limits.maxNodes()) {
      throw new ValidationLimitException(NODE_ISSUE, "maxNodes", state.visits, limits.maxNodes());
    }

    Integer known = state.references.get(node);
    int references = known == null ? 1 : known.intValue() + 1;
    state.references.put(node, Integer.valueOf(references));

    if (!isStandardTag(node.getTag())) {
      issues.add(
          issue(
              "validation.yaml-tag-rejected",
              pointer,
              node.getStartMark(),
              input,
              Collections.singletonList(safeTag(tagText(node.getTag())))));
    }

    if (state.stack.containsKey(node)) {
      throw new ValidationLimitException(
          ALIAS_ISSUE,
          "maxAliasExpansionsPerNode",
          (long) limits.maxAliasExpansionsPerNode() + 1L,
          limits.maxAliasExpansionsPerNode());
    }
    if (state.visited.containsKey(node)) {
      if (references > limits.maxAliasExpansionsPerNode()) {
        throw new ValidationLimitException(
            ALIAS_ISSUE,
            "maxAliasExpansionsPerNode",
            references,
            limits.maxAliasExpansionsPerNode());
      }
      return;
    }

    state.visited.put(node, Boolean.TRUE);
    state.stack.put(node, Boolean.TRUE);
    try {
      if (node instanceof MappingNode) {
        int nested = nextDepth(depth, limits);
        Set<String> keys = new HashSet<String>();
        for (NodeTuple tuple : ((MappingNode) node).getValue()) {
          Node keyNode = tuple.getKeyNode();
          String key = keyText(keyNode);
          String childPointer = pointer;
          if (key == null) {
            issues.add(
                issue(
                    "validation.yaml-syntax",
                    pointer,
                    keyNode.getStartMark(),
                    input,
                    Collections.<String>emptyList()));
          } else {
            childPointer = Pointers.append(pointer, key);
            if (!keys.add(key)) {
              issues.add(
                  issue(
                      "validation.yaml-duplicate-key",
                      childPointer,
                      keyNode.getStartMark(),
                      input,
                      Collections.<String>emptyList()));
            }
          }
          visit(keyNode, pointer, nested, input, limits, state, issues);
          visit(tuple.getValueNode(), childPointer, nested, input, limits, state, issues);
        }
        return;
      }
      if (node instanceof SequenceNode) {
        int nested = nextDepth(depth, limits);
        List<Node> items = ((SequenceNode) node).getValue();
        for (int i = 0; i < items.size(); i++) {
          visit(
              items.get(i),
              Pointers.append(pointer, Integer.toString(i)),
              nested,
              input,
              limits,
              state,
              issues);
        }
        return;
      }
      if (!(node instanceof ScalarNode)) {
        issues.add(
            issue(
                "validation.yaml-syntax",
                pointer,
                node.getStartMark(),
                input,
                Collections.<String>emptyList()));
      }
    } finally {
      state.stack.remove(node);
    }
  }

  private static int nextDepth(int depth, ValidationLimits limits) throws ValidationLimitException {
    int nested = depth + 1;
    if (nested > limits.maxDepth()) {
      throw new ValidationLimitException(DEPTH_ISSUE, "maxDepth", nested, limits.maxDepth());
    }
    return nested;
  }

  private static void scanTabs(TextInput input, List<ValidationIssue> issues) {
    String text = input.text();
    int lineStart = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i < text.length() && text.charAt(i) != '\n') {
        continue;
      }
      for (int index = lineStart; index < i; index++) {
        char current = text.charAt(index);
        if (current == ' ') {
          continue;
        }
        if (current == '\t') {
          issues.add(
              issueAt("validation.yaml-tab", "", index, input, Collections.<String>emptyList()));
        }
        break;
      }
      lineStart = i + 1;
    }
  }

  private static void addFailure(
      YAMLException failure,
      Set<String> rejectedTags,
      TextInput input,
      ValidationLimits limits,
      List<ValidationIssue> issues)
      throws ValidationLimitException {
    if (!rejectedTags.isEmpty()) {
      Mark mark = problemMark(failure);
      for (String tag : rejectedTags) {
        issues.add(
            issue(
                "validation.yaml-tag-rejected",
                "",
                mark,
                input,
                Collections.singletonList(safeTag(tag))));
      }
      return;
    }
    String message = failureText(failure).toLowerCase(Locale.ROOT);
    if (message.contains("nesting depth")) {
      throw new ValidationLimitException(
          DEPTH_ISSUE, "maxDepth", (long) limits.maxDepth() + 1L, limits.maxDepth());
    }
    if (message.contains("codepointlimit") || message.contains("code point limit")) {
      throw new ValidationLimitException(
          "maxCodePoints", (long) limits.maxCodePoints() + 1L, limits.maxCodePoints());
    }
    if (message.contains("aliases")
        || message.contains("recursive")
        || message.contains("recursion")) {
      throw new ValidationLimitException(
          ALIAS_ISSUE,
          "maxAliasExpansionsPerNode",
          (long) limits.maxAliasExpansionsPerNode() + 1L,
          limits.maxAliasExpansionsPerNode());
    }
    if (isTabFailure(failure)) {
      ValidationIssue tab =
          issue(
              "validation.yaml-tab",
              "",
              problemMark(failure),
              input,
              Collections.<String>emptyList());
      if (!hasIssueAt(issues, tab)) {
        issues.add(tab);
      }
      return;
    }
    issues.add(
        issue(
            "validation.yaml-syntax",
            "",
            problemMark(failure),
            input,
            Collections.<String>emptyList()));
  }

  private static boolean isTabFailure(YAMLException failure) {
    String text = failureText(failure);
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("tab") || text.indexOf('\t') >= 0;
  }

  private static String failureText(YAMLException failure) {
    if (failure instanceof MarkedYAMLException) {
      MarkedYAMLException marked = (MarkedYAMLException) failure;
      String context = marked.getContext();
      String problem = marked.getProblem();
      return (context == null ? "" : context) + " " + (problem == null ? "" : problem);
    }
    return String.valueOf(failure.getMessage());
  }

  private static boolean hasIssueAt(List<ValidationIssue> issues, ValidationIssue candidate) {
    for (ValidationIssue existing : issues) {
      if (candidate.code().equals(existing.code())
          && candidate.line() == existing.line()
          && candidate.column() == existing.column()
          && candidate.offset() == existing.offset()) {
        return true;
      }
    }
    return false;
  }

  private static Mark problemMark(YAMLException failure) {
    if (failure instanceof MarkedYAMLException) {
      return ((MarkedYAMLException) failure).getProblemMark();
    }
    return null;
  }

  private static ValidationIssue issue(
      String code, String pointer, Mark mark, TextInput input, List<String> parameters) {
    return mark == null
        ? new ValidationIssue(Severity.ERROR, code, pointer, 0, 0, -1L, parameters)
        : issueAt(code, pointer, mark.getIndex(), input, parameters);
  }

  private static ValidationIssue issueAt(
      String code, String pointer, int charIndex, TextInput input, List<String> parameters) {
    int[] position = input.position(charIndex);
    return new ValidationIssue(
        Severity.ERROR,
        code,
        pointer,
        position[0],
        position[1],
        input.byteOffset(charIndex),
        parameters);
  }

  private static String keyText(Node node) {
    return node instanceof ScalarNode ? ((ScalarNode) node).getValue() : null;
  }

  private static boolean isStandardTag(Tag tag) {
    return tag != null && STANDARD_TAGS.contains(tag);
  }

  private static String tagText(Tag tag) {
    return tag == null ? "" : String.valueOf(tag.getValue());
  }

  private static String safeTag(String tag) {
    String value = tag == null ? "" : tag;
    int length = Math.min(value.length(), MAX_TAG_PARAMETER_LENGTH);
    StringBuilder result = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char current = value.charAt(i);
      result.append(current < 0x20 || current == 0x7f ? '?' : current);
    }
    return result.toString();
  }

  private static Set<Tag> standardTags() {
    Set<Tag> tags = new HashSet<Tag>();
    Collections.addAll(
        tags,
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
        Tag.MERGE);
    return Collections.unmodifiableSet(tags);
  }

  private static final class WalkState {
    private final Map<Node, Boolean> visited = new IdentityHashMap<Node, Boolean>();
    private final Map<Node, Boolean> stack = new IdentityHashMap<Node, Boolean>();
    private final Map<Node, Integer> references = new IdentityHashMap<Node, Integer>();
    private int visits;
  }
}
