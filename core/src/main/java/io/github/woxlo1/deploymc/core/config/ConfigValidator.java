package io.github.woxlo1.deploymc.core.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Validates parsed configuration against the normative schema with exact line/column findings. */
public final class ConfigValidator {

  private ConfigValidator() {}

  public static ConfigValidationResult validate(
      YamlValue root, ConfigSchema schema, Locale locale) {
    List<ConfigIssue> issues = new ArrayList<ConfigIssue>();
    validateValue(root, schema.root(), "", schema, locale, issues);
    return new ConfigValidationResult(issues);
  }

  @SuppressWarnings("unchecked")
  private static void validateValue(
      YamlValue node,
      Object schemaObject,
      String path,
      ConfigSchema schema,
      Locale locale,
      List<ConfigIssue> issues) {
    Object resolved;
    try {
      resolved = schema.resolve(schemaObject);
    } catch (IllegalStateException exception) {
      error(issues, node, path, locale, "config.error.schema", exception.getMessage());
      return;
    }
    if (!(resolved instanceof Map)) {
      return;
    }
    Map<String, Object> rules = (Map<String, Object>) resolved;

    checkType(node, rules.get("type"), path, locale, issues);
    checkEnums(node, rules.get("const"), rules.get("enum"), path, locale, issues);
    checkString(node, rules, path, locale, issues);
    checkNumber(node, rules, path, locale, issues);
    checkArray(node, rules, path, schema, locale, issues);
    checkObject(node, rules, path, schema, locale, issues);
    checkCombinators(node, rules, "allOf", true, false, path, schema, locale, issues);
    checkCombinators(node, rules, "anyOf", false, false, path, schema, locale, issues);
    checkCombinators(node, rules, "oneOf", false, true, path, schema, locale, issues);
    checkCondition(node, rules, path, schema, locale, issues);
    if (path.isEmpty()) {
      checkRoleReferences(node, locale, issues);
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkType(
      YamlValue node, Object type, String path, Locale locale, List<ConfigIssue> issues) {
    if (type == null) {
      return;
    }
    List<String> expected = new ArrayList<String>();
    if (type instanceof String) {
      expected.add((String) type);
    } else if (type instanceof List) {
      for (Object item : (List<Object>) type) {
        if (item instanceof String) {
          expected.add((String) item);
        }
      }
    }
    if (expected.isEmpty()) {
      return;
    }
    for (String candidate : expected) {
      if (matchesType(node, candidate)) {
        return;
      }
    }
    error(
        issues,
        node,
        path,
        locale,
        "config.error.type",
        String.join("/", expected),
        node.jsonType());
  }

  private static boolean matchesType(YamlValue node, String type) {
    if ("object".equals(type)) {
      return node.isObject();
    }
    if ("array".equals(type)) {
      return node.isArray();
    }
    if ("string".equals(type)) {
      return node.isString();
    }
    if ("integer".equals(type)) {
      return node.isInteger();
    }
    if ("number".equals(type)) {
      return node.isNumber();
    }
    if ("boolean".equals(type)) {
      return node.isBoolean();
    }
    if ("null".equals(type)) {
      return node.isNull();
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static void checkEnums(
      YamlValue node,
      Object constant,
      Object enumeration,
      String path,
      Locale locale,
      List<ConfigIssue> issues) {
    Object raw = node.raw();
    if (constant != null && !sameValue(constant, raw)) {
      error(issues, node, path, locale, "config.error.const", display(constant));
    }
    if (enumeration instanceof List) {
      for (Object candidate : (List<Object>) enumeration) {
        if (sameValue(candidate, raw)) {
          return;
        }
      }
      List<String> values = new ArrayList<String>();
      for (Object candidate : (List<Object>) enumeration) {
        values.add(display(candidate));
      }
      error(
          issues, node, path, locale, "config.error.enum", display(raw), String.join(", ", values));
    }
  }

  private static boolean sameValue(Object left, Object right) {
    if (left instanceof Number && right instanceof Number) {
      return BigDecimal.valueOf(((Number) left).doubleValue())
              .compareTo(BigDecimal.valueOf(((Number) right).doubleValue()))
          == 0;
    }
    return left == null ? right == null : left.equals(right);
  }

  private static String display(Object value) {
    return value == null ? "null" : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static void checkString(
      YamlValue node,
      Map<String, Object> rules,
      String path,
      Locale locale,
      List<ConfigIssue> issues) {
    if (!node.isString()) {
      return;
    }
    String value = (String) node.raw();
    Integer minLength = integer(rules.get("minLength"));
    Integer maxLength = integer(rules.get("maxLength"));
    if (minLength != null && value.length() < minLength) {
      error(issues, node, path, locale, "config.error.minLength", minLength);
    }
    if (maxLength != null && value.length() > maxLength) {
      error(issues, node, path, locale, "config.error.maxLength", maxLength);
    }
    Object pattern = rules.get("pattern");
    if (pattern instanceof String) {
      try {
        if (!Pattern.compile((String) pattern).matcher(value).find()) {
          error(issues, node, path, locale, "config.error.pattern", pattern);
        }
      } catch (PatternSyntaxException exception) {
        error(issues, node, path, locale, "config.error.schema", exception.getMessage());
      }
    }
  }

  private static void checkNumber(
      YamlValue node,
      Map<String, Object> rules,
      String path,
      Locale locale,
      List<ConfigIssue> issues) {
    if (!node.isNumber()) {
      return;
    }
    BigDecimal value = BigDecimal.valueOf(((Number) node.raw()).doubleValue());
    BigDecimal minimum = decimal(rules.get("minimum"));
    BigDecimal maximum = decimal(rules.get("maximum"));
    if (minimum != null && value.compareTo(minimum) < 0) {
      error(issues, node, path, locale, "config.error.minimum", displayminimum(minimum));
    }
    if (maximum != null && value.compareTo(maximum) > 0) {
      error(issues, node, path, locale, "config.error.maximum", displayminimum(maximum));
    }
  }

  private static String displayminimum(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  @SuppressWarnings("unchecked")
  private static void checkArray(
      YamlValue node,
      Map<String, Object> rules,
      String path,
      ConfigSchema schema,
      Locale locale,
      List<ConfigIssue> issues) {
    if (!node.isArray()) {
      return;
    }
    List<YamlValue> items = node.items();
    Integer minItems = integer(rules.get("minItems"));
    Integer maxItems = integer(rules.get("maxItems"));
    if (minItems != null && items.size() < minItems) {
      error(issues, node, path, locale, "config.error.minItems", minItems);
    }
    if (maxItems != null && items.size() > maxItems) {
      error(issues, node, path, locale, "config.error.maxItems", maxItems);
    }
    if (Boolean.TRUE.equals(rules.get("uniqueItems"))) {
      Set<Object> seen = new HashSet<Object>();
      for (YamlValue item : items) {
        if (!seen.add(item.raw())) {
          error(issues, item, path, locale, "config.error.uniqueItems");
        }
      }
    }
    Object itemSchema = rules.get("items");
    if (itemSchema != null) {
      for (int index = 0; index < items.size(); index++) {
        validateValue(
            items.get(index), itemSchema, path + "[" + index + "]", schema, locale, issues);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkObject(
      YamlValue node,
      Map<String, Object> rules,
      String path,
      ConfigSchema schema,
      Locale locale,
      List<ConfigIssue> issues) {
    if (!node.isObject()) {
      return;
    }
    Map<String, YamlValue> entries = node.entries();
    Integer minProperties = integer(rules.get("minProperties"));
    Integer maxProperties = integer(rules.get("maxProperties"));
    if (minProperties != null && entries.size() < minProperties) {
      error(issues, node, path, locale, "config.error.minProperties", minProperties);
    }
    if (maxProperties != null && entries.size() > maxProperties) {
      error(issues, node, path, locale, "config.error.maxProperties", maxProperties);
    }
    Object required = rules.get("required");
    if (required instanceof List) {
      for (Object key : (List<Object>) required) {
        if (key instanceof String && !entries.containsKey(key)) {
          String missingPath = path.isEmpty() ? (String) key : path + "." + key;
          error(issues, node, missingPath, locale, "config.error.required", key);
        }
      }
    }
    Map<String, Object> properties = map(rules.get("properties"));
    Map<String, Object> patterns = map(rules.get("patternProperties"));
    Object propertyNames = rules.get("propertyNames");
    Object additional = rules.get("additionalProperties");

    for (Map.Entry<String, YamlValue> entry : entries.entrySet()) {
      String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
      if (propertyNames != null) {
        validateValue(
            new YamlValue(entry.getKey(), entry.getValue().line(), entry.getValue().column()),
            propertyNames,
            childPath,
            schema,
            locale,
            issues);
      }
      Object childSchema = properties.get(entry.getKey());
      boolean matched = childSchema != null;
      if (childSchema != null) {
        validateValue(entry.getValue(), childSchema, childPath, schema, locale, issues);
      }
      for (Map.Entry<String, Object> pattern : patterns.entrySet()) {
        try {
          if (Pattern.compile(pattern.getKey()).matcher(entry.getKey()).find()) {
            matched = true;
            validateValue(entry.getValue(), pattern.getValue(), childPath, schema, locale, issues);
          }
        } catch (PatternSyntaxException exception) {
          error(
              issues,
              entry.getValue(),
              childPath,
              locale,
              "config.error.schema",
              exception.getMessage());
        }
      }
      if (!matched) {
        if (Boolean.FALSE.equals(additional)) {
          error(issues, entry.getValue(), childPath, locale, "config.error.unknownKey", childPath);
        } else if (additional instanceof Map) {
          validateValue(entry.getValue(), additional, childPath, schema, locale, issues);
        } else {
          warn(issues, entry.getValue(), childPath, locale, "config.warning.unknownKey", childPath);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkCombinators(
      YamlValue node,
      Map<String, Object> rules,
      String keyword,
      boolean all,
      boolean exactlyOne,
      String path,
      ConfigSchema schema,
      Locale locale,
      List<ConfigIssue> issues) {
    Object value = rules.get(keyword);
    if (!(value instanceof List)) {
      return;
    }
    int matched = 0;
    for (Object branch : (List<Object>) value) {
      List<ConfigIssue> branchIssues = new ArrayList<ConfigIssue>();
      validateValue(node, branch, path, schema, locale, branchIssues);
      boolean valid = !hasError(branchIssues);
      if (valid) {
        matched++;
      } else if (all) {
        addErrors(branchIssues, issues);
      }
    }
    if ((all && matched != ((List<?>) value).size())
        || (!all && !exactlyOne && matched == 0)
        || (exactlyOne && matched != 1)) {
      error(issues, node, path, locale, "config.error.schema", keyword);
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkCondition(
      YamlValue node,
      Map<String, Object> rules,
      String path,
      ConfigSchema schema,
      Locale locale,
      List<ConfigIssue> issues) {
    Object condition = rules.get("if");
    if (condition == null) {
      return;
    }
    List<ConfigIssue> probe = new ArrayList<ConfigIssue>();
    validateValue(node, condition, path, schema, locale, probe);
    boolean conditionMet = !hasError(probe);
    Object branch = conditionMet ? rules.get("then") : rules.get("else");
    if (branch != null) {
      List<ConfigIssue> branchIssues = new ArrayList<ConfigIssue>();
      validateValue(node, branch, path, schema, locale, branchIssues);
      addErrors(branchIssues, issues);
    }
  }

  /** Application-level integrity checks that JSON Schema cannot express without data lookup. */
  private static void checkRoleReferences(YamlValue root, Locale locale, List<ConfigIssue> issues) {
    if (!root.isObject()) {
      return;
    }
    Set<String> knownRoles = new HashSet<String>();
    YamlValue roles = root.get("roles");
    if (roles != null && roles.isObject()) {
      knownRoles.addAll(roles.keys());
    }

    YamlValue defaultRole = root.get("default-role");
    if (defaultRole != null
        && defaultRole.isString()
        && !"none".equals(defaultRole.raw())
        && !knownRoles.contains(defaultRole.raw())) {
      error(
          issues,
          defaultRole,
          "default-role",
          locale,
          "config.error.unknownRole",
          defaultRole.raw());
    }

    YamlValue users = root.get("users");
    if (users != null && users.isObject()) {
      for (Map.Entry<String, YamlValue> user : users.entries().entrySet()) {
        YamlValue assigned = user.getValue().get("roles");
        if (assigned == null || !assigned.isArray()) {
          continue;
        }
        for (int index = 0; index < assigned.items().size(); index++) {
          YamlValue role = assigned.items().get(index);
          if (role.isString() && !knownRoles.contains(role.raw())) {
            error(
                issues,
                role,
                "users." + user.getKey() + ".roles[" + index + "]",
                locale,
                "config.error.unknownRole",
                role.raw());
          }
        }
      }
    }

    YamlValue deploy = root.get("deploy");
    YamlValue approval = deploy == null ? null : deploy.get("approval");
    YamlValue approvers = approval == null ? null : approval.get("approver-roles");
    if (approvers != null && approvers.isArray()) {
      for (int index = 0; index < approvers.items().size(); index++) {
        YamlValue role = approvers.items().get(index);
        if (role.isString() && !knownRoles.contains(role.raw())) {
          error(
              issues,
              role,
              "deploy.approval.approver-roles[" + index + "]",
              locale,
              "config.error.unknownRole",
              role.raw());
        }
      }
    }
  }

  private static boolean hasError(List<ConfigIssue> issues) {
    for (ConfigIssue issue : issues) {
      if (issue.isError()) {
        return true;
      }
    }
    return false;
  }

  private static void addErrors(List<ConfigIssue> source, List<ConfigIssue> target) {
    for (ConfigIssue issue : source) {
      if (issue.isError()) {
        target.add(issue);
      }
    }
  }

  private static Integer integer(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  private static BigDecimal decimal(Object value) {
    if (value instanceof Number) {
      return BigDecimal.valueOf(((Number) value).doubleValue());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
  }

  private static void error(
      List<ConfigIssue> issues,
      YamlValue node,
      String path,
      Locale locale,
      String key,
      Object... parameters) {
    issues.add(
        new ConfigIssue(
            ConfigIssue.Severity.ERROR, path, node.line(), node.column(), key, parameters));
  }

  private static void warn(
      List<ConfigIssue> issues,
      YamlValue node,
      String path,
      Locale locale,
      String key,
      Object... parameters) {
    issues.add(
        new ConfigIssue(
            ConfigIssue.Severity.WARNING, path, node.line(), node.column(), key, parameters));
  }
}
