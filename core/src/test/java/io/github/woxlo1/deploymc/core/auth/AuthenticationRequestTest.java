package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationRequestTest {

  @Test
  void normalizesHeaderNamesAndCopiesValues() {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    List<String> values = new ArrayList<String>(Arrays.asList("Bearer secret-value-1234567890"));
    headers.put("Authorization", values);
    AuthenticationRequest request =
        new AuthenticationRequest("req-1", "get", "/api/v1/server", headers);

    values.set(0, "tampered");
    assertEquals("Bearer secret-value-1234567890", request.header("authorization"));
    assertEquals("Bearer secret-value-1234567890", request.header("AUTHORIZATION"));
    assertThrows(
        UnsupportedOperationException.class, () -> request.headers().get("authorization").add("x"));
  }

  @Test
  void neverPrintsHeaderValues() {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("Authorization", Collections.singletonList("Bearer dmc_secret_value_1234567890"));
    AuthenticationRequest request =
        new AuthenticationRequest("req-1", "GET", "/api/v1/server", headers);

    assertFalse(request.toString().contains("dmc_secret_value_1234567890"));
    assertTrue(request.toString().contains("headerCount=1"));
  }

  @Test
  void rejectsMalformedRequestData() {
    Map<String, List<String>> headers = Collections.<String, List<String>>emptyMap();
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthenticationRequest("", "GET", "/api/v1/server", headers));
    assertThrows(
        NullPointerException.class,
        () -> new AuthenticationRequest("req-1", "GET", "/api/v1/server", null));
    Map<String, List<String>> tooMany = new LinkedHashMap<String, List<String>>();
    for (int index = 0; index < 65; index++) {
      tooMany.put("x-" + index, Collections.singletonList("value"));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthenticationRequest("req-1", "GET", "/api/v1/server", tooMany));
  }
}
