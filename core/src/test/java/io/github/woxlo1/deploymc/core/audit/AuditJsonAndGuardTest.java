package io.github.woxlo1.deploymc.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditJsonAndGuardTest {

  @Test
  void canonicalJsonSortsKeysAndUsesOnlyIntegers() {
    String source = "{ \"b\" : 2, \"a\" : [true, null, -3], \"nested\": { \"z\": 1, \"y\": 0 } }";

    String canonical = AuditJson.canonical(AuditJson.parse(source));

    assertEquals("{\"a\":[true,null,-3],\"b\":2,\"nested\":{\"y\":0,\"z\":1}}", canonical);
  }

  @Test
  void hashUsesSha256PrefixAndStableCanonicalBytes() {
    String canonical = AuditJson.canonical(AuditJson.parse("{\"b\":2,\"a\":1}"));

    assertEquals(
        "sha256:43258cff783fe7036d8a43033f830adfc60ec037382473548ac742b888292777",
        AuditHash.hash(canonical));
    assertTrue(AuditHash.valid(AuditHash.hash(canonical)));
  }

  @Test
  void parserRejectsDecimalsExponentsDuplicatesAndUnpairedSurrogates() {
    assertThrows(IllegalArgumentException.class, () -> AuditJson.parse("{\"a\":1.5}"));
    assertThrows(IllegalArgumentException.class, () -> AuditJson.parse("{\"a\":1e3}"));
    assertThrows(IllegalArgumentException.class, () -> AuditJson.parse("{\"a\":1,\"a\":2}"));
    assertThrows(IllegalArgumentException.class, () -> AuditJson.parse("{\"a\":\"\\ud800\"}"));
  }

  @Test
  void redactionGuardRejectsSensitiveNamesAndValues() {
    Map<String, Object> tokenName = new LinkedHashMap<String, Object>();
    tokenName.put("auth.token", "fake");
    assertThrows(IllegalArgumentException.class, () -> AuditDetails.validate(tokenName));

    Map<String, Object> bodyName = new LinkedHashMap<String, Object>();
    bodyName.put("request-body", "fake");
    assertThrows(IllegalArgumentException.class, () -> AuditDetails.validate(bodyName));

    Map<String, Object> bearer = new LinkedHashMap<String, Object>();
    bearer.put("note", "Bearer fake-token-value");
    assertThrows(IllegalArgumentException.class, () -> AuditDetails.validate(bearer));

    Map<String, Object> jwt = new LinkedHashMap<String, Object>();
    jwt.put("note", "eyJhbGciOiJSUzI1NiJ9.fake.fake");
    assertThrows(IllegalArgumentException.class, () -> AuditDetails.validate(jwt));
  }

  @Test
  void redactionGuardAcceptsBoundedOperationalMetadata() {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("bytes", Long.valueOf(2048L));
    details.put("snapshot", "snap_01J00000000000000000000001");
    details.put("attempts", Long.valueOf(2L));

    Map<String, Object> validated = AuditDetails.validate(details);

    assertEquals(3, validated.size());
    assertEquals(Long.valueOf(2048L), validated.get("bytes"));
  }
}
