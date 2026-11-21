package io.github.woxlo1.deploymc.core.files;

import io.github.woxlo1.deploymc.core.validation.ValidationResult;
import java.util.Objects;

/** A committed file plus the host gate result. The validation is null when no gate is wired. */
public final class ValidatedFileResult {

  private final FileInfo file;
  private final ValidationResult validation;

  public ValidatedFileResult(FileInfo file, ValidationResult validation) {
    this.file = Objects.requireNonNull(file, "file");
    this.validation = validation;
  }

  public FileInfo file() {
    return file;
  }

  /** Full validation result, including warnings. Null means no validation gate was configured. */
  public ValidationResult validation() {
    return validation;
  }
}
