package io.github.woxlo1.deploymc.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PART 33.5 specification contract for the deployment state machine and startup recovery. No
 * deployment engine exists in this repository. These tests parse the normative transition data and
 * check the documented invariants; they claim neither implemented behavior nor platform support.
 */
class DeploymentRecoveryContractTest {

  private static final String NO_PRODUCT_IMPLEMENTATION =
      "No product deployment engine exists; this is a SPEC/FIXTURE CONTRACT test only";

  private static final List<String> EXPECTED_STATES =
      Arrays.asList(
          "CREATED",
          "UPLOADING",
          "VALIDATING",
          "REJECTED",
          "STAGED",
          "AWAITING_APPROVAL",
          "APPROVED",
          "SNAPSHOTTING",
          "APPLYING",
          "RESTART_PENDING",
          "RESTARTING",
          "VERIFYING",
          "SUCCEEDED",
          "FAILED",
          "ROLLING_BACK",
          "ROLLED_BACK",
          "ROLLBACK_FAILED",
          "CANCELLED",
          "SAFE_STATE");

  private static final List<String> EXPECTED_RECOVERY_STATES =
      Arrays.asList(
          "UPLOADING",
          "VALIDATING",
          "STAGED",
          "AWAITING_APPROVAL",
          "APPROVED",
          "SNAPSHOTTING",
          "APPLYING",
          "RESTART_PENDING",
          "RESTARTING",
          "VERIFYING",
          "ROLLING_BACK",
          "ROLLBACK_FAILED");

  private static final Set<String> TERMINAL_STATES =
      new HashSet<String>(
          Arrays.asList("REJECTED", "SUCCEEDED", "ROLLED_BACK", "CANCELLED", "SAFE_STATE"));

  private static final Set<String> CANCELLABLE_STATES =
      new HashSet<String>(
          Arrays.asList("CREATED", "UPLOADING", "VALIDATING", "STAGED", "AWAITING_APPROVAL"));

  @Test
  void normativeDataPinsStatesAndTargetWriteRule() {
    Data data = normativeData();
    assertEquals("1", data.header.get("version"));
    assertEquals("CREATED", data.header.get("initial"));
    assertEquals(19, data.states.size());
    assertEquals(EXPECTED_STATES, new ArrayList<String>(data.states.keySet()));

    Set<String> terminal = new HashSet<String>();
    Set<String> writers = new HashSet<String>();
    for (Map.Entry<String, Map<String, String>> entry : data.states.entrySet()) {
      Map<String, String> state = entry.getValue();
      assertNotNull(state.get("class"), entry.getKey());
      assertNotNull(state.get("terminal"), entry.getKey());
      String writes = state.get("target-writes");
      assertNotNull(writes, entry.getKey());
      assertTrue(
          "yes".equals(writes) || "no".equals(writes) || "unknown".equals(writes), entry.getKey());
      if ("true".equals(state.get("terminal"))) {
        terminal.add(entry.getKey());
      }
      if ("yes".equals(writes)) {
        writers.add(entry.getKey());
      }
    }
    assertEquals(TERMINAL_STATES, terminal);
    assertEquals(new HashSet<String>(Arrays.asList("APPLYING", "ROLLING_BACK")), writers);
    assertEquals("unknown", data.states.get("SAFE_STATE").get("target-writes"));
    assertEquals("initial", data.states.get("CREATED").get("class"));
    assertEquals("across-process", data.states.get("RESTARTING").get("class"));
  }

