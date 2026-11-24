package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthenticationFilterTest {

  @Test
  void failsClosedWithoutAuthenticatorOrRequest() {
    AuthenticationFilter filter = new AuthenticationFilter(null);
    assertEquals(
        AuthenticationFailure.REQUIRED,
        filter.filter(TestRequests.bearer("dmc_any_token_value_1234")).failure());
    AuthenticationFilter configured =
        new AuthenticationFilter(
            request -> {
              throw new AssertionError("the authenticator must not be called for a null request");
            });
    assertEquals(AuthenticationFailure.REQUIRED, configured.filter(null).failure());
  }

  @Test
  void passesSuccessfulResultAndBuildsExplicitContext() {
    Identity identity = new Identity("id", "label", "token", false, null);
    AuthenticationFilter filter =
        new AuthenticationFilter(request -> AuthenticationResult.authenticated(identity));
    AuthenticationResult result = filter.filter(TestRequests.bearer("dmc_any_token_value_1234"));

    assertTrue(result.authenticated());
    assertEquals(identity, result.identity());
    AuthContext context = filter.context("req-1", result);
    assertTrue(context.authenticated());
    assertEquals(identity, context.identity());
  }

  @Test
  void mapsNullAndThrowingAuthenticatorsToPublicFailureWithoutDetail() {
    AuthenticationFilter nullResult = new AuthenticationFilter(request -> null);
    assertEquals(
        AuthenticationFailure.INVALID,
        nullResult.filter(TestRequests.bearer("dmc_any_token_value_1234")).failure());

    AuthenticationFilter throwing =
        new AuthenticationFilter(
            request -> {
              throw new IllegalStateException("dmc_secret_value_1234567890");
            });
    AuthenticationResult result = throwing.filter(TestRequests.bearer("dmc_any_token_value_1234"));
    assertEquals(AuthenticationFailure.INVALID, result.failure());
    assertEquals("auth.invalid", result.code());
    assertFalse(String.valueOf(result).contains("dmc_secret_value_1234567890"));
  }

  @Test
  void anonymousContextIsUsedForFailedResults() {
    AuthenticationFilter filter =
        new AuthenticationFilter(
            request -> AuthenticationResult.failed(AuthenticationFailure.EXPIRED));
    AuthContext context = filter.context("req-1", filter.filter(TestRequests.bearer("unused")));

    assertFalse(context.authenticated());
    assertEquals(
        AuthenticationFailure.EXPIRED, filter.filter(TestRequests.bearer("unused")).failure());
  }
}
