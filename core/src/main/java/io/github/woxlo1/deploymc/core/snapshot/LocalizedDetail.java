package io.github.woxlo1.deploymc.core.snapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A localized parameter that is expanded when its enclosing diagnostic is formatted. */
final class LocalizedDetail implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String messageKey;
  private final Serializable[] parameters;

  LocalizedDetail(SnapshotStoreDetail detail, Object... parameters) {
    this.messageKey = detail.messageKey();
    List<Serializable> copied = new ArrayList<Serializable>();
    if (parameters != null) {
      for (Object parameter : parameters) {
        copied.add(String.valueOf(parameter));
      }
    }
    this.parameters = copied.toArray(new Serializable[0]);
  }

  String localized(Locale locale) {
    return SnapshotStoreMessages.format(locale, messageKey, (Object[]) parameters);
  }

  @Override
  public String toString() {
    return localized(Locale.ROOT);
  }
}
