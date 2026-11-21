package io.github.woxlo1.deploymc.core.validation;

/** Save-time validation boundary. A missing gate is a host decision, never a request option. */
public interface ValidationGate {

  ValidationResult validate(String relativePath, byte[] content) throws ValidationException;

  /**
   * Host byte budget used for pre-validation reads. A custom gate without a known budget returns
   * {@code null}; the file service then applies its own hard file-size bound.
   */
  default ValidationLimits limits() {
    return null;
  }
}
