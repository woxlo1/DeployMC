package io.github.woxlo1.deploymc.core.diff;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Internal signal that input cannot be interpreted in the requested mode. */
final class InvalidInputException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String note;
  private final LinkedHashSet<String> notes;

  InvalidInputException(String note) {
    this(note, Collections.<String>emptySet());
  }

  InvalidInputException(String note, Set<String> notes) {
    super(note);
    this.note = note;
    this.notes = notes == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(notes);
  }

  String note() {
    return note;
  }

  Set<String> notes() {
    return Collections.unmodifiableSet(notes);
  }
}
