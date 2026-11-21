package io.github.woxlo1.deploymc.core.validation;

/** A pure, bounded validator for one format. It never touches the file system. */
public interface ContentValidator {

  String id();

  ValidationResult validate(String relativePath, byte[] content, ValidationLimits limits)
      throws ValidationException;
}
