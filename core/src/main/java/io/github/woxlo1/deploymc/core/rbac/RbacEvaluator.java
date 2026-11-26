package io.github.woxlo1.deploymc.core.rbac;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxPath;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Immutable, deterministic WP-3.4 authorization evaluator. */
public final class RbacEvaluator {

  private static final String TOKEN_METHOD = "token";
  private static final String CLAIM_TOKEN_OWNER = "token.owner";
  private static final String CLAIM_TOKEN_SCOPES = "token.scopes";

  private final Supplier<RbacPolicy> policySource;
  private final SandboxResolver resolver;

  public RbacEvaluator(RbacPolicy policy, SandboxResolver resolver) {
    this(fixedPolicy(Objects.requireNonNull(policy, "policy")), resolver);
  }

  /**
   * Creates an evaluator that reads the host's current immutable policy snapshot for every
   * decision. Invalid reloads must install a deny-all snapshot in the source before any later
   * request.
   */
  public RbacEvaluator(Supplier<RbacPolicy> policySource, SandboxResolver resolver) {
    this.policySource = Objects.requireNonNull(policySource, "policySource");
    this.resolver = resolver;
  }

  public RbacPolicy policy() {
    return policySource.get();
  }

  /** Policy lookup key for one verified identity; token identities resolve to their owner. */
  public String identityKey(Identity identity) {
    return lookupIdentity(identity);
  }

  /** Assigned role names for one verified identity at decision time. */
  public List<String> rolesFor(Identity identity) {
    return policy().rolesFor(identityKey(identity));
  }

  private static Supplier<RbacPolicy> fixedPolicy(final RbacPolicy policy) {
    return new Supplier<RbacPolicy>() {
      @Override
      public RbacPolicy get() {
        return policy;
      }
    };
  }

  public AuthorizationDecision authorize(
      AuthContext context, String permission, String requestedPath) {
    Objects.requireNonNull(context, "context");
    return authorize(context.identity(), permission, requestedPath, SandboxAccessMode.READ);
  }

  public AuthorizationDecision authorize(
      Identity identity, String permission, String requestedPath) {
    return authorize(identity, permission, requestedPath, SandboxAccessMode.READ);
  }

  public AuthorizationDecision authorize(
      Identity identity, String permission, String requestedPath, SandboxAccessMode mode) {
    Objects.requireNonNull(mode, "mode");
    RbacPolicy policy = policySource.get();
    boolean token = isToken(identity);
    Set<String> tokenScopes = token ? tokenScopes(identity) : Collections.<String>emptySet();
    boolean tokenContainsPermission =
        !token || tokenScopes.isEmpty() || tokenScopes.contains(permission);

    List<String> assignedNames = policy.rolesFor(lookupIdentity(identity));
    int assignedCount = assignedNames.size();
    if (assignedCount == 0 || assignedCount == 1 && "none".equals(assignedNames.get(0))) {
      return deny(
          DecisionReason.NO_ROLE,
          false,
          false,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          0,
          0,
          0);
    }

    List<RolePolicy> granting = new ArrayList<RolePolicy>();
    for (String name : assignedNames) {
      RolePolicy role = policy.role(name);
      if (role != null && PermissionRegistry.isKnown(permission) && role.grants(permission)) {
        granting.add(role);
      }
    }
    if (granting.isEmpty()) {
      return deny(
          DecisionReason.NOT_GRANTED,
          false,
          false,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          0,
          0,
          0);
    }
    if (!tokenContainsPermission) {
      return deny(
          DecisionReason.TOKEN_SCOPE_EXCEEDED,
          false,
          false,
          false,
          token,
          false,
          assignedCount,
          granting.size(),
          0,
          0);
    }

    boolean pathRequested = requestedPath != null && !requestedPath.isEmpty();
    if (!pathRequested) {
      return AuthorizationDecision.allow(
          new DecisionExplanation(
              false,
              false,
              false,
              token,
              tokenContainsPermission,
              assignedCount,
              granting.size(),
              0,
              0));
    }

    if (resolver == null) {
      return deny(
          DecisionReason.PATH_DENIED,
          true,
          false,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          granting.size(),
          0,
          0);
    }
    String relative;
    try {
      SandboxPath resolved = resolver.resolve(requestedPath, mode);
      relative = resolved.relativePath();
    } catch (SandboxRejectionException | RuntimeException exception) {
      return deny(
          DecisionReason.PATH_DENIED,
          true,
          false,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          granting.size(),
          0,
          0);
    }

    int denyMatches = 0;
    for (String name : assignedNames) {
      RolePolicy role = policy.role(name);
      if (role != null && role.pathScope().matchesDeny(relative)) {
        denyMatches++;
      }
    }
    if (denyMatches > 0) {
      return deny(
          DecisionReason.PATH_DENIED,
          true,
          true,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          granting.size(),
          denyMatches,
          0);
    }

    int allowMatches = 0;
    for (RolePolicy role : granting) {
      if (role.pathScope().matchesAllow(relative)) {
        allowMatches++;
      }
    }
    if (allowMatches == 0) {
      return deny(
          DecisionReason.PATH_SCOPE,
          true,
          true,
          false,
          token,
          tokenContainsPermission,
          assignedCount,
          granting.size(),
          0,
          0);
    }
    return AuthorizationDecision.allow(
        new DecisionExplanation(
            true,
            true,
            true,
            token,
            tokenContainsPermission,
            assignedCount,
            granting.size(),
            0,
            allowMatches));
  }