  @Test
  void normativeDataPinsTransitionsAndRecoveryActions() {
    Data data = normativeData();
    assertEquals(29, data.transitions.size());
    Set<String> triples = new HashSet<String>();
    for (Map<String, String> transition : data.transitions) {
      for (String key : Arrays.asList("from", "to", "trigger", "actor", "guard", "emits")) {
        assertNotNull(transition.get(key), key);
      }
      assertEquals("deployment.state", transition.get("emits"));
      triples.add(triple(transition));
    }
    assertEquals(29, triples.size(), "Transition triples must be unique");

    assertTransition(
        data, "CREATED", "UPLOADING", "start-upload", "client", "idempotency-key-present");
    assertTransition(
        data, "CREATED", "CANCELLED", "requester-cancel", "client", "requester-or-admin");
    assertTransition(
        data, "VALIDATING", "STAGED", "validation-passed", "system", "validation-report-persisted");
    assertTransition(
        data,
        "VALIDATING",
        "REJECTED",
        "validation-failed",
        "system",
        "validation-report-persisted");
    assertTransition(
        data,
        "AWAITING_APPROVAL",
        "APPROVED",
        "approve",
        "approver",
        "approver-authorized-and-not-requester");
    assertTransition(
        data,
        "SNAPSHOTTING",
        "APPLYING",
        "snapshots-committed",
        "system",
        "every-target-snapshot-recorded");
    assertTransition(
        data,
        "APPLYING",
        "RESTART_PENDING",
        "manifest-applied-restart-needed",
        "system",
        "every-operation-verified");
    assertTransition(
        data,
        "APPLYING",
        "VERIFYING",
        "manifest-applied-no-restart",
        "system",
        "every-operation-verified");
    assertTransition(
        data,
        "RESTARTING",
        "VERIFYING",
        "startup-recovery",
        "system",
        "new-process-observes-persisted-restarting");
    assertTransition(
        data, "VERIFYING", "SUCCEEDED", "checks-passed", "system", "every-required-check-passed");
    assertTransition(
        data, "VERIFYING", "FAILED", "check-failed", "system", "check-failed-or-timeout");
    assertTransition(
        data, "FAILED", "ROLLING_BACK", "target-modified", "system", "target-writes-occurred");
    assertTransition(
        data,
        "ROLLBACK_FAILED",
        "SAFE_STATE",
        "safe-state-entered",
        "system",
        "attempt-limit-reached-or-unrecoverable");

    assertEquals(12, data.recovery.size());
    assertEquals(EXPECTED_RECOVERY_STATES, new ArrayList<String>(data.recovery.keySet()));
    assertEquals("keep-record-clean-stale-staging-after-grace", data.recovery.get("UPLOADING"));
    assertEquals("keep-record-clean-stale-staging-after-grace", data.recovery.get("VALIDATING"));
    assertEquals("keep-record", data.recovery.get("STAGED"));
    assertEquals("keep-record", data.recovery.get("AWAITING_APPROVAL"));
    assertEquals("reverify-snapshots-or-fail", data.recovery.get("APPROVED"));
    assertEquals("reverify-snapshots-or-fail", data.recovery.get("SNAPSHOTTING"));
    assertEquals(
        "verify-each-operation-resume-forward-only-if-idempotent-and-matching-else-fail",
        data.recovery.get("APPLYING"));
    assertEquals("resume-countdown-if-alive-else-verify", data.recovery.get("RESTART_PENDING"));
    assertEquals("map-to-verifying", data.recovery.get("RESTARTING"));
    assertEquals("rerun-checks-with-remaining-timeout", data.recovery.get("VERIFYING"));
    assertEquals("resume-from-recorded-progress", data.recovery.get("ROLLING_BACK"));
    assertEquals("enter-safe-state", data.recovery.get("ROLLBACK_FAILED"));
  }

  @Test
  void invariantsHoldForNormativeTransitionGraph() {
    Data data = normativeData();
    assertTrue(predecessors(data, "CREATED").isEmpty(), "Initial state has no incoming transition");
    assertEquals(new HashSet<String>(data.states.keySet()), reachable(data, "CREATED"));

    for (Map.Entry<String, Map<String, String>> entry : data.states.entrySet()) {
      String name = entry.getKey();
      Set<String> successors = successors(data, name);
      if ("true".equals(entry.getValue().get("terminal"))) {
        assertTrue(successors.isEmpty(), name + " is terminal");
        assertFalse(data.recovery.containsKey(name), name + " is terminal");
      } else {
        assertFalse(successors.isEmpty() && !data.recovery.containsKey(name), name + " is stuck");
      }
    }

    Set<String> afterApply = reachable(data, "APPLYING");
    assertTrue(afterApply.contains("SUCCEEDED"));
    assertTrue(afterApply.contains("ROLLED_BACK"));
    assertTrue(afterApply.contains("SAFE_STATE"));
    assertEquals(new HashSet<String>(Arrays.asList("VERIFYING")), successors(data, "RESTARTING"));
    assertEquals(
        new HashSet<String>(Arrays.asList("SAFE_STATE")), successors(data, "ROLLBACK_FAILED"));
    assertEquals(new HashSet<String>(Arrays.asList("ROLLING_BACK")), successors(data, "FAILED"));
    assertEquals(
        new HashSet<String>(Arrays.asList("SNAPSHOTTING")), predecessors(data, "APPLYING"));
    assertEquals(new HashSet<String>(Arrays.asList("FAILED")), predecessors(data, "ROLLING_BACK"));

    Set<String> cancellations = new HashSet<String>();
    for (Map<String, String> transition : data.transitions) {
      if ("CANCELLED".equals(transition.get("to"))) {
        cancellations.add(transition.get("from"));
      }
      if (!CANCELLABLE_STATES.contains(transition.get("from"))) {
        assertFalse(
            "CANCELLED".equals(transition.get("to")),
            "Invalid cancellation from " + transition.get("from"));
      }
    }
    assertEquals(CANCELLABLE_STATES, cancellations);
  }

