package io.github.woxlo1.deploymc.core.snapshot;

import java.util.Collections;
import java.util.List;

/** Counts and stable issues from one verify pass. */
public final class VerifyReport {

  private final int metadataFiles;
  private final int indexFiles;
  private final int blobs;
  private final int stagingFiles;
  private final List<VerifyIssue> issues;

  public VerifyReport(
      int metadataFiles, int indexFiles, int blobs, int stagingFiles, List<VerifyIssue> issues) {
    this.metadataFiles = metadataFiles;
    this.indexFiles = indexFiles;
    this.blobs = blobs;
    this.stagingFiles = stagingFiles;
    this.issues = Collections.unmodifiableList(issues);
  }

  public int metadataFiles() {
    return metadataFiles;
  }

  public int indexFiles() {
    return indexFiles;
  }

  public int blobs() {
    return blobs;
  }

  public int stagingFiles() {
    return stagingFiles;
  }

  public List<VerifyIssue> issues() {
    return issues;
  }

  public boolean hasIssues() {
    return !issues.isEmpty();
  }
}
