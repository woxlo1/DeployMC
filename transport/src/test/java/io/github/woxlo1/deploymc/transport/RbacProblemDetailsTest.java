package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.rbac.DecisionReason;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Server-free coverage of the five stable reasons, their codes, and localized Problem Details. */
class RbacProblemDetailsTest {

  @Test
  void allFiveReasonsMapTo403ProblemDetailsWithoutDecisionDetail() throws Exception {
    Map<DecisionReason, String> expected = new LinkedHashMap<DecisionReason, String>();
    expected.put(DecisionReason.NO_ROLE, "rbac.denied");
    expected.put(DecisionReason.NOT_GRANTED, "rbac.denied");
    expected.put(DecisionReason.TOKEN_SCOPE_EXCEEDED, "rbac.token-scope-exceeded");
    expected.put(DecisionReason.PATH_DENIED, "rbac.path-denied");
    expected.put(DecisionReason.PATH_SCOPE, "rbac.path-denied");

    for (Map.Entry<DecisionReason, String> entry : expected.entrySet()) {
      HttpResponse response =
          TransportProblems.authorizationDenied(
              "request-1", entry.getKey().problemCode(), TransportMessages.of(Locale.ENGLISH));
      String body = new String(response.body(), StandardCharsets.UTF_8);
      YamlValue problem = ConfigYamlParser.parseSingleDocument(body);

      assertEquals(5, DecisionReason.values().length);
      assertEquals(entry.getValue(), entry.getKey().problemCode());
      assertEquals(403, response.status());
      assertEquals("urn:deploymc:error:" + entry.getValue(), problem.get("type").raw());
      assertEquals(entry.getValue(), problem.get("code").raw());
      assertEquals(403L, problem.get("status").raw());
      assertEquals("request-1", problem.get("requestId").raw());
      assertEquals(Boolean.FALSE, problem.get("retryable").raw());
      assertFalse(body.contains(entry.getKey().name()));
      assertFalse(body.contains("files.read"));
      assertFalse(body.contains("owner-1"));
      assertTrue(problem.get("errors").items().isEmpty());
    }
  }

  @Test
  void unknownAndMissingDecisionCodesFallBackToRbacDenied() throws Exception {
    for (String code : new String[] {null, "unexpected.code"}) {
      HttpResponse response =
          TransportProblems.authorizationDenied(
              "request-1", code, TransportMessages.of(Locale.ENGLISH));
      YamlValue problem = parse(response);

      assertEquals("rbac.denied", problem.get("code").raw());
      assertEquals(403, response.status());
    }
  }

  @Test
  void rbacProblemStringsRemainSynchronizedAcrossEnglishAndJapaneseCatalogs() {
    String[] keys = {
      MessageKeys.AUTH_DENIED_TITLE,
      MessageKeys.AUTH_DENIED_DETAIL,
      MessageKeys.RBAC_TOKEN_SCOPE_EXCEEDED_TITLE,
      MessageKeys.RBAC_TOKEN_SCOPE_EXCEEDED_DETAIL,
      MessageKeys.RBAC_PATH_DENIED_TITLE,
      MessageKeys.RBAC_PATH_DENIED_DETAIL
    };
    TransportMessages english = TransportMessages.of(Locale.ENGLISH);
    TransportMessages japanese = TransportMessages.of(Locale.JAPANESE);
    for (String key : keys) {
      assertFalse(english.format(key).startsWith("missing catalog key"), key);
      assertFalse(japanese.format(key).startsWith("missing catalog key"), key);
    }
  }

  private static YamlValue parse(HttpResponse response) throws Exception {
    return ConfigYamlParser.parseSingleDocument(
        new String(response.body(), StandardCharsets.UTF_8));
  }
}