  @Test
  void masterSpecAndConfigurationPinRecoveryAndLimits() throws Exception {
    String master =
        section(
            masterSpecification(),
            "## 33.5 Deployment Execution and Recovery",
            "## 33.6 Snapshot Garbage Collection");
    for (String marker :
        Arrays.asList(
            "VALIDATING: validate all artifacts and manifest; any error -> REJECTED",
            "STAGED: copy artifacts to staging; verify hashes",
            "SNAPSHOTTING: snapshot every target path (including \"did not exist\" markers)",
            "persist state=RESTARTING ; request restart ; (process ends)",
            "for each deployment in {RESTARTING, VERIFYING, APPLYING, ROLLING_BACK}: recover(d)",
            "continue verification with remaining timeout (timeout is absolute, persisted)",
            "failure -> FAILED -> ROLLING_BACK (if automatic and attempts < max)",
            "if attempts exceeded or ROLLBACK_FAILED -> SAFE_STATE",
            "Every step is idempotent: re-running it after a crash must not corrupt state.",
            "A boot-loop counter is incremented at each recovery and reset on SUCCEEDED/ROLLED_BACK")) {
      assertTrue(master.contains(marker), "Master contract lost marker: " + marker);
    }

    String stateMachine = rootFile("specification/deployment/state-machine.md");
    assertTrue(stateMachine.contains("Snapshot the failed on-disk state"));
    assertTrue(stateMachine.contains("The automatic rollback attempt counter is bounded by"));
    assertTrue(
        stateMachine.contains("`deploy.rollback.max-attempts`; exceeding it enters `SAFE_STATE`"));
    assertTrue(
        stateMachine.contains(
            "If any step fails, persist `ROLLBACK_FAILED` and enter `SAFE_STATE`"));

    String schema = rootFile("specification/config/config.schema.json");
    String verification = section(schema, "\"verification\": {", "\"rollback\": {");
    String rollback = section(schema, "\"rollback\": {", "\"approval\": {");
    assertTrue(verification.contains("\"timeout-seconds\": {"));
    assertTrue(verification.contains("\"minimum\": 10"));
    assertTrue(verification.contains("\"maximum\": 1800"));
    assertTrue(verification.contains("\"default\": 180"));
    assertTrue(verification.contains("Total budget for post-restart checks"));
    assertTrue(rollback.contains("\"automatic\": {"));
    assertTrue(rollback.contains("\"default\": true"));
    assertTrue(rollback.contains("\"max-attempts\": {"));
    assertTrue(rollback.contains("\"minimum\": 0"));
    assertTrue(rollback.contains("\"maximum\": 10"));
    assertTrue(rollback.contains("\"default\": 2"));
    assertTrue(
        rollback.contains(
            "Automatic rollback attempts before the transaction enters `SAFE_STATE`"));

    String example = rootFile("specification/config/example.config.yaml");
    for (String marker :
        Arrays.asList(
            "  verification:",
            "    timeout-seconds: 180",
            "    require-plugin-enabled: true",
            "  rollback:",
            "    automatic: true",
            "    max-attempts: 2")) {
      assertTrue(example.contains(marker), "Example config lost marker: " + marker);
    }

    String errors = rootFile("specification/api/errors.md");
    assertError(
        errors,
        "deployments.invalid-state",
        "409",
        "The requested transition or cancellation is not allowed from the current state.");
    assertError(
        errors,
        "deployments.rollback-failed",
        "500",
        "Rollback could not complete; the instance entered `SAFE_STATE`.");
  }

