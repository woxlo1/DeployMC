package io.github.woxlo1.deploymc.core.files;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Test double that records snapshot captures without writing to disk. */
final class RecordingSnapshotHook implements SnapshotHook {

  private final List<SnapshotRequest> requests = new ArrayList<SnapshotRequest>();
  private IOException failure;

  @Override
  public String capture(SnapshotRequest request) throws IOException {
    if (failure != null) {
      throw failure;
    }
    requests.add(request);
    return "snapshot-" + requests.size();
  }

  void failWith(IOException failure) {
    this.failure = failure;
  }

  List<SnapshotRequest> requests() {
    return requests;
  }

  boolean empty() {
    return requests.isEmpty();
  }
}
