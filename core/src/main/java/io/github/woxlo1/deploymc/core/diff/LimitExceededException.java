package io.github.woxlo1.deploymc.core.diff;

/** Internal signal that a configured diff limit was exceeded. */
final class LimitExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String limit;
  private final long observed;

  LimitExceededException(String limit, long observed) {
    super(limit);
    this.limit = limit;
    this.observed = observed;
  }

  String limit() {
    return limit;
  }

  long observed() {
    return observed;
  }
}