  @Test
  void noProductDeploymentEngineExists() throws Exception {
    List<Path> sources = mainSources();
    assertFalse(sources.isEmpty(), "No main Java sources found");
    for (Path source : sources) {
      String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
      assertFalse(name.contains("deploymentengine"), source + " would be a product engine");
      assertFalse(name.contains("deploymentstatemachine"), source + " would be a product engine");
      assertFalse(name.contains("deploymentrecovery"), source + " would be a product engine");
      assertFalse(name.contains("statemachine"), source + " would be a product engine");
      String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
      for (String token :
          Arrays.asList(
              "SAFE_STATE",
              "ROLLING_BACK",
              "APPLYING",
              "deployments.invalid-state",
              "target-writes-occurred")) {
        assertFalse(text.contains(token), source + " contains " + token);
      }
    }
    assertTrue(sources.size() < 1000, "Source scan must stay bounded");
    String status = normalized("docs/development/implementation-status.md");
    assertTrue(
        status.contains("There is still no deployment engine or Web UI screen"),
        NO_PRODUCT_IMPLEMENTATION);
  }

  private static void assertTransition(
      Data data, String from, String to, String trigger, String actor, String guard) {
    for (Map<String, String> transition : data.transitions) {
      if (from.equals(transition.get("from"))
          && to.equals(transition.get("to"))
          && trigger.equals(transition.get("trigger"))) {
        assertEquals(actor, transition.get("actor"), from + " -> " + to + " actor");
        assertEquals(guard, transition.get("guard"), from + " -> " + to + " guard");
        return;
      }
    }
    throw new AssertionError("Missing transition " + from + " -> " + to + " on " + trigger);
  }

  private static void assertError(String catalog, String code, String status, String description) {
    String expected = "| `" + code + "` | " + status + " | no | " + description;
    assertTrue(catalog.contains(expected), "Missing stable error code: " + code);
  }

  private static String triple(Map<String, String> transition) {
    return transition.get("from") + "|" + transition.get("to") + "|" + transition.get("trigger");
  }

  private static Set<String> successors(Data data, String name) {
    Set<String> result = new HashSet<String>();
    for (Map<String, String> transition : data.transitions) {
      if (name.equals(transition.get("from"))) {
        result.add(transition.get("to"));
      }
    }
    return result;
  }

  private static Set<String> predecessors(Data data, String name) {
    Set<String> result = new HashSet<String>();
    for (Map<String, String> transition : data.transitions) {
      if (name.equals(transition.get("to"))) {
        result.add(transition.get("from"));
      }
    }
    return result;
  }

