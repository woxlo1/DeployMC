package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AuthContextTest {

  @Test
  void carriesIdentityExplicitlyAcrossExecutorThreads() throws Exception {
    Identity identity = new Identity("worker-id", "worker", "token", false, null);
    final AuthContext context = AuthContext.authenticated("req-async", identity);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<String> result =
          executor.submit(
              new Callable<String>() {
                @Override
                public String call() {
                  return context.identity() == null ? "none" : context.identity().id();
                }
              });
      assertEquals("worker-id", result.get());
      assertEquals("req-async", context.requestId());
      assertTrue(context.authenticated());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void anonymousContextHasNoIdentity() {
    AuthContext context = AuthContext.anonymous("req-probe");
    assertNull(context.identity());
    assertFalse(context.authenticated());
    assertTrue(context.attributes().isEmpty());
  }

  @Test
  void attributesAreImmutableAndBounded() {
    AuthContext context =
        AuthContext.authenticated("req-1", new Identity("id", "label", "token", false, null))
            .withAttribute("endpoint", "files");
    assertEquals("files", context.attributes().get("endpoint"));
    assertThrows(
        UnsupportedOperationException.class, () -> context.attributes().put("other", "value"));

    StringBuilder tooLong = new StringBuilder();
    for (int index = 0; index < 1025; index++) {
      tooLong.append('x');
    }
    assertThrows(
        IllegalArgumentException.class, () -> context.withAttribute("large", tooLong.toString()));
    assertFalse(context.toString().contains("files"));
  }

  @Test
  void rejectsNullIdentityForAuthenticatedContext() {
    assertThrows(NullPointerException.class, () -> AuthContext.authenticated("req-1", null));
    assertThrows(
        NullPointerException.class,
        () -> AuthContext.of("req-1", null, Collections.<String, String>emptyMap()));
  }
}
