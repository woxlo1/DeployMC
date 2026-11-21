package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Base failure for the validation boundary. The code is stable and never localized. */
public class ValidationException extends Exception {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final String messageKey;
  private final ArrayList<String> parameters;

  public ValidationException(String code, String messageKey, Object... parameters) {
    this(code, null, messageKey, parameters);
  }

  public ValidationException(
      String code, Throwable cause, String messageKey, Object... parameters) {
    super(ValidationMessages.format(Locale.ROOT, messageKey, parameters), cause);
    this.code = code;
    this.messageKey = messageKey;
    this.parameters = copy(parameters);
  }

  public String code() {
    return code;
  }

  public String messageKey() {
    return messageKey;
  }

  public List<String> parameters() {
    return Collections.unmodifiableList(parameters);
  }

  private static ArrayList<String> copy(Object... values) {
    ArrayList<String> result = new ArrayList<String>();
    if (values != null) {
      for (Object value : values) {
        result.add(String.valueOf(value));
      }
    }
    return result;
  }
}
