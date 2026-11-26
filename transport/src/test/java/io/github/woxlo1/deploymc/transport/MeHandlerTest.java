package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.AuthContext;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.rbac.PathScope;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import io.github.woxlo1.deploymc.core.rbac.RbacPolicy;
import io.github.woxlo1.deploymc.core.rbac.RolePolicy;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free {@code /api/v1/me} handler tests with bounded, redacted fixtures. */
class MeHandlerTest {

  private static final String OWNER = "owner-1";
  private static final String TOKEN_ID = "token-1";
  private static final String SECRET = "raw-secret-value-do-not-return";
  private static final String DIGEST = "digest-value-do-not-return";
  private static final String LABEL = "Secret Display Name";
  private static final String ALLOW_GLOB = "docs/private/**";
  private static final String DENY_GLOB = "docs/secrets.yml";

  @TempDir Path sandboxRoot;

  @Test
  void mePayloadIsRedactedAndNeverContainsRawTokenOrPathData() throws Exception {
    Identity identity = token(OWNER, "files.read,server.read");

    HttpResponse response = get("", identity);
    String body = body(response);
    YamlValue me = parse(response);

    assertEquals(200, response.status());
    assertEquals("1.0", me.get("schemaVersion").raw());
    assertEquals(TOKEN_ID, me.get("identity").get("id").raw());
    assertEquals("token", me.get("identity").get("kind").raw());
    assertEquals(Arrays.asList("server.read", "files.read"), strings(me.get("permissions")));
    assertFalse(me.keys().contains("explain"));
    assertFalse(body.contains(SECRET));
    assertFalse(body.contains(DIGEST));
    assertFalse(body.contains(LABEL));
    assertFalse(body.contains(ALLOW_GLOB));
    assertFalse(body.contains(DENY_GLOB));
    assertFalse(body.contains("files.read,server.read"));
    assertFalse(body.contains("token.scopes"));
  }

  @Test
  void rolesAreSortedAndPermissionsFollowCanonicalRegistryOrder() throws Exception {
    HttpResponse response = get("", token(OWNER, null));
    YamlValue me = parse(response);

    assertEquals(Arrays.asList("alpha", "zeta"), strings(me.get("roles")));
    assertEquals(
        Arrays.asList("server.read", "files.read", "files.write"), strings(me.get("permissions")));
  }

  @Test
  void explainDenyCarriesStableReasonAndBooleanDiagnosticsOnly() throws Exception {
    HttpResponse response = get("explain=files.read&path=docs/secrets.yml", token(OWNER, null));
    String body = body(response);
    YamlValue explain = parse(response).get("explain");

    assertEquals(200, response.status());
    assertEquals("files.read", explain.get("permission").raw());
    assertEquals("deny", explain.get("decision").raw());
    assertEquals("PATH_DENIED", explain.get("reason").raw());
    assertTrue((Boolean) explain.get("path").get("requested").raw());
    assertTrue((Boolean) explain.get("path").get("resolved").raw());
    assertFalse((Boolean) explain.get("path").get("withinScope").raw());
    assertEquals(2L, explain.get("roles").get("assigned").raw());
    assertFalse(body.contains(DENY_GLOB));
    assertFalse(body.contains(SECRET));

    HttpResponse scopeDenied = get("explain=files.read&path=other/note.txt", token(OWNER, null));
    assertEquals("PATH_SCOPE", parse(scopeDenied).get("explain").get("reason").raw());
  }

  @Test
  void explainAllowHasNullReasonRatherThanAnInventedDenyReason() throws Exception {
    HttpResponse response = get("explain=files.read", token(OWNER, null));
    YamlValue explain = parse(response).get("explain");

    assertEquals("allow", explain.get("decision").raw());
    assertTrue(explain.get("reason").isNull());
    assertFalse((Boolean) explain.get("path").get("requested").raw());
  }

  @Test
  void tokenScopesNarrowPayloadAndExplainReason() throws Exception {
    Identity narrowed = token(OWNER, "files.read");

    HttpResponse response = get("explain=files.write", narrowed);
    YamlValue me = parse(response);
    YamlValue explain = me.get("explain");

    assertEquals(Collections.singletonList("files.read"), strings(me.get("permissions")));
    assertEquals("deny", explain.get("decision").raw());
    assertEquals("TOKEN_SCOPE_EXCEEDED", explain.get("reason").raw());
    assertTrue((Boolean) explain.get("token").get("present").raw());
    assertFalse((Boolean) explain.get("token").get("containsPermission").raw());
    assertFalse(body(response).contains("files.write,files.read"));
  }

