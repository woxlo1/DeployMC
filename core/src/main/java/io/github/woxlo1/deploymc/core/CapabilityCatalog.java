package io.github.woxlo1.deploymc.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable catalog of the capability keys that platform adapters may report.
 *
 * <p>The keys are stable wire values. Adding, renaming, or removing a key is a public API change
 * because the {@code /server} response exposes the states. Every entry must describe a safe
 * fallback; an unavailable or unknown capability must never be treated as available.
 */
public final class CapabilityCatalog {

  private static final Map<String, CapabilityDefinition> DEFINITIONS = build();

  private static Map<String, CapabilityDefinition> build() {
    Map<String, CapabilityDefinition> definitions =
        new LinkedHashMap<String, CapabilityDefinition>();
    add(
        definitions,
        CapabilityKeys.LOG_CAPTURE,
        "Attach to the server log stream.",
        "Console endpoints report CAPABILITY_UNAVAILABLE and the UI hides the live console.");
    add(
        definitions,
        CapabilityKeys.LOG_CAPTURE_STRUCTURED,
        "Provide level, logger, thread, and timestamp fields for each log line.",
        "Parse only the fields the platform provides; leave unknown fields unknown.");
    add(
        definitions,
        CapabilityKeys.COMMAND_DISPATCH,
        "Dispatch a console command on the platform's command thread.",
        "Reject command requests; never start a local process as a substitute.");
    add(
        definitions,
        CapabilityKeys.COMMAND_OUTPUT,
        "Capture the output produced by a dispatched command.",
        "Report only that the command was accepted, not what it printed.");
    add(
        definitions,
        CapabilityKeys.SERVER_SAVE,
        "Trigger a world save before a controlled restart.",
        "Deployment reports the missing step and does not claim a safe restart.");
    add(
        definitions,
        CapabilityKeys.SERVER_SHUTDOWN,
        "Request a server shutdown through the platform.",
        "Use restart mode none and ask the operator to restart manually.");
    add(
        definitions,
        CapabilityKeys.SERVER_RESTART_SCRIPT,
        "An external restart mechanism is known to be configured.",
        "Treat restart as unsupported; never imply that a restart will happen.");
    add(
        definitions,
        CapabilityKeys.PLUGINS_ENUMERATE,
        "List loaded plugins with name, version, main class, and enabled state.",
        "Report the plugin list as unavailable; static JAR inspection still works.");
    add(
        definitions,
        CapabilityKeys.PLUGINS_ENABLE_STATE,
        "Report whether each loaded plugin is enabled.",
        "Report the state as unknown; do not infer success from absence of errors.");
    add(
        definitions,
        CapabilityKeys.PLUGINS_LOAD_ERRORS,
        "Detect plugin load failures after a deployment.",
        "Report which verification step could not run; do not claim a clean load.");
    add(
        definitions,
        CapabilityKeys.UPDATE_FOLDER,
        "Move replacement JARs through the platform's safe update-folder mechanism.",
        "Use a restart-time swap; never overwrite a loaded JAR on Windows.");
    add(
        definitions,
        CapabilityKeys.SCHEDULER_REGION,
        "Schedule work on a Folia-style region scheduler.",
        "Use the global scheduler and do not claim Folia region support.");
    add(
        definitions,
        CapabilityKeys.PLAYERS_LOCALE,
        "Report a per-player locale for localized messages.",
        "Use the configured server default locale.");
    add(
        definitions,
        CapabilityKeys.PLAYERS_BROADCAST,
        "Broadcast a localized message to online players.",
        "Send to the console only and report the limitation to the caller.");
    return Collections.unmodifiableMap(definitions);
  }

  private static void add(
      Map<String, CapabilityDefinition> definitions,
      String key,
      String description,
      String fallback) {
    CapabilityDefinition definition = new CapabilityDefinition(key, description, fallback);
    if (definitions.put(key, definition) != null) {
      throw new IllegalStateException("duplicate capability key: " + key);
    }
  }

  /** Returns all definitions in stable insertion order. */
  public static Collection<CapabilityDefinition> all() {
    return DEFINITIONS.values();
  }

  /** Returns all stable keys in insertion order. */
  public static Set<String> keys() {
    return DEFINITIONS.keySet();
  }

  /** Returns the definition for a key, or {@code null} when the key is not cataloged. */
  public static CapabilityDefinition get(String key) {
    return DEFINITIONS.get(Objects.requireNonNull(key, "key"));
  }

  private CapabilityCatalog() {}
}