  private static Set<String> reachable(Data data, String start) {
    Set<String> visited = new HashSet<String>();
    Deque<String> queue = new ArrayDeque<String>();
    queue.add(start);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!visited.add(current)) {
        continue;
      }
      for (String next : successors(data, current)) {
        if (!visited.contains(next)) {
          queue.addLast(next);
        }
      }
    }
    return visited;
  }

  private static Data normativeData() {
    return parse(normativeText());
  }

  private static String normativeText() {
    try {
      return rootFile("specification/deployment/state-machine.md");
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }

  private static Data parse(String text) {
    int begin = text.indexOf("<!-- state-machine-data:begin -->");
    assertTrue(begin >= 0, "Missing normative data begin marker");
    int end = text.indexOf("<!-- state-machine-data:end -->", begin);
    assertTrue(end > begin, "Missing normative data end marker");
    String block = text.substring(begin, end);
    assertTrue(block.length() <= 256 * 1024, "Normative data block is too large");

    Map<String, String> header = new LinkedHashMap<String, String>();
    Map<String, Map<String, String>> states = new LinkedHashMap<String, Map<String, String>>();
    List<Map<String, String>> transitions = new ArrayList<Map<String, String>>();
    Map<String, String> recovery = new LinkedHashMap<String, String>();
    String mode = "";
    Map<String, String> current = null;
    String recoveryFrom = null;
    for (String raw : block.split("\n")) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#") || line.equals("```") || line.equals("```yaml")) {
        continue;
      }
      if (line.equals("states:")) {
        mode = "states";
        current = null;
        continue;
      }
      if (line.equals("transitions:")) {
        mode = "transitions";
        current = null;
        continue;
      }
      if (line.equals("recovery:")) {
        mode = "recovery";
        current = null;
        continue;
      }
      if ("states".equals(mode) && line.startsWith("- name: ")) {
        current = new LinkedHashMap<String, String>();
        current.put("name", value(line, "- name: "));
        states.put(current.get("name"), current);
        continue;
      }
      if ("transitions".equals(mode) && line.startsWith("- from: ")) {
        current = new LinkedHashMap<String, String>();
        current.put("from", value(line, "- from: "));
        transitions.add(current);
        continue;
      }
      if ("recovery".equals(mode) && line.startsWith("- from: ")) {
        recoveryFrom = value(line, "- from: ");
        assertFalse(recovery.containsKey(recoveryFrom), "Duplicate recovery state " + recoveryFrom);
        continue;
      }
      if ("recovery".equals(mode) && line.startsWith("action: ")) {
        assertNotNull(recoveryFrom, "Recovery action without state");
        recovery.put(recoveryFrom, value(line, "action: "));
        recoveryFrom = null;
        continue;
      }
      int colon = line.indexOf(':');
      if (colon > 0) {
        String key = line.substring(0, colon).trim();
        String value = line.substring(colon + 1).trim();
        if (current == null) {
          header.put(key, value);
        } else {
          current.put(key, value);
        }
      }
    }
    return new Data(header, states, transitions, recovery);
  }

  private static String value(String line, String prefix) {
    return line.substring(prefix.length()).trim();
  }

  private static String normalized(String relative) throws IOException {
    return rootFile(relative).replace('\n', ' ').replaceAll("\\s+", " ");
  }

  private static String section(String text, String start, String end) {
    int from = text.indexOf(start);
    assertTrue(from >= 0, "Missing section: " + start);
    int to = text.indexOf(end, from + start.length());
    assertTrue(to > from, "Missing section end: " + end);
    return text.substring(from, to);
  }

  private static String masterSpecification() throws IOException {
    return rootFile("docs/development/DeployMC_Complete_Master_Development_Specification.md");
  }

  private static List<Path> mainSources() throws IOException {
    Path root = repositoryRoot();
    List<Path> sources = new ArrayList<Path>();
    for (String module : Arrays.asList("core", "api", "transport", "platform")) {
      Path directory = root.resolve(module + "/src/main/java");
      if (Files.isDirectory(directory)) {
        sources.addAll(javaFiles(directory));
      }
    }
    return sources;
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.walk(root, 12)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .limit(1000)
          .collect(Collectors.<Path>toList());
    }
  }

  private static Path repositoryRoot() {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    for (int attempt = 0; attempt < 6; attempt++) {
      if (Files.isRegularFile(
              current.resolve(
                  "docs/development/DeployMC_Complete_Master_Development_Specification.md"))
          && Files.isRegularFile(current.resolve("specification/deployment/state-machine.md"))) {
        return current;
      }
      Path parent = current.getParent();
      if (parent == null) {
        break;
      }
      current = parent;
    }
    throw new AssertionError("Repository root not found from " + current);
  }

  private static String rootFile(String relative) throws IOException {
    Path path = repositoryRoot().resolve(relative);
    assertTrue(Files.isRegularFile(path), "Missing repository artifact: " + relative);
    byte[] bytes = Files.readAllBytes(path);
    assertTrue(bytes.length > 0 && bytes.length <= 8 * 1024 * 1024, relative + " size");
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static final class Data {
    final Map<String, String> header;
    final Map<String, Map<String, String>> states;
    final List<Map<String, String>> transitions;
    final Map<String, String> recovery;

    Data(
        Map<String, String> header,
        Map<String, Map<String, String>> states,
        List<Map<String, String>> transitions,
        Map<String, String> recovery) {
      this.header = header;
      this.states = states;
      this.transitions = transitions;
      this.recovery = recovery;
    }
  }
}
