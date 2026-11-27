package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.rbac.PathScope;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.rbac.RolePolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded evaluator fixtures for server-free transport tests. */
final class RbacTestSupport {

  private RbacTestSupport() {}

  static RbacEvaluator permitAll() {
    return evaluator(true, Collections.<String>emptyList());
  }

  static RbacEvaluator permitting(String... permissions) {
    return evaluator(false, Arrays.asList(permissions));
  }

  static RbacEvaluator denyAll() {
    return new RbacEvaluator(RbacPolicy.denyAll(), null);
  }

  static RbacEvaluator rateLimited(int rateLimitPerMinute) {
    RolePolicy role =
        new RolePolicy(
            "rate-limited",
            true,
            Collections.<String>emptyList(),
            PathScope.wholeSandbox(Collections.<String>emptyList()),
            rateLimitPerMinute);
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put("rate-limited", role);
    return new RbacEvaluator(
        RbacPolicy.of(roles, Collections.<String, List<String>>emptyMap(), "rate-limited"), null);
  }

  private static RbacEvaluator evaluator(boolean wildcard, List<String> permissions) {
    RolePolicy role =
        new RolePolicy(
            "test", wildcard, permissions, PathScope.wholeSandbox(Collections.<String>emptyList()));
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put("test", role);
    return new RbacEvaluator(
        RbacPolicy.of(roles, Collections.<String, List<String>>emptyMap(), "test"), null);
  }
}