  @Test
  void invalidExplainAndPathParametersReturnProblemDetails() throws Exception {
    Identity identity = token(OWNER, null);
    String[] invalidQueries = {
      "explain=not-a-permission",
      "path=docs/note.txt",
      "explain=files.read&path=",
      "explain=files.read&path=%ZZ",
      "explain=files.read&path=%00",
      "explain=files.read&path=%c3%28",
      "explain=files.read&path=" + repeat('a', 9000)
    };
    for (String query : invalidQueries) {
      HttpResponse response = get(query, identity);
      assertEquals(400, response.status(), query);
      YamlValue problem = parse(response);
      assertEquals("request.invalid", problem.get("code").raw(), query);
      assertEquals("request-1", problem.get("requestId").raw(), query);
      assertFalse(body(response).contains(SECRET), query);
    }
  }

  @Test
  void methodPathAndIdentityChecksRemainClosed() throws Exception {
    assertEquals(404, get("", token(OWNER, null), "/api/v1/not-me").status());

    HttpResponse method = call("POST", "", token(OWNER, null), MeHandler.PATH);
    assertEquals(405, method.status());
    assertEquals(Collections.singletonList("GET"), method.headers().get("allow"));
    assertEquals("request.method-not-allowed", parse(method).get("code").raw());

    HttpResponse anonymous = get("", null);
    assertEquals(401, anonymous.status());
    assertEquals("auth.required", parse(anonymous).get("code").raw());
    assertTrue(parse(anonymous).get("errors").items().isEmpty());
  }

  private HttpResponse get(String query, Identity identity) {
    return get(query, identity, MeHandler.PATH);
  }

  private HttpResponse get(String query, Identity identity, String path) {
    return call("GET", query, identity, path);
  }

  private HttpResponse call(String method, String query, Identity identity, String path) {
    AuthContext context =
        identity == null
            ? AuthContext.anonymous("request-1")
            : AuthContext.authenticated("request-1", identity);
    HttpRequest request =
        HttpRequest.withContext(
            "request-1",
            method,
            path,
            query,
            Collections.<String, List<String>>emptyMap(),
            new byte[0],
            context);
    return new MeHandler(evaluator(), TransportMessages.of(Locale.ENGLISH)).handle(request);
  }

  private RbacEvaluator evaluator() {
    return new RbacEvaluator(policy(), new SandboxResolver(sandboxRoot));
  }

  private static RbacPolicy policy() {
    Map<String, RolePolicy> roles = new LinkedHashMap<String, RolePolicy>();
    roles.put(
        "zeta",
        new RolePolicy(
            "zeta",
            false,
            Arrays.asList("files.write", "files.read"),
            PathScope.restricted(
                Collections.singletonList(ALLOW_GLOB), Collections.singletonList(DENY_GLOB))));
    roles.put(
        "alpha",
        new RolePolicy(
            "alpha",
            false,
            Collections.singletonList("server.read"),
            PathScope.wholeSandbox(Collections.<String>emptyList())));
    Map<String, List<String>> users = new LinkedHashMap<String, List<String>>();
    users.put(OWNER, Arrays.asList("zeta", "alpha"));
    return RbacPolicy.of(roles, users, "none");
  }

  private static Identity token(String owner, String scopes) {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("token.id", TOKEN_ID);
    claims.put("token.label", LABEL);
    claims.put("token.secret", SECRET);
    claims.put("token.digest", DIGEST);
    if (owner != null) {
      claims.put("token.owner", owner);
    }
    if (scopes != null) {
      claims.put("token.scopes", scopes);
    }
    return new Identity(TOKEN_ID, LABEL, "token", false, Collections.<String>emptyList(), claims);
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int index = 0; index < count; index++) {
      result.append(value);
    }
    return result.toString();
  }

  private static List<String> strings(YamlValue array) {
    List<String> values = new ArrayList<String>();
    for (YamlValue item : array.items()) {
      values.add(String.valueOf(item.raw()));
    }
    return values;
  }

  private static YamlValue parse(HttpResponse response) throws Exception {
    return ConfigYamlParser.parseSingleDocument(body(response));
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
