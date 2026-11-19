package io.github.woxlo1.deploymc.core.config;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Parses host-controlled YAML into values that keep their exact source locations. */
public final class ConfigYamlParser {

  private static final int MAX_NESTING_DEPTH = 32;
  private static final int MAX_CODE_POINTS = 1_000_000;

  private ConfigYamlParser() {}

  public static YamlValue parse(String source) throws ConfigException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setAllowRecursiveKeys(false);
    options.setMaxAliasesForCollections(0);
    options.setNestingDepthLimit(MAX_NESTING_DEPTH);
    options.setCodePointLimit(MAX_CODE_POINTS);
    options.setTagInspector(tag -> false);

    try {
      Yaml yaml = new Yaml(new SafeConstructor(options));
      Node root = yaml.compose(new StringReader(source));
      if (root == null) {
        return new YamlValue(null, 1, 1);
      }
      return convert(root, Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()));
    } catch (RuntimeException exception) {
      throw new ConfigException(
          ConfigMessages.format(
              Locale.ROOT, "config.error.parse", String.valueOf(exception.getMessage())),
          exception);
    }
  }

  private static YamlValue convert(Node node, Set<Node> visited) throws ConfigException {
    if (!visited.add(node)) {
      throw new ConfigException(
          ConfigMessages.format(Locale.ROOT, "config.error.parse", "aliases are not allowed"));
    }
    int line = node.getStartMark().getLine() + 1;
    int column = node.getStartMark().getColumn() + 1;
    if (node instanceof ScalarNode) {
      return new YamlValue(scalar((ScalarNode) node), line, column);
    }
    if (node instanceof MappingNode) {
      Map<String, YamlValue> values = new LinkedHashMap<String, YamlValue>();
      for (NodeTuple tuple : ((MappingNode) node).getValue()) {
        if (!(tuple.getKeyNode() instanceof ScalarNode)) {
          throw new ConfigException(ConfigMessages.format(Locale.ROOT, "config.error.root"));
        }
        String key = scalar((ScalarNode) tuple.getKeyNode()).toString();
        if (values.containsKey(key)) {
          throw new ConfigException(
              ConfigMessages.format(Locale.ROOT, "config.error.parse", "duplicate key " + key));
        }
        values.put(key, convert(tuple.getValueNode(), visited));
      }
      return new YamlValue(values, line, column);
    }
    if (node instanceof SequenceNode) {
      List<YamlValue> values = new ArrayList<YamlValue>();
      for (Node item : ((SequenceNode) node).getValue()) {
        values.add(convert(item, visited));
      }
      return new YamlValue(values, line, column);
    }
    throw new ConfigException(
        ConfigMessages.format(Locale.ROOT, "config.error.parse", node.getTag()));
  }

  private static Object scalar(ScalarNode node) throws ConfigException {
    Tag tag = node.getTag();
    String text = node.getValue();
    if (Tag.STR.equals(tag)) {
      return text;
    }
    if (Tag.NULL.equals(tag)) {
      return null;
    }
    if (Tag.BOOL.equals(tag)) {
      String normalized = text.toLowerCase(Locale.ROOT);
      if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
        return Boolean.TRUE;
      }
      if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
        return Boolean.FALSE;
      }
      throw invalidScalar(tag, text);
    }
    if (Tag.INT.equals(tag)) {
      try {
        return Long.valueOf(text.replace("_", ""));
      } catch (NumberFormatException exception) {
        throw invalidScalar(tag, text);
      }
    }
    if (Tag.FLOAT.equals(tag)) {
      try {
        return Double.valueOf(text.replace("_", ""));
      } catch (NumberFormatException exception) {
        throw invalidScalar(tag, text);
      }
    }
    throw invalidScalar(tag, text);
  }

  private static ConfigException invalidScalar(Tag tag, String text) {
    return new ConfigException(
        ConfigMessages.format(Locale.ROOT, "config.error.parse", tag + " " + text));
  }
}
