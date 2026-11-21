package io.github.woxlo1.deploymc.core.snapshot;

/** Localized diagnostic details used inside snapshot-store exceptions. */
enum SnapshotStoreDetail {
  DEPLOYMENT_ID_EMPTY("snapshots.detail.deploymentIdEmpty"),
  RESTORE_ACTOR_EMPTY("snapshots.detail.restoreActorEmpty"),
  DATA_ROOT_NOT_DIRECTORY("snapshots.detail.dataRootNotDirectory"),
  DATA_DIRECTORY_LOCKED("snapshots.detail.dataDirectoryLocked"),
  FORMAT_MISSING_NONEMPTY("snapshots.detail.formatMissingNonEmpty"),
  FORMAT_UNSUPPORTED("snapshots.detail.formatUnsupported"),
  STAGING_REMOVE_FAILED("snapshots.detail.stagingRemoveFailed"),
  STORE_BUSY("snapshots.detail.storeBusy"),
  STORE_LOCK_INTERRUPTED("snapshots.detail.storeLockInterrupted"),
  STORE_CLOSED("snapshots.detail.storeClosed"),
  SNAPSHOT_REASON_UNSUPPORTED("snapshots.detail.snapshotReasonUnsupported"),
  CAPTURE_NO_ENTRIES("snapshots.detail.captureNoEntries"),
  CAPTURE_DUPLICATE_PATH("snapshots.detail.captureDuplicatePath"),
  REVISION_MISMATCH("snapshots.detail.revisionMismatch"),
  ACTIVE_DEPLOYMENT("snapshots.detail.activeDeployment"),
  RESTORE_TARGET_NOT_FILE("snapshots.detail.restoreTargetNotFile"),
  RESTORE_CONTENT_CHANGED("snapshots.detail.restoreContentChanged"),
  BLOB_METADATA_MISMATCH("snapshots.detail.blobMetadataMismatch"),
  INDEX_INVALID_PATH("snapshots.detail.indexInvalidPath"),
  INDEX_PATH_MISMATCH("snapshots.detail.indexPathMismatch"),
  INDEX_DUPLICATE_ID("snapshots.detail.indexDuplicateId"),
  INDEX_UNREADABLE_ID("snapshots.detail.indexUnreadableId"),
  INDEX_FOREIGN_ID("snapshots.detail.indexForeignId"),
  INDEX_ORDER_UNSTABLE("snapshots.detail.indexOrderUnstable"),
  INDEX_INVALID("snapshots.detail.indexInvalid"),
  INDEX_HASH_MISMATCH("snapshots.detail.indexHashMismatch"),
  METADATA_INVALID("snapshots.detail.metadataInvalid"),
  METADATA_PATH_UNNORMALIZED("snapshots.detail.metadataPathUnnormalized"),
  METADATA_PATH_REJECTED("snapshots.detail.metadataPathRejected"),
  BLOB_MISSING("snapshots.detail.blobMissing"),
  BLOB_HASH_MISMATCH("snapshots.detail.blobHashMismatch"),
  BLOB_PATH_INVALID("snapshots.detail.blobPathInvalid"),
  BLOB_NAME_INVALID("snapshots.detail.blobNameInvalid"),
  ROOT_MISMATCH("snapshots.detail.rootMismatch"),
  ROOT_INVALID("snapshots.detail.rootInvalid"),
  FILE_MISSING("snapshots.detail.fileMissing"),
  PATH_EMPTY("snapshots.detail.pathEmpty"),
  PATH_SEPARATOR_ESCAPE("snapshots.detail.pathSeparatorEscape"),
  PATH_ABSOLUTE("snapshots.detail.pathAbsolute"),
  PATH_DOT_SEGMENT("snapshots.detail.pathDotSegment"),
  PATH_OUTSIDE_ROOT("snapshots.detail.pathOutsideRoot"),
  PATH_LINK("snapshots.detail.pathLink"),
  PATH_UNSUPPORTED_TYPE("snapshots.detail.pathUnsupportedType"),
  PATH_NO_PARENT("snapshots.detail.pathNoParent"),
  PATH_SANDBOX_ROOT("snapshots.detail.pathSandboxRoot"),
  PATH_SANDBOX_REJECTED("snapshots.detail.pathSandboxRejected");

  private final String messageKey;

  SnapshotStoreDetail(String messageKey) {
    this.messageKey = messageKey;
  }

  String messageKey() {
    return messageKey;
  }
}
