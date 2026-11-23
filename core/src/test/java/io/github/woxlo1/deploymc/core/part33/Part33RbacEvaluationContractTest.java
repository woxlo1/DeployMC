package io.github.woxlo1.deploymc.core.part33;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PART 33.4 specification contract for role and path evaluation. No product RBAC evaluator exists
 * in this repository. These tests validate the normative algorithm and its configuration and error
 * artifacts only; they claim neither implemented behavior nor parity with any external system.
 */
class Part33RbacEvaluationContractTest {

  private static final String NO_PRODUCT_IMPLEMENTATION =
      "No product RBAC evaluator exists; this is a SPEC/FIXTURE CONTRACT test only";

  private static final List<String> PERMISSIONS =
      Arrays.asList(
          "server.read",
          "files.read",
          "files.write",
          "files.delete",
          "files.upload",
          "snapshots.read",
          "snapshots.restore",
          "snapshots.prune",
          "deployments.read",
          "deployments.create",
          "deployments.create.jar",
          "deployments.approve",
          "deployments.cancel",
          "deployments.rollback",
          "server.restart",
          "console.read",
          "console.command",
          "audit.read",
          "audit.export",
          "tokens.manage.self",
          "tokens.manage.all",
          "webhooks.manage",
          "users.read");

  private static final List<String> REASONS =
      Arrays.asList("NO_ROLE", "NOT_GRANTED", "PATH_DENIED", "PATH_SCOPE");

  @Test
  void normativeEvaluatorDefinesFailClosedReasons() throws Exception {
    String block =
        section(
            masterSpecification(),
            "## 33.4 RBAC Evaluation",
            "## 33.5 Deployment Execution and Recovery");
    for (String marker :
        Arrays.asList(
            "authorize(identity, permission, path?) -> Decision(allow|deny, reason)",
            "roles = userRoles(identity) or [default-role]",
            "if roles empty -> deny(NO_ROLE)",
            "for each role: collect grants and path rules",
            "if no role grants permission -> deny(NOT_GRANTED)",
            "resolve path via sandbox (never evaluate raw strings)",
            "if matches any deny rule of any applicable role -> deny(PATH_DENIED)",
            "if some applicable role grants permission and its allow rules match",
            "else -> deny(PATH_SCOPE)",
            "record decision reason for explain-mode and audit (denials always audited)")) {
      assertTrue(block.contains(marker), "Evaluator contract lost marker: " + marker);
    }
    for (String reason : REASONS) {
      assertTrue(block.contains(reason), "Missing reason code: " + reason);
    }
    assertTrue(block.indexOf("deny(NO_ROLE)") < block.indexOf("deny(NOT_GRANTED)"));
    assertTrue(block.indexOf("deny(PATH_DENIED)") < block.indexOf("deny(PATH_SCOPE)"));
    assertFalse(block.contains("allow by default"));
  }

  @Test
  void tokenScopesIntersectWithOwnerPermissions() throws Exception {
    String master = masterSpecification();
    String tokens =
        section(master, "## 8.5 Authentication for API Clients", "## 8.6 Java API Module");
    String authorization =
        section(master, "## 33.4 RBAC Evaluation", "## 33.5 Deployment Execution and Recovery");
    assertTrue(tokens.contains("scoped (permissions + path scopes)"));
    assertTrue(tokens.contains("an API token can never exceed its owner's permissions"));
    assertTrue(
        authorization.contains("tokens: effective = intersection(tokenScopes, ownerEffective)"));
    assertFalse(authorization.contains("union(tokenScopes"));
    assertFalse(authorization.contains("tokenScopes + ownerEffective"));
  }

  @Test
  void permissionIdentifiersAndPathScopeAreNormative() throws Exception {
    String block =
        section(
            masterSpecification(),
            "## 24.2 Permission Identifiers",
            "## 24.3 Audit Log Record Format");
    assertTrue(block.contains("Permissions are flat, dotted, lowercase identifiers"));
    assertTrue(block.contains("`*` is allowed only as a whole-grant for admin roles"));
    assertEqualsSize(23, PERMISSIONS);
    for (String permission : PERMISSIONS) {
      boolean found = false;
      for (String line : block.split("\n")) {
        if (line.trim().startsWith(permission + " ")) {
          found = true;
          break;
        }
      }
      assertTrue(found, "Missing permission: " + permission);
    }
    assertTrue(
        block.contains("Path scoping applies to `files.*`, `snapshots.*`, and `deployments.*`"));
    assertTrue(block.contains("whole sandbox root, minus protected paths"));
  }

