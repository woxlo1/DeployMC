package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ProblemDetailsTest {

  @Test
  void serializesRetryableAndFieldLevelIssues() throws Exception {
    HttpResponse response =
        HttpResponse.problem(
            413,
            "validation.limit-exceeded",
            "Validation limit exceeded",
            "The node budget was exhausted.",
            "request-12345678",
            false,
            Collections.singletonList(
                HttpResponse.ProblemIssue.finding(
                    "/config.yml",
                    "validation.yaml-node-limit",
                    "YAML node limit exceeded.",
                    "ERROR",
                    12,
                    3,
                    99L,
                    Collections.singletonList("nodes"))));

    assertEquals(413, response.status());
    assertEquals("application/problem+json; charset=utf-8", header(response, "content-type"));
    YamlValue problem = parse(response);
    assertEquals("urn:deploymc:error:validation.limit-exceeded", problem.get("type").raw());
    assertEquals("Validation limit exceeded", problem.get("title").raw());
    assertEquals(413L, problem.get("status").raw());
    assertEquals("validation.limit-exceeded", problem.get("code").raw());
    assertEquals("request-12345678", problem.get("requestId").raw());
    assertEquals(Boolean.FALSE, problem.get("retryable").raw());

    YamlValue issue = problem.get("errors").items().get(0);
    assertEquals("/config.yml", issue.get("pointer").raw());
    assertEquals("validation.yaml-node-limit", issue.get("code").raw());
    assertEquals("ERROR", issue.get("severity").raw());
    assertEquals(12L, issue.get("line").raw());
    assertEquals(3L, issue.get("column").raw());
    assertEquals(99L, issue.get("offset").raw());
    assertEquals("nodes", issue.get("parameters").items().get(0).raw());
  }

  @Test
  void serializesSimpleProblemAsProblemJson() throws Exception {
    HttpResponse response =
        HttpResponse.problem(
            401, "auth.required", "Authentication required", "Supply a token.", "request-abcdefgh");

    assertEquals("application/problem+json; charset=utf-8", header(response, "content-type"));
    YamlValue problem = parse(response);
    assertEquals(Boolean.FALSE, problem.get("retryable").raw());
    assertTrue(problem.get("errors").items().isEmpty());
    assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("token=secret"));
  }

  private static YamlValue parse(HttpResponse response) throws Exception {
    return ConfigYamlParser.parseSingleDocument(
        new String(response.body(), StandardCharsets.UTF_8));
  }

  private static String header(HttpResponse response, String name) {
    return response.headers().get(name).get(0);
  }
}
