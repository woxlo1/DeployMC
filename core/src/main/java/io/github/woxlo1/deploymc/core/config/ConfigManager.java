package io.github.woxlo1.deploymc.core.config;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads, migrates, validates, and reloads the host-controlled configuration.
 *
 * <p>Invalid input is never partially applied: startup fails and a reload keeps the last known good
 * view. Only local host code can construct this class; API modules receive {@link ConfigView}.
 */
public final class ConfigManager {

  private static final Pattern ARRAY_INDEX = Pattern.compile("\\[\\d+\\]");

  private final Path configFile;
  private final ConfigSchema schema;
  private final ConfigMigrator migrator;
  private final Clock clock;
  private final Locale locale;

  private volatile ConfigView current;
  private volatile ConfigValidationResult lastValidation;
  private volatile ReloadReport lastReloadReport;
  private volatile Path lastBackup;

  public ConfigManager(Path configFile) throws ConfigException {
    this(
        configFile,
        ConfigSchema.load(),
        Collections.<ConfigMigration>emptyList(),
        Clock.systemUTC(),
        Locale.ROOT);
  }

  public ConfigManager(
      Path configFile,
      ConfigSchema schema,
      List<ConfigMigration> migrations,
      Clock clock,
      Locale locale) {
    this.configFile = Objects.requireNonNull(configFile, "configFile");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.migrator = new ConfigMigrator(schema, migrations);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.locale = locale == null ? Locale.ROOT : locale;
  }

  /** Loads the file, generating a safe default first if the file does not exist. */
  public ConfigView load() throws ConfigException {
    DefaultConfigWriter.writeIfMissing(configFile);
    Candidate candidate = prepare(true);
    current = new ConfigView(candidate.values);
    lastValidation = candidate.validation;
    lastReloadReport = null;
    return current;
  }

  /** Revalidates the file and atomically swaps the view only when it is valid. */
  public ReloadReport reload() throws ConfigException {
    if (!Files.exists(configFile)) {
      throw new ConfigException(
          ConfigMessages.format(locale, "config.error.read", configFile.toString()));
    }
    Candidate candidate = prepare(false);
    ConfigView previous = current;
    List<String> changed =
        previous == null
            ? Collections.<String>emptyList()
            : new ArrayList<String>(ConfigDiff.changedPaths(previous.raw(), candidate.values));
    List<String> restartRequired = new ArrayList<String>();
    for (String path : changed) {
      String propertyPath = ARRAY_INDEX.matcher(path).replaceAll("");
      if (!Boolean.TRUE.equals(schema.hotReloadable(propertyPath))) {
        restartRequired.add(path);
      }
    }
    ReloadReport report =
        new ReloadReport(changed, restartRequired, candidate.validation.warnings());
    current = new ConfigView(candidate.values);
    lastValidation = candidate.validation;
    lastReloadReport = report;
    return report;
  }

  public Optional<ConfigView> current() {
    return Optional.ofNullable(current);
  }

  public Optional<ConfigValidationResult> lastValidation() {
    return Optional.ofNullable(lastValidation);
  }

  public Optional<ReloadReport> lastReloadReport() {
    return Optional.ofNullable(lastReloadReport);
  }

  public Optional<Path> lastBackup() {
    return Optional.ofNullable(lastBackup);
  }

  public Path configFile() {
    return configFile;
  }

  private Candidate prepare(boolean allowMigration) throws ConfigException {
    String text = ConfigFiles.readUtf8(configFile);
    YamlValue root = requireObject(ConfigYamlParser.parse(text));
    int version = migrator.version(root);
    if (version > schema.supportedVersion()
        || (version < schema.supportedVersion() && !allowMigration)) {
      throw new ConfigException(
          ConfigMessages.format(
              locale, "config.error.version", version, schema.supportedVersion()));
    }
    if (version < schema.supportedVersion()) {
      Path backup = ConfigFiles.backup(configFile, text, clock);
      lastBackup = backup;
      YamlValue migrated = migrator.migrate(root);
      validate(migrated);
      String rendered = render(castValues(migrated.raw()));
      ConfigFiles.writeUtf8(configFile, rendered, true);
      text = rendered;
      root = requireObject(ConfigYamlParser.parse(text));
    }
    return validate(root);
  }

  private YamlValue requireObject(YamlValue root) throws ConfigException {
    if (!root.isObject()) {
      throw new ConfigException(ConfigMessages.format(locale, "config.error.root"));
    }
    return root;
  }

  private Candidate validate(YamlValue root) throws ConfigException {
    ConfigValidationResult validation = ConfigValidator.validate(root, schema, locale);
    if (!validation.isValid()) {
      throw invalid(validation.errors());
    }
    return new Candidate(castValues(root.raw()), validation);
  }

  private ConfigException invalid(List<ConfigIssue> errors) {
    StringBuilder details = new StringBuilder();
    for (ConfigIssue issue : errors) {
      if (details.length() > 0) {
        details.append("; ");
      }
      details
          .append(issue.path().isEmpty() ? "<root>" : issue.path())
          .append(" (")
          .append(issue.line())
          .append(':')
          .append(issue.column())
          .append("): ")
          .append(issue.format(locale));
    }
    return new ConfigException(
        ConfigMessages.format(locale, "config.error.invalid", details.toString()));
  }

  private static String render(Map<String, Object> values) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setIndent(2);
    options.setExplicitStart(false);
    StringWriter writer = new StringWriter();
    new Yaml(options).dump(values, writer);
    return writer.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castValues(Object raw) {
    return (Map<String, Object>) raw;
  }

  private static final class Candidate {
    private final Map<String, Object> values;
    private final ConfigValidationResult validation;

    private Candidate(Map<String, Object> values, ConfigValidationResult validation) {
      this.values = values;
      this.validation = validation;
    }
  }
}
