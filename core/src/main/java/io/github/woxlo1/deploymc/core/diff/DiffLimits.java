package io.github.woxlo1.deploymc.core.diff;

/** Resource bounds for one diff operation. Defaults are normative and may only be lowered. */
public final class DiffLimits {

  public static final DiffLimits DEFAULTS =
      new DiffLimits(
          2_097_152, 100_000, 1_000_000, 4_096, 100_000, 100_000, 262_144, 1_048_576, 1_000);

  private final int maxBytesPerSide;
  private final int maxLinesPerSide;
  private final int maxEditOperations;
  private final int maxWordTokensPerLine;
  private final int maxSemanticNodes;
  private final int maxJarEntries;
  private final int maxJarDescriptorBytes;
  private final int maxJarDescriptorTotalBytes;
  private final int maxJarEntryListOutput;

  private DiffLimits(
      int maxBytesPerSide,
      int maxLinesPerSide,
      int maxEditOperations,
      int maxWordTokensPerLine,
      int maxSemanticNodes,
      int maxJarEntries,
      int maxJarDescriptorBytes,
      int maxJarDescriptorTotalBytes,
      int maxJarEntryListOutput) {
    this.maxBytesPerSide = maxBytesPerSide;
    this.maxLinesPerSide = maxLinesPerSide;
    this.maxEditOperations = maxEditOperations;
    this.maxWordTokensPerLine = maxWordTokensPerLine;
    this.maxSemanticNodes = maxSemanticNodes;
    this.maxJarEntries = maxJarEntries;
    this.maxJarDescriptorBytes = maxJarDescriptorBytes;
    this.maxJarDescriptorTotalBytes = maxJarDescriptorTotalBytes;
    this.maxJarEntryListOutput = maxJarEntryListOutput;
  }

  public int maxBytesPerSide() {
    return maxBytesPerSide;
  }

  public int maxLinesPerSide() {
    return maxLinesPerSide;
  }

  public int maxEditOperations() {
    return maxEditOperations;
  }

  public int maxWordTokensPerLine() {
    return maxWordTokensPerLine;
  }

  public int maxSemanticNodes() {
    return maxSemanticNodes;
  }

  public int maxJarEntries() {
    return maxJarEntries;
  }

  public int maxJarDescriptorBytes() {
    return maxJarDescriptorBytes;
  }

  public int maxJarDescriptorTotalBytes() {
    return maxJarDescriptorTotalBytes;
  }

  public int maxJarEntryListOutput() {
    return maxJarEntryListOutput;
  }

  public DiffLimits withMaxBytesPerSide(int value) {
    checkPositive("maxBytesPerSide", value);
    checkLower("maxBytesPerSide", value, maxBytesPerSide);
    return new DiffLimits(
        value,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxLinesPerSide(int value) {
    checkPositive("maxLinesPerSide", value);
    checkLower("maxLinesPerSide", value, maxLinesPerSide);
    return new DiffLimits(
        maxBytesPerSide,
        value,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxEditOperations(int value) {
    checkPositive("maxEditOperations", value);
    checkLower("maxEditOperations", value, maxEditOperations);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        value,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxWordTokensPerLine(int value) {
    checkPositive("maxWordTokensPerLine", value);
    checkLower("maxWordTokensPerLine", value, maxWordTokensPerLine);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        value,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxSemanticNodes(int value) {
    checkPositive("maxSemanticNodes", value);
    checkLower("maxSemanticNodes", value, maxSemanticNodes);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        value,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxJarEntries(int value) {
    checkPositive("maxJarEntries", value);
    checkLower("maxJarEntries", value, maxJarEntries);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        value,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxJarDescriptorBytes(int value) {
    checkPositive("maxJarDescriptorBytes", value);
    checkLower("maxJarDescriptorBytes", value, maxJarDescriptorBytes);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        value,
        maxJarDescriptorTotalBytes,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxJarDescriptorTotalBytes(int value) {
    checkPositive("maxJarDescriptorTotalBytes", value);
    checkLower("maxJarDescriptorTotalBytes", value, maxJarDescriptorTotalBytes);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        value,
        maxJarEntryListOutput);
  }

  public DiffLimits withMaxJarEntryListOutput(int value) {
    checkPositive("maxJarEntryListOutput", value);
    checkLower("maxJarEntryListOutput", value, maxJarEntryListOutput);
    return new DiffLimits(
        maxBytesPerSide,
        maxLinesPerSide,
        maxEditOperations,
        maxWordTokensPerLine,
        maxSemanticNodes,
        maxJarEntries,
        maxJarDescriptorBytes,
        maxJarDescriptorTotalBytes,
        value);
  }

  private static void checkPositive(String name, int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  private static void checkLower(String name, int value, int current) {
    if (value > current) {
      throw new IllegalArgumentException(name + " cannot exceed the default");
    }
  }
}
