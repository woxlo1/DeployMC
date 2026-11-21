package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable registry that selects one validator by exact path, path pattern, or extension. */
public final class ValidatorRegistry implements ValidationGate {

  private static final Comparator<PatternEntry> PATTERN_ORDER =
      new Comparator<PatternEntry>() {
        @Override
        public int compare(PatternEntry left, PatternEntry right) {
          int result = Integer.compare(right.pattern.literalCount(), left.pattern.literalCount());
          if (result != 0) {
            return result;
          }
          return Integer.compare(left.order, right.order);
        }
      };

  private static final Comparator<ExtensionEntry> EXTENSION_ORDER =
      new Comparator<ExtensionEntry>() {
        @Override
        public int compare(ExtensionEntry left, ExtensionEntry right) {
          int result = Integer.compare(right.extension.length(), left.extension.length());
          if (result != 0) {
            return result;
          }
          return Integer.compare(left.order, right.order);
        }
      };

  private final ValidationLimits limits;
  private final List<Rule> rules;
  private final Map<String, Rule> exactRules;
  private final List<PatternEntry> patternEntries;
  private final Map<String, List<ExtensionEntry>> extensionEntries;

  private ValidatorRegistry(ValidationLimits limits, List<Rule> rules) {
    this.limits = limits;
    this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
    Map<String, Rule> exact = new LinkedHashMap<String, Rule>();
    List<PatternEntry> patterns = new ArrayList<PatternEntry>();
    Map<String, List<ExtensionEntry>> extensions =
        new LinkedHashMap<String, List<ExtensionEntry>>();
    for (Rule rule : rules) {
      for (String path : rule.exactPaths) {
        exact.put(path, rule);
      }
      for (GlobMatcher pattern : rule.patterns) {
        patterns.add(new PatternEntry(pattern, rule, rule.order));
      }
      for (String extension : rule.extensions) {
        String normalized = normalizeExtension(extension);
        List<ExtensionEntry> entries = extensions.get(normalized);
        if (entries == null) {
          entries = new ArrayList<ExtensionEntry>();
          extensions.put(normalized, entries);
        }
        entries.add(new ExtensionEntry(normalized, rule, rule.order));
      }
    }
    Collections.sort(patterns, PATTERN_ORDER);
    for (List<ExtensionEntry> entries : extensions.values()) {
      Collections.sort(entries, EXTENSION_ORDER);
    }
    this.exactRules = Collections.unmodifiableMap(exact);
    this.patternEntries = Collections.unmodifiableList(patterns);
    this.extensionEntries = Collections.unmodifiableMap(extensions);
  }

  public static ValidatorRegistry withBuiltIns() {
    return withBuiltIns(ValidationLimits.defaults());
  }

  public static ValidatorRegistry withBuiltIns(ValidationLimits limits) {
    return empty(limits)
        .register(
            "yaml",
            Collections.<String>emptySet(),
            Collections.<String>emptyList(),
            extensions(".yml", ".yaml"),
            new YamlValidator())
        .register(
            "json",
            Collections.<String>emptySet(),
            Collections.<String>emptyList(),
            extensions(".json"),
            new JsonValidator())
        .register(
            "toml",
            Collections.<String>emptySet(),
            Collections.<String>emptyList(),
            extensions(".toml"),
            new TomlValidator())
        .register(
            "properties",
            Collections.<String>emptySet(),
            Collections.<String>emptyList(),
            extensions(".properties"),
            new PropertiesValidator());
  }

  public static ValidatorRegistry empty() {
    return empty(ValidationLimits.defaults());
  }

  public static ValidatorRegistry empty(ValidationLimits limits) {
    return new ValidatorRegistry(limits, Collections.<Rule>emptyList());
  }

  @Override
  public ValidationLimits limits() {
    return limits;
  }

  /** Returns a new registry with updated host limits; the receiver is unchanged. */
  public ValidatorRegistry withLimits(ValidationLimits replacement) {
    return new ValidatorRegistry(Objects.requireNonNull(replacement, "limits"), rules);
  }

