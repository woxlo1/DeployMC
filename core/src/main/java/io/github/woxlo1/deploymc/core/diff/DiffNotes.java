package io.github.woxlo1.deploymc.core.diff;

/** Stable informational note codes. */
final class DiffNotes {

  static final String BOM = "diff.bom";
  static final String DESCRIPTOR_INVALID = "diff.descriptor-invalid";
  static final String DESCRIPTOR_MISSING = "diff.descriptor-missing";
  static final String DESCRIPTOR_DUPLICATE = "diff.descriptor-duplicate";
  static final String ENTRY_LIST_TRUNCATED = "diff.entry-list-truncated";
  static final String LINE_ENDING = "diff.line-ending";
  static final String PARSE_FALLBACK = "diff.parse-fallback";
  static final String ZIP64_UNSUPPORTED = "diff.zip64-unsupported";
  static final String ZIP_ENCRYPTED = "diff.zip-encrypted";
  static final String ZIP_TRUNCATED = "diff.zip-truncated";

  private DiffNotes() {}
}
