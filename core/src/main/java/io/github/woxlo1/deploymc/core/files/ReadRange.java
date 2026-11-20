package io.github.woxlo1.deploymc.core.files;

/** A validated byte range. Ranges are inclusive at the start and exclusive at the end. */
public final class ReadRange {

  private final long offset;
  private final long length;
  private final boolean bounded;

  private ReadRange(long offset, long length, boolean bounded) {
    this.offset = offset;
    this.length = length;
    this.bounded = bounded;
  }

  public static ReadRange full() {
    return new ReadRange(0L, 0L, false);
  }

  public static ReadRange from(long offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("Range offset must not be negative");
    }
    return new ReadRange(offset, 0L, false);
  }

  public static ReadRange between(long offset, long length) {
    if (offset < 0 || length < 0) {
      throw new IllegalArgumentException("Range offset and length must not be negative");
    }
    return new ReadRange(offset, length, true);
  }

  public long offset() {
    return offset;
  }

  public long length() {
    return length;
  }

  public boolean bounded() {
    return bounded;
  }
}