  /** Registers a rule. At least one exact path, pattern, or extension is required. */
  public ValidatorRegistry register(
      String id,
      Set<String> exactPaths,
      List<String> patterns,
      Set<String> extensions,
      ContentValidator validator) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(validator, "validator");
    if (id.trim().isEmpty()) {
      throw new IllegalArgumentException("validator id must not be blank");
    }
    Set<String> exactCopy = new LinkedHashSet<String>();
    if (exactPaths != null) {
      for (String path : exactPaths) {
        exactCopy.add(PathRules.normalize(path));
      }
    }
    List<GlobMatcher> patternCopy = new ArrayList<GlobMatcher>();
    if (patterns != null) {
      for (String pattern : patterns) {
        GlobMatcher matcher = new GlobMatcher(pattern);
        if (matcher.literalCount() == 0) {
          throw new IllegalArgumentException("path pattern needs at least one literal character");
        }
        patternCopy.add(matcher);
      }
    }
    Set<String> extensionCopy = new LinkedHashSet<String>();
    if (extensions != null) {
      for (String extension : extensions) {
        extensionCopy.add(normalizeExtension(extension));
      }
    }
    if (exactCopy.isEmpty() && patternCopy.isEmpty() && extensionCopy.isEmpty()) {
      throw new IllegalArgumentException("a validator rule needs at least one match condition");
    }
    List<Rule> updated = new ArrayList<Rule>(rules);
    updated.add(new Rule(id, exactCopy, patternCopy, extensionCopy, validator, updated.size()));
    return new ValidatorRegistry(limits, updated);
  }

  @Override
  public ValidationResult validate(String relativePath, byte[] content) throws ValidationException {
    String normalized = PathRules.normalize(relativePath);
    Objects.requireNonNull(content, "content");
    Rule rule = match(normalized);
    if (rule == null) {
      return ValidationResult.unmatched(normalized);
    }
    try {
      return rule.validator.validate(normalized, content, limits);
    } catch (ValidationLimitException limitFailure) {
      throw limitFailure;
    } catch (ValidationException failure) {
      throw failure;
    } catch (RuntimeException unexpected) {
      throw new ValidatorUnavailableException(rule.id, unexpected);
    }
  }

  private Rule match(String path) {
    Rule exact = exactRules.get(path);
    if (exact != null) {
      return exact;
    }
    for (PatternEntry entry : patternEntries) {
      if (entry.pattern.matches(path)) {
        return entry.rule;
      }
    }
    int dot = path.lastIndexOf('.');
    if (dot < 0) {
      return null;
    }
    String extension = normalizeExtension(path.substring(dot));
    List<ExtensionEntry> entries = extensionEntries.get(extension);
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    return entries.get(0).rule;
  }

  private static Set<String> extensions(String... values) {
    Set<String> result = new LinkedHashSet<String>();
    Collections.addAll(result, values);
    return result;
  }

  private static String normalizeExtension(String extension) {
    if (extension == null || extension.isEmpty()) {
      throw new IllegalArgumentException("extension must not be empty");
    }
    String normalized = extension.startsWith(".") ? extension : "." + extension;
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static final class Rule {
    private final String id;
    private final Set<String> exactPaths;
    private final List<GlobMatcher> patterns;
    private final Set<String> extensions;
    private final ContentValidator validator;
    private final int order;

    private Rule(
        String id,
        Set<String> exactPaths,
        List<GlobMatcher> patterns,
        Set<String> extensions,
        ContentValidator validator,
        int order) {
      this.id = id;
      this.exactPaths = Collections.unmodifiableSet(new LinkedHashSet<String>(exactPaths));
      this.patterns = Collections.unmodifiableList(new ArrayList<GlobMatcher>(patterns));
      this.extensions = Collections.unmodifiableSet(new LinkedHashSet<String>(extensions));
      this.validator = validator;
      this.order = order;
    }
  }

  private static final class PatternEntry {
    private final GlobMatcher pattern;
    private final Rule rule;
    private final int order;

    private PatternEntry(GlobMatcher pattern, Rule rule, int order) {
      this.pattern = pattern;
      this.rule = rule;
      this.order = order;
    }
  }

  private static final class ExtensionEntry {
    private final String extension;
    private final Rule rule;
    private final int order;

    private ExtensionEntry(String extension, Rule rule, int order) {
      this.extension = extension;
      this.rule = rule;
      this.order = order;
    }
  }
}
