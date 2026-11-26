package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.AuthorizationDecision;
import io.github.woxlo1.deploymc.core.rbac.DecisionReason;
import io.github.woxlo1.deploymc.core.rbac.PathScope;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.rbac.RolePolicy;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free tests for the closed route-to-permission map and bounded path decoding. */
class RbacGateTest {

  private static final String ACTOR = "actor-1";
  private static final String OWNER = "owner-1";

  @TempDir Path sandboxRoot;

  @Test
  void closedRouteMapUsesDistinctPermissions() {
    RbacGate all = new RbacGate(RbacTestSupport.permitAll());
    String[] openRoutes = {
      "/api/v1/server",
      "/api/v1/server/doctor",
      "/api/v1/files",
      "/api/v1/validation",
      "/api/v1/snapshots",
      "/api/v1/metrics"
    };
    for (String route : openRoutes) {
      assertTrue(all.authorize(request("GET", route, "", user(ACTOR))).allowed(), route);
    }
    assertTrue(all.authorizeWebSocket(user(ACTOR)).allowed());
    assertTrue(
        all.authorize(request("DELETE", "/api/v1/snapshots/s-1", "", user(ACTOR))).allowed());
    assertTrue(
        all.authorize(request("POST", "/api/v1/snapshots/s-1/restore", "", user(ACTOR))).allowed());
    assertTrue(all.authorize(request("POST", "/api/v1/files/upload", "", user(ACTOR))).allowed());

    RbacGate filesRead = new RbacGate(RbacTestSupport.permitting("files.read"));
    assertTrue(filesRead.authorize(request("GET", "/api/v1/files", "", user(ACTOR))).allowed());
    assertTrue(
        filesRead.authorize(request("GET", "/api/v1/validation", "", user(ACTOR))).allowed());
    assertDenied(filesRead.authorize(request("GET", "/api/v1/server", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("GET", "/api/v1/server/doctor", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("GET", "/api/v1/snapshots", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("GET", "/api/v1/metrics", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("POST", "/api/v1/files/upload", "", user(ACTOR))));
    assertDenied(
        filesRead.authorize(request("DELETE", "/api/v1/files/notes.txt", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("PATCH", "/api/v1/files/notes.txt", "", user(ACTOR))));
    assertDenied(filesRead.authorizeWebSocket(user(ACTOR)));
    assertDenied(filesRead.authorize(request("GET", "/api/v1/unmapped", "", user(ACTOR))));
    assertDenied(filesRead.authorize(request("OPTIONS", "/api/v1/files", "", user(ACTOR))));
  }

  @Test
  void auditRoutesUseDedicatedReadAndExportPermissions() {
    RbacGate all = new RbacGate(RbacTestSupport.permitAll());
    assertTrue(all.authorize(request("GET", "/api/v1/audit", "", user(ACTOR))).allowed());
    assertTrue(all.authorize(request("GET", "/api/v1/audit/verify", "", user(ACTOR))).allowed());
    assertTrue(all.authorize(request("GET", "/api/v1/audit/export", "", user(ACTOR))).allowed());

    RbacGate reader = new RbacGate(RbacTestSupport.permitting("audit.read"));
    assertTrue(reader.authorize(request("GET", "/api/v1/audit", "", user(ACTOR))).allowed());
    assertTrue(reader.authorize(request("GET", "/api/v1/audit/verify", "", user(ACTOR))).allowed());
    assertDenied(reader.authorize(request("GET", "/api/v1/audit/export", "", user(ACTOR))));
    assertDenied(reader.authorize(request("POST", "/api/v1/audit", "", user(ACTOR))));
    assertDenied(reader.authorize(request("GET", "/api/v1/audit/unknown", "", user(ACTOR))));
    assertDenied(
        new RbacGate(RbacTestSupport.permitting("audit.export"))
            .authorize(request("GET", "/api/v1/audit", "", user(ACTOR))));
  }

  @Test
  void routesWithoutExtraPermissionDoNotConsultTheCallerPolicy() {
    RbacGate denyAll = new RbacGate(RbacTestSupport.denyAll());
    AuthorizationDecision health = denyAll.authorize(request("GET", "/healthz", "", null));
    AuthorizationDecision ready = denyAll.authorize(request("GET", "/readyz", "", null));
    AuthorizationDecision me = denyAll.authorize(request("GET", "/api/v1/me", "", user(ACTOR)));
    AuthorizationDecision staticAsset =
        denyAll.authorize(request("GET", "/assets/index-abcdef12.js", "", null));

    assertTrue(health.allowed());
    assertTrue(ready.allowed());
    assertTrue(me.allowed());
    assertTrue(staticAsset.allowed());
    assertNull(health.reason());
    assertDenied(denyAll.authorize(request("GET", "/api/v1/server", "", user(ACTOR))));
  }

  @Test
  void missingIdentityDeniesProtectedRoutes() {
    RbacGate gate = new RbacGate(RbacTestSupport.permitAll());
    AuthorizationDecision decision = gate.authorize(request("GET", "/api/v1/server", "", null));
    AuthorizationDecision webSocket = gate.authorizeWebSocket(null);

    assertFalse(decision.allowed());
    assertEquals(DecisionReason.NOT_GRANTED, decision.reason());
    assertEquals("rbac.denied", decision.errorCode());
    assertFalse(webSocket.allowed());
    assertEquals(DecisionReason.NOT_GRANTED, webSocket.reason());
  }

  @Test
  void fileRouteUsesSandboxResolvedPathScope() {
    RolePolicy reader =
        new RolePolicy(
            "reader",
            false,
            Collections.singletonList("files.read"),
            PathScope.restricted(
                Collections.singletonList("docs/**"),
                Collections.singletonList("docs/secrets.yml")));
    RbacPolicy policy =
        RbacPolicy.of(
            Collections.<String, RolePolicy>singletonMap("reader", reader),
            Collections.<String, List<String>>emptyMap(),
            "reader");
    RbacGate gate = new RbacGate(new RbacEvaluator(policy, new SandboxResolver(sandboxRoot)));

    assertTrue(
        gate.authorize(request("GET", "/api/v1/files", "path=docs/note.txt", user(ACTOR)))
            .allowed());
    assertEquals(
        DecisionReason.PATH_SCOPE,
        gate.authorize(request("GET", "/api/v1/files", "path=other/note.txt", user(ACTOR)))
            .reason());
    assertEquals(
        DecisionReason.PATH_DENIED,
        gate.authorize(request("GET", "/api/v1/files", "path=docs/secrets.yml", user(ACTOR)))
            .reason());
    assertTrue(
        gate.authorize(request("GET", "/api/v1/files/docs%2fnote.txt", "", user(ACTOR))).allowed());
  }

  @Test
  void metricsPermissionIsBoundToTheConfiguredServerReadGrant() {
    assertTrue(ObservabilityConfig.isSupportedPermission("server.read"));
    assertFalse(ObservabilityConfig.isSupportedPermission("files.read"));

    assertTrue(
        new RbacGate(RbacTestSupport.permitting("server.read"))
            .authorize(request("GET", "/api/v1/metrics", "", user(ACTOR)))
            .allowed());
    assertDenied(
        new RbacGate(RbacTestSupport.permitting("files.read"))
            .authorize(request("GET", "/api/v1/metrics", "", user(ACTOR))));
    assertTrue(
        new RbacGate(RbacTestSupport.permitting("server.read"), "server.read")
            .authorize(request("GET", "/api/v1/metrics", "", user(ACTOR)))
            .allowed());
    assertThrows(
        IllegalArgumentException.class,
        () -> new RbacGate(RbacTestSupport.permitAll(), "not-a-permission"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RbacGate(RbacTestSupport.permitAll(), "files.read"));
  }

  @Test
  void tokenOwnerMappingUsesOwnerInsteadOfTokenIdOrLabel() {
    RbacPolicy policy = ownerPolicy();
    RbacGate gate = new RbacGate(new RbacEvaluator(policy, null));
    Identity token = token(OWNER);

    assertTrue(gate.authorize(request("GET", "/api/v1/files", "", token)).allowed());
    assertDenied(gate.authorize(request("GET", "/api/v1/files", "", token(null))));
    assertEquals(Collections.singletonList("reader"), policy.rolesFor(OWNER));
  }

  @Test
  void malformedPathParametersFailClosedInsteadOfSkippingPathScope() {
    RbacPolicy policy = ownerPolicy();
    RbacGate gate = new RbacGate(new RbacEvaluator(policy, new SandboxResolver(sandboxRoot)));
    Identity identity = token(OWNER);

    assertEquals(
        DecisionReason.PATH_DENIED,
        gate.authorize(request("GET", "/api/v1/files", "path=%ZZ", identity)).reason());
    assertEquals(
        DecisionReason.PATH_DENIED,
        gate.authorize(request("GET", "/api/v1/files/%00", "", identity)).reason());
    assertEquals(
        DecisionReason.PATH_DENIED,
        gate.authorize(request("GET", "/api/v1/files", repeat('a', 9000), identity)).reason());
  }

  @Test
  void pathDecodingRejectsMalformedPercentEscapesAndControlBytes() {
    assertNull(RbacGate.decodePath(null));
    assertNull(RbacGate.decodePath(""));
    assertNull(RbacGate.decodePath("%"));
    assertNull(RbacGate.decodePath("docs/%2"));
    assertNull(RbacGate.decodePath("%zz"));
    assertNull(RbacGate.decodePath("%00"));
    assertNull(RbacGate.decodePath("%1f"));
    assertNull(RbacGate.decodePath("%c3%28"));
    assertNull(RbacGate.decodePath(repeat('a', 1025)));
    assertEquals("docs/note.txt", RbacGate.decodePath("docs%2fnote.txt"));
    assertEquals(
        "\u30c9\u30ad\u30e5\u30e1\u30f3\u30c8/README.md",
        RbacGate.decodePath("%E3%83%89%E3%82%AD%E3%83%A5%E3%83%A1%E3%83%B3%E3%83%88/README.md"));
  }

  private static void assertDenied(AuthorizationDecision decision) {
    assertFalse(decision.allowed());
    assertEquals("rbac.denied", decision.errorCode());
  }

  private static HttpRequest request(String method, String path, String query, Identity identity) {
    AuthContext context =
        identity == null
            ? AuthContext.anonymous("request-1")
            : AuthContext.authenticated("request-1", identity);
    return HttpRequest.withContext(
        "request-1",
        method,
        path,
        query,
        Collections.<String, List<String>>emptyMap(),
        new byte[0],
        context);
  }

  private static Identity user(String id) {
    return new Identity(
        id, "Display label", "cloudflare-access", false, Collections.<String>emptyList());
  }

  private static Identity token(String owner) {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("token.id", "token-1");
    claims.put("token.label", "Secret Label");
    if (owner != null) {
      claims.put("token.owner", owner);
    }
    return new Identity(
        "token-1", "Secret Label", "token", false, Collections.<String>emptyList(), claims);
  }

  private static RbacPolicy ownerPolicy() {
    RolePolicy reader =
        new RolePolicy(
            "reader",
            false,
            Collections.singletonList("files.read"),
            PathScope.wholeSandbox(Collections.<String>emptyList()));
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put("reader", reader);
    Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
    users.put(OWNER, Arrays.asList("reader"));
    return RbacPolicy.of(roles, users, "none");
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int index = 0; index < count; index++) {
      result.append(value);
    }
    return result.toString();
  }
}
