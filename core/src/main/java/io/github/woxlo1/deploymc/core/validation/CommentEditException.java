package io.github.woxlo1.deploymc.core.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A YAML scalar edit cannot be applied without changing untouched bytes. */
public final class CommentEditException extends ValidationException {

  private static final long serialVersionUID = 1L;

  public CommentEditException(String reason, Object... parameters) {
    super(
        "validation.comment-edit-unsupported",
        "code.validation.comment-edit-unsupported",
        prepend(reason, parameters));
  }

  private static Object[] prepend(String reason, Object... parameters) {
    List<Object> result = new ArrayList<Object>();
    result.add(reason);
    if (parameters != null) {
      result.addAll(Arrays.asList(parameters));
    }
    return result.toArray();
  }
}
