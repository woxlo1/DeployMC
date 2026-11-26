package io.github.woxlo1.deploymc.core.rbac;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Identity;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Secure-random property checks; no external property-testing dependency. */
class RbacTokenSubsetPropertyTest {

  private static final int CASES = 500;

  @Test
  void tokenEffectivePermissionsNeverExceedOwnerEffectivePermissions() {
    SecureRandom random = new SecureRandom();
    for (int index = 0; index < CASES; index++) {
      int roleCount = 1 + random.nextInt(5);
      Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
      List<String> roleNames = new ArrayList<String>();
      for (int roleIndex = 0; roleIndex < roleCount; roleIndex++) {
        String name = "role" + roleIndex;
        roleNames.add(name);
        List<String> grants = new ArrayList<String>();
        for (String permission : PermissionRegistry.ALL) {
          if (random.nextInt(4) == 0) {
            grants.add(permission);
          }
        }
        roles.put(
            name,
            new RolePolicy(
                name, false, grants, PathScope.wholeSandbox(Collections.<String>emptyList())));
      }
      List<String> assigned = new ArrayList<String>();
      for (String name : roleNames) {
        if (random.nextInt(2) == 0) {
          assigned.add(name);
        }
      }
      if (assigned.isEmpty()) {
        assigned.add(roleNames.get(0));
      }
      Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
      users.put("owner", Collections.unmodifiableList(assigned));
      RbacPolicy policy = RbacPolicy.of(roles, users, "none");
      RbacEvaluator evaluator = new RbacEvaluator(policy, null);

      List<String> scopes = new ArrayList<String>();
      for (String permission : PermissionRegistry.ALL) {
        if (random.nextInt(3) == 0) {
          scopes.add(permission);
        }
      }
      String scopeClaim = String.join(",", scopes);
      List<String> ownerEffective = evaluator.effective(owner());
      List<String> tokenEffective = evaluator.effective(token(scopeClaim));
      assertTrue(
          ownerEffective.containsAll(tokenEffective),
          "token exceeded owner at case " + index + ": " + tokenEffective);
      for (String permission : PermissionRegistry.ALL) {
        if (!ownerEffective.contains(permission)) {
          assertTrue(
              !tokenEffective.contains(permission),
              "token gained a removed owner permission at case " + index);
        }
      }

      roles.put(
          assigned.get(0),
          new RolePolicy(
              assigned.get(0),
              false,
              Collections.<String>emptyList(),
              PathScope.wholeSandbox(Collections.<String>emptyList())));
      RbacEvaluator reduced = new RbacEvaluator(RbacPolicy.of(roles, users, "none"), null);
      List<String> reducedOwner = reduced.effective(owner());
      List<String> reducedToken = reduced.effective(token(scopeClaim));
      assertTrue(
          reducedOwner.containsAll(reducedToken), "token exceeded reduced owner at case " + index);
    }
  }

  @Test
  void registryOrderIsStableAndUnknownPermissionsAreIgnored() {
    Set<String> known = new LinkedHashSet<String>(PermissionRegistry.ALL);
    List<String> canonical =
        PermissionRegistry.canonical(
            Arrays.asList("files.write", "server.status", "server.read", "files.write"));
    assertTrue(known.containsAll(canonical));
    assertTrue(canonical.indexOf("server.read") < canonical.indexOf("files.write"));
    assertTrue(!PermissionRegistry.isKnown("server.status"));
    assertTrue(!PermissionRegistry.isKnown("metrics.read"));
    assertTrue(!PermissionRegistry.isKnown("*"));
  }

  private static Identity owner() {
    return new Identity(
        "owner",
        "owner",
        "cloudflare-access",
        false,
        Collections.<String>emptyList(),
        Collections.<String, String>emptyMap());
  }

  private static Identity token(String scopes) {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("token.id", "t-example");
    claims.put("auth.method", "token");
    claims.put("token.owner", "owner");
    if (!scopes.isEmpty()) {
      claims.put("token.scopes", scopes);
    }
    return new Identity(
        "t-example", "example", "token", false, Collections.<String>emptyList(), claims);
  }
}
