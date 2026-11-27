package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectivePeerResolverTest {

  @Test
  void usesSocketPeerWhenPeerIsNotTrusted() {
    EffectivePeerResolver resolver = new EffectivePeerResolver(Collections.<String>emptyList());

    assertEquals(
        "203.0.113.10",
        resolver.resolve(
            "203.0.113.10", headers("x-forwarded-for", Arrays.asList("198.51.100.7"))));
  }

  @Test
  void trustsLoopbackTunnelTerminatorWithoutConfiguration() {
    EffectivePeerResolver resolver = new EffectivePeerResolver(Collections.<String>emptyList());

    assertEquals(
        "198.51.100.7", resolver.resolve("127.0.0.1", headers("cf-connecting-ip", "198.51.100.7")));
    assertEquals(
        "198.51.100.7", resolver.resolve("[::1]", headers("x-forwarded-for", "198.51.100.7")));
  }

  @Test
  void trustsConfiguredLiteralCidrAndHonoursHeaderPrecedence() {
    EffectivePeerResolver resolver =
        new EffectivePeerResolver(Collections.singletonList("10.0.0.0/8"));
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put("cf-connecting-ip", Collections.singletonList("192.0.2.99"));
    headers.put("x-forwarded-for", Collections.singletonList("198.51.100.7"));
    headers.put("forwarded", Collections.singletonList("for=203.0.113.5;proto=https"));

    assertEquals("203.0.113.5", resolver.resolve("10.1.2.3", headers));
  }

  @Test
  void parsesLeftmostChainEntryAndRejectsOversizedChain() {
    EffectivePeerResolver resolver = new EffectivePeerResolver(Collections.<String>emptyList());
    assertEquals(
        "198.51.100.7",
        resolver.resolve("127.0.0.1", headers("x-forwarded-for", "198.51.100.7, 203.0.113.5")));
    StringBuilder chain = new StringBuilder();
    for (int index = 0; index < 17; index++) {
      if (index > 0) {
        chain.append(", ");
      }
      chain.append("198.51.100.7");
    }
    assertEquals(
        "127.0.0.1", resolver.resolve("127.0.0.1", headers("x-forwarded-for", chain.toString())));
  }

  @Test
  void malformedForwardedValuesFallBackToSocketPeer() {
    EffectivePeerResolver resolver = new EffectivePeerResolver(Collections.<String>emptyList());

    assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", headers("forwarded", "for=_hidden")));
    assertEquals(
        "127.0.0.1",
        resolver.resolve("127.0.0.1", headers("x-forwarded-for", "proxy.example.test")));
  }

  @Test
  void refusesHostnamesAndWildcardsInConfiguration() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EffectivePeerResolver(Collections.singletonList("proxy.example.test")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new EffectivePeerResolver(Collections.singletonList("10.0.0.*")));
  }

  private static Map<String, List<String>> headers(String name, String value) {
    return headers(name, Collections.singletonList(value));
  }

  private static Map<String, List<String>> headers(String name, List<String> values) {
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    headers.put(name, values);
    return headers;
  }
}
