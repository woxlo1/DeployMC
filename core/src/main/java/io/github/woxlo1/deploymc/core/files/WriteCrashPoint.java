package io.github.woxlo1.deploymc.core.files;

/**
 * Deterministic process-termination points around the single atomic commit.
 *
 * <p>This is test infrastructure, not a production setting. Throwing here has the same durability
 * consequence as a process kill at that point: the temporary file is either discarded or already
 * committed.
 */
interface WriteCrashPoint {

  WriteCrashPoint NONE =
      new WriteCrashPoint() {
        @Override
        public void beforeCommit() {}

        @Override
        public void afterCommit() {}
      };

  void beforeCommit();

  void afterCommit();
}
