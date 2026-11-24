package io.github.woxlo1.deploymc.core.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestRequests {

  private TestRequests() {}

  static AuthenticationRequest bearer(String value) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("Authorization", Collections.singletonList("Bearer " + value));
    return new AuthenticationRequest("test-request", "GET", "/api/v1/server", headers);
  }

  static AuthenticationRequest header(String name, String value) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put(name, Collections.singletonList(value));
    return new AuthenticationRequest("test-request", "GET", "/api/v1/server", headers);
  }
}
