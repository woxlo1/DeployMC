package io.github.woxlo1.deploymc.core.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Records read from all retained files plus bounded format defects and warnings. */
public final class AuditReadResult {

  private final int formatVersion;
  private final List<AuditRecord> records;
  private final List<AuditFileInfo> files;
  private final List<AuditVerificationWarning> warnings;
  private final List<AuditVerificationFailure> failures;

  public AuditReadResult(
      int formatVersion,
      List<AuditRecord> records,
      List<AuditFileInfo> files,
      List<AuditVerificationWarning> warnings,
      List<AuditVerificationFailure> failures) {
    this.formatVersion = formatVersion;
    this.records = Collections.unmodifiableList(new ArrayList<AuditRecord>(records));
    this.files = Collections.unmodifiableList(new ArrayList<AuditFileInfo>(files));
    this.warnings = Collections.unmodifiableList(new ArrayList<AuditVerificationWarning>(warnings));
    this.failures = Collections.unmodifiableList(new ArrayList<AuditVerificationFailure>(failures));
  }

  public int formatVersion() {
    return formatVersion;
  }

  public List<AuditRecord> records() {
    return records;
  }

  public List<AuditFileInfo> files() {
    return files;
  }

  public List<AuditVerificationWarning> warnings() {
    return warnings;
  }

  public List<AuditVerificationFailure> failures() {
    return failures;
  }
}