  /** Effective permissions in canonical registry order after token scope narrowing. */
  public List<String> effective(Identity identity) {
    if (identity == null) {
      return Collections.emptyList();
    }
    RbacPolicy policy = policySource.get();
    Set<String> combined = new LinkedHashSet<String>();
    for (String name : policy.rolesFor(lookupIdentity(identity))) {
      RolePolicy role = policy.role(name);
      if (role != null) {
        combined.addAll(role.grantedPermissions());
      }
    }
    List<String> effective = PermissionRegistry.canonical(combined);
    if (!isToken(identity)) {
      return effective;
    }
    Set<String> scopes = tokenScopes(identity);
    if (scopes.isEmpty()) {
      return effective;
    }
    List<String> narrowed = new ArrayList<String>();
    for (String permission : effective) {
      if (scopes.contains(permission)) {
        narrowed.add(permission);
      }
    }
    return Collections.unmodifiableList(narrowed);
  }

  private static AuthorizationDecision deny(
      DecisionReason reason,
      boolean pathRequested,
      boolean pathResolved,
      boolean pathWithinScope,
      boolean tokenPresent,
      boolean tokenContainsPermission,
      int rolesAssigned,
      int rolesGranting,
      int denyRuleMatches,
      int allowRuleMatches) {
    DecisionExplanation explanation =
        new DecisionExplanation(
            pathRequested,
            pathResolved,
            pathWithinScope,
            tokenPresent,
            tokenContainsPermission,
            rolesAssigned,
            rolesGranting,
            denyRuleMatches,
            allowRuleMatches);
    return AuthorizationDecision.deny(reason, explanation);
  }

  private static String lookupIdentity(Identity identity) {
    if (identity == null) {
      return null;
    }
    if (isToken(identity)) {
      return identity.claims().get(CLAIM_TOKEN_OWNER);
    }
    return identity.id();
  }

  private static boolean isToken(Identity identity) {
    return identity != null && TOKEN_METHOD.equals(identity.method());
  }

  private static Set<String> tokenScopes(Identity identity) {
    String raw = identity.claims().get(CLAIM_TOKEN_SCOPES);
    if (raw == null || raw.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> scopes = new LinkedHashSet<String>();
    for (String scope : raw.split(",", -1)) {
      if (PermissionRegistry.isKnown(scope)) {
        scopes.add(scope);
      }
    }
    return scopes;
  }
}
