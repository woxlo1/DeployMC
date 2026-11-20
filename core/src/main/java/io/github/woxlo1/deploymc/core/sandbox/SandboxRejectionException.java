package io.github.woxlo1.deploymc.core.sandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A request path that failed sandbox validation. Callers must fail closed. */
public final class SandboxRejectionException extends Exception {

  private static final long serialVersionUID = 1L;

  private final SandboxErrorCode code;
  private final String[] parameters;

  public SandboxRejectionException(SandboxErrorCode code, Object... parameters) {
    super(SandboxMessages.format(Locale.ROOT, code.messageKey(), parameters));
    this.code = code;
    List<String> copied = new ArrayList<String>();
    if (parameters != null) {
      for (Object parameter : parameters) {
        copied.add(String.valueOf(parameter));
      }
    }
    this.parameters = copied.toArray(new String[0]);
  }

  public SandboxErrorCode code() {
    return code;
  }

  public String messageKey() {
    return code.messageKey();
  }

  public List<String> parameters() {
    return Collections.unmodifiableList(Arrays.asList(parameters));
  }
}
