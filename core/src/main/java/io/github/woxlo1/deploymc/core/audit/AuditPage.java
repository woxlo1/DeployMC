package io.github.woxlo1.deploymc.core.audit;

import java.util.Collections;
import java.util.List;

/** One cursor-paginated audit query result. */
public final class AuditPage {

  private final List<AuditRecord> items;
  private final String nextCursor;
  private final long total;

  public AuditPage(List<AuditRecord> items, String nextCursor, long total) {
    this.items = Collections.unmodifiableList(items);
    this.nextCursor = nextCursor;
    this.total = total;
  }

  public List<AuditRecord> items() {
    return items;
  }

  public String nextCursor() {
    return nextCursor;
  }

  public long total() {
    return total;
  }
}
