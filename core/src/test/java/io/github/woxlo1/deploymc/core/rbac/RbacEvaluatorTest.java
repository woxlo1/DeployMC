package io.github.woxlo1.deploymc.core.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RbacEvaluatorTest {

  @TempDir Path root;

  @Test
  void deniesUnknownIdentityWithNoRole() {
    RbacEvaluator evaluator = evaluator(policy());

    AuthorizationDecision decision = evaluator.authorize(user("stranger"), "files.read", null);

    assertFalse(decision.allowed());
    assertEquals(DecisionReason.NO_ROLE, decision.reason());
    assertEquals("rbac.denied", decision.errorCode());
  }

  @Test
  void deniesPermissionNotGrantedByAnyRole() {
    RbacEvaluator evaluator = evaluator(policy());

    AuthorizationDecision decision = evaluator.authorize(user("carol"), "files.write", null);

    assertEquals(DecisionReason.NOT_GRANTED, decision.reason());
  }

  @Test
  void wildcardGrantsEveryRegistryPermissionButStillResolvesPaths() {
    RbacEvaluator evaluator = evaluator(policy());

    assertTrue(evaluator.authorize(user("alice"), "files.delete", "plugin.jar").allowed());
    assertEquals(
        DecisionReason.PATH_DENIED,
        evaluator.authorize(user("alice"), "files.delete", "/etc/passwd").reason());
  }

  @Test
  void denyRuleFromNonGrantingRoleWins() {
    RbacEvaluator evaluator = evaluator(policy());

    AuthorizationDecision decision =
        evaluator.authorize(user("bob"), "files.read", "docs/secrets.yml");

    assertEquals(DecisionReason.PATH_DENIED, decision.reason());
    assertEquals(1, decision.explanation().rolesDenyRuleMatches());
  }

  @Test
  void absentAllowMatchesWholeSandboxButEmptyAllowMatchesNothing() {
    RbacEvaluator evaluator = evaluator(policy());

    assertTrue(evaluator.authorize(user("carol"), "files.read", "readme.txt").allowed());
    assertEquals(
        DecisionReason.PATH_SCOPE,
        evaluator.authorize(user("dave"), "files.read", "readme.txt").reason());
  }

  @Test
  void protectedPathsAreDeniedThroughSandboxResolver() {
    RbacEvaluator evaluator = evaluator(policy());

    assertEquals(
        DecisionReason.PATH_DENIED,
        evaluator.authorize(user("alice"), "files.read", ".deploymc/config.yml").reason());
  }

  @Test
  void tokenScopesNarrowAndNeverExceedOwnerPermissions() {
    RbacEvaluator evaluator = evaluator(policy());

    assertTrue(
        evaluator
            .authorize(token("token-owner", "files.read"), "files.read", "docs/a.txt")
            .allowed());
    assertEquals(
        DecisionReason.PATH_SCOPE,
        evaluator
            .authorize(token("token-owner", "files.read"), "files.read", "other/a.txt")
            .reason());
    assertEquals(
        DecisionReason.NOT_GRANTED,
        evaluator.authorize(token("token-owner", "files.delete"), "files.delete", null).reason());
    assertEquals(
        DecisionReason.TOKEN_SCOPE_EXCEEDED,
        evaluator.authorize(token("admin-owner", "files.read"), "files.write", null).reason());
    assertEquals(
        DecisionReason.NO_ROLE,
        evaluator.authorize(token(null, "files.read"), "files.read", null).reason());
  }

  @Test
  void effectivePermissionsAreCanonicalAndTokenNarrowed() {
    RbacEvaluator evaluator = evaluator(policy());

    assertEquals(
        Arrays.asList("files.read", "files.write", "snapshots.read"),
        evaluator.effective(user("bob")));
    assertEquals(
        Collections.singletonList("files.read"),
        evaluator.effective(token("admin-owner", "files.read")));
    assertTrue(evaluator.effective(user("stranger")).isEmpty());
  }

  @Test
  void roleLimitsLowerTheIdentityBudgetButNeverRaiseIt() {
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put(
        "limited",
        new RolePolicy(
            "limited",
            false,
            Collections.singletonList("files.read"),
            PathScope.wholeSandbox(Collections.<String>emptyList()),
            12));
    roles.put(
        "unlimited",
        new RolePolicy(
            "unlimited",
            false,
            Collections.singletonList("files.read"),
            PathScope.wholeSandbox(Collections.<String>emptyList())));
    Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
    users.put("limited-user", Arrays.asList("limited", "unlimited"));
    users.put("normal-user", Collections.singletonList("unlimited"));
    RbacEvaluator evaluator = evaluator(RbacPolicy.of(roles, users, "none"));

    assertEquals(12, evaluator.identityRateLimitPerMinute(user("limited-user"), 300));
    assertEquals(5, evaluator.identityRateLimitPerMinute(user("limited-user"), 5));
    assertEquals(300, evaluator.identityRateLimitPerMinute(user("normal-user"), 300));
  }

  @Test
  void reasonsMapToStableProblemCodes() {
    assertEquals("rbac.denied", DecisionReason.NO_ROLE.problemCode());
    assertEquals("rbac.denied", DecisionReason.NOT_GRANTED.problemCode());
    assertEquals("rbac.token-scope-exceeded", DecisionReason.TOKEN_SCOPE_EXCEEDED.problemCode());
    assertEquals("rbac.path-denied", DecisionReason.PATH_DENIED.problemCode());
    assertEquals("rbac.path-denied", DecisionReason.PATH_SCOPE.problemCode());
  }

  @Test
  void explainCarriesOnlyBooleansAndBoundedCounts() {
    RbacEvaluator evaluator = evaluator(policy());

    AuthorizationDecision decision =
        evaluator.authorize(user("bob"), "files.read", "docs/secrets.yml");
    DecisionExplanation explanation = decision.explanation();

    assertTrue(explanation.pathRequested());
    assertTrue(explanation.pathResolved());
    assertFalse(explanation.pathWithinScope());
    assertFalse(explanation.tokenPresent());
    assertEquals(2, explanation.rolesAssigned());
    assertEquals(1, explanation.rolesGranting());
    assertEquals(1, explanation.rolesDenyRuleMatches());
    assertEquals(0, explanation.rolesAllowRuleMatches());
  }

  private RbacEvaluator evaluator(RbacPolicy policy) {
    return new RbacEvaluator(policy, new SandboxResolver(root));
  }

  private static RbacPolicy policy() {
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put(
        "admin",
        new RolePolicy(
            "admin",
            true,
            Collections.<String>emptyList(),
            PathScope.wholeSandbox(Collections.<String>emptyList())));
    roles.put(
        "developer",
        new RolePolicy(
            "developer",
            false,
            Arrays.asList("files.read", "files.write", "snapshots.read"),
            PathScope.restricted(
                Collections.singletonList("docs/**"), Collections.<String>emptyList())));
    roles.put(
        "viewer",
        new RolePolicy(
            "viewer",
            false,
            Collections.singletonList("files.read"),
            PathScope.wholeSandbox(Collections.<String>emptyList())));
    roles.put(
        "empty-paths",
        new RolePolicy(
            "empty-paths",
            false,
            Collections.singletonList("files.read"),
            PathScope.restricted(
                Collections.<String>emptyList(), Collections.<String>emptyList())));
    roles.put(
        "deny-only",
        new RolePolicy(
            "deny-only",
            false,
            Collections.<String>emptyList(),
            PathScope.wholeSandbox(Collections.singletonList("**/secrets.yml"))));

    Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
    users.put("alice", Collections.singletonList("admin"));
    users.put("bob", Arrays.asList("developer", "deny-only"));
    users.put("carol", Collections.singletonList("viewer"));
    users.put("dave", Collections.singletonList("empty-paths"));
    users.put("token-owner", Collections.singletonList("developer"));
    users.put("admin-owner", Collections.singletonList("admin"));
    return RbacPolicy.of(roles, users, "none");
  }

  private static Identity user(String id) {
    return new Identity(
        id,
        id,
        "cloudflare-access",
        false,
        Collections.<String>emptyList(),
        Collections.<String, String>emptyMap());
  }

  private static Identity token(String owner, String scopes) {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("token.id", "t-example");
    claims.put("token.label", "example");
    claims.put("auth.method", "token");
    if (owner != null) {
      claims.put("token.owner", owner);
    }
    if (scopes != null && !scopes.isEmpty()) {
      claims.put("token.scopes", scopes);
    }
    return new Identity(
        "t-example", "example", "token", false, Collections.<String>emptyList(), claims);
  }
}
