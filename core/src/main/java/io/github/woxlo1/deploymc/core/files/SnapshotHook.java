package io.github.woxlo1.deploymc.core.files;

import java.io.IOException;

/**
 * Boundary for the snapshot store implemented in WP-2.3.
 *
 * <p>Destructive operations in {@link FileOperationsService} fail closed when no hook is supplied.
 * The hook must finish successfully before any overwrite, move, or delete is attempted.
 */
public interface SnapshotHook {

  /** Records the captured entries and returns the host-defined snapshot identifier. */
  String capture(SnapshotRequest request) throws IOException;
}
