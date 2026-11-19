package io.github.woxlo1.deploymc.core.auth;

import java.util.Objects;

/** One-time output of token creation. The secret is never stored in this object's string form. */
public final class CreatedToken {

  private final TokenRecord record;
  private final String token;

  public CreatedToken(TokenRecord record, String token) {
    this.record = Objects.requireNonNull(record, "record");
    this.token = Objects.requireNonNull(token, "token");
  }

  public TokenRecord record() {
    return record;
  }

  /** The secret to show to the administrator exactly once. */
  public String token() {
    return token;
  }

  @Override
  public String toString() {
    return "CreatedToken[id=" + record.id() + "]";
  }
}
