package io.github.woxlo1.deploymc.core.diff;

/** Shared work counter for all Myers computations in one diff request. */
final class WorkBudget {

  private final int maximum;
  private int used;

  WorkBudget(int maximum) {
    this.maximum = maximum;
  }

  void consume(int amount) {
    long next = (long) used + amount;
    if (next > maximum) {
      throw new LimitExceededException("edit-operations", next);
    }
    used = (int) next;
  }

  int used() {
    return used;
  }
}