  @Test
  void configSchemaEncodesFailClosedRoleRules() throws Exception {
    String schema = rootFile("specification/config/config.schema.json");
    for (String marker :
        Arrays.asList(
            "\"roleName\": {",
            "\"permission\": {",
            "\"pathGlob\": {",
            "\"role\": {",
            "\"required\": [\"permissions\"]",
            "Every name must exist in `roles`.",
            "\"default\": \"none\"",
            "`none` denies all access and is the only safe default",
            "Empty means no path-scoped permission is granted.",
            "Deny wins over allow and over the sandbox default.",
            "Absolute paths, backslashes, NUL, and `..` segments are rejected.",
            "\"maxProperties\": 256",
            "\"maxItems\": 256")) {
      assertTrue(schema.contains(marker), "Schema contract lost marker: " + marker);
    }
    assertFalse(schema.contains("\"default\": \"admin\""));
  }

  @Test
  void exampleAndDefaultConfigsAreFailClosed() throws Exception {
    String example = rootFile("specification/config/example.config.yaml");
    for (String marker :
        Arrays.asList(
            "roles:",
            "  admin:",
            "      - \"*\"",
            "  developer:",
            "      - files.read",
            "      - files.write",
            "      - snapshots.read",
            "      - snapshots.restore",
            "      - deployments.create",
            "      - console.read",
            "    paths:",
            "      allow:",
            "        - \"MyRpgPlugin/**\"",
            "        - \"MyRpgPlugin.jar\"",
            "      deny:",
            "        - \"**/secrets.yml\"",
            "  observer:",
            "  ci-deployer:",
            "users:",
            "  \"alice@example.com\":",
            "  \"bob@example.com\":",
            "  \"ci-service-token\":",
            "default-role: none")) {
      assertTrue(example.contains(marker), "Example config lost marker: " + marker);
    }
    assertFalse(example.contains("default-role: admin"));
    String defaults = rootFile("core/src/main/resources/config/default-config.yml");
    assertTrue(defaults.contains("roles: {}"));
    assertTrue(defaults.contains("users: {}"));
    assertTrue(defaults.contains("default-role: none"));
    assertFalse(defaults.contains("default-role: admin"));
  }

  @Test
  void authorizationErrorCodesAreStable() throws Exception {
    String catalog = rootFile("specification/api/errors.md");
    assertError(catalog, "rbac.denied", "The identity lacks the required permission.");
    assertError(
        catalog,
        "rbac.path-denied",
        "The identity lacks access to this path scope, or a deny rule matched.");
    assertError(
        catalog,
        "rbac.self-approval-denied",
        "The requester tried to approve their own deployment.");
    assertError(
        catalog,
        "rbac.token-scope-exceeded",
        "The token scope is narrower than the requested action, or wider than its owner's permissions.");
  }

  @Test
  void noProductRbacEvaluatorExists() throws Exception {
    List<Path> sources = mainSources();
    assertFalse(sources.isEmpty(), "No main Java sources found");
    for (Path source : sources) {
      String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
      assertFalse(name.contains("rbac"), source + " would be a product evaluator");
      assertFalse(name.contains("roleevaluator"), source + " would be a product evaluator");
      assertFalse(name.contains("permissionevaluator"), source + " would be a product evaluator");
      assertFalse(
          name.contains("authorizationevaluator"), source + " would be a product evaluator");
      String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
      for (String reason : REASONS) {
        assertFalse(text.contains(reason), source + " contains " + reason);
      }
      assertFalse(text.contains("authorize(identity, permission, path?)"), source.toString());
      assertFalse(text.contains("intersection(tokenScopes, ownerEffective)"), source.toString());
    }
    assertTrue(sources.size() < 1000, "Source scan must stay bounded");
    String status = normalized("docs/development/implementation-status.md");
    assertTrue(
        status.contains("There is no deployment engine, authorization, audit log"),
        NO_PRODUCT_IMPLEMENTATION);
  }

  private static void assertError(String catalog, String code, String description) {
    String expected = "| `" + code + "` | 403 | no | " + description;
    assertTrue(catalog.contains(expected), "Missing stable error code: " + code);
  }

  private static void assertEqualsSize(int expected, List<String> values) {
    assertTrue(
        values.size() == expected, "Expected " + expected + " entries but got " + values.size());
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
          && Files.isRegularFile(current.resolve("specification/config/config.schema.json"))) {
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
}
