package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityTest {

  @Test
  void exposesBoundedImmutableClaimSubset() {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    claims.put("sub", "user-1");
    claims.put("email", "user@example.invalid");
    Identity identity =
        new Identity("id-1", "User One", "cloudflare", false, Collections.singleton("dev"), claims);

    assertEquals("User One", identity.displayName());
    assertEquals("user-1", identity.claims().get("sub"));
    assertEquals("email", identity.claims().keySet().iterator().next());
    assertEquals(Collections.singleton("dev"), identity.groups());
    assertThrows(UnsupportedOperationException.class, () -> identity.claims().put("role", "admin"));
    assertThrows(UnsupportedOperationException.class, () -> identity.groups().add("admin"));
    assertFalse(identity.toString().contains("user@example.invalid"));
  }

  @Test
  void defaultConstructorHasEmptyClaims() {
    Identity identity = new Identity("id-1", "label", "token", true, null);
    assertTrue(identity.claims().isEmpty());
    assertTrue(identity.groups().isEmpty());
  }

  @Test
  void rejectsOversizedClaims() {
    Map<String, String> claims = new LinkedHashMap<String, String>();
    for (int index = 0; index < 33; index++) {
      claims.put("claim-" + index, "value");
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new Identity("id-1", "label", "token", true, null, claims));
    assertThrows(
        NullPointerException.class, () -> new Identity("id-1", "label", "token", true, null, null));
  }
}
