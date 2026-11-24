package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AuthorizationDeniedExceptionTest {

  @Test
  void carriesNoDetailThatCouldLeakIdentityOrPath() {
    AuthorizationDeniedException exception = new AuthorizationDeniedException();
    assertEquals("rbac.denied", exception.code());
    assertNull(exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void acceptsOnlyNonEmptyStableCodes() {
    assertEquals("files.denied", new AuthorizationDeniedException("files.denied").code());
    assertThrows(IllegalArgumentException.class, () -> new AuthorizationDeniedException(" "));
  }
}
