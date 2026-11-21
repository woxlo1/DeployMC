package io.github.woxlo1.deploymc.core.validation;

/** Host-controlled validation limits. Request data can never raise these bounds. */
public final class ValidationLimits {

  public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
  public static final int DEFAULT_MAX_CODE_POINTS = 1_000_000;
  public static final int DEFAULT_MAX_LINES = 50_000;
  public static final int DEFAULT_MAX_DEPTH = 100;
  public static final int DEFAULT_MAX_NODES = 1_000_000;
  public static final int DEFAULT_MAX_ALIAS_EXPANSIONS_PER_NODE = 100;
  public static final int DEFAULT_MAX_KEY_LENGTH = 1_024;
  public static final int DEFAULT_MAX_ISSUES = 200;

  private final long maxBytes;
  private final int maxCodePoints;
  private final int maxLines;
  private final int maxDepth;
  private final int maxNodes;
  private final int maxAliasExpansionsPerNode;
  private final int maxKeyLength;
  private final int maxIssues;

  public ValidationLimits(
      long maxBytes,
      int maxCodePoints,
      int maxLines,
      int maxDepth,
      int maxNodes,
      int maxAliasExpansionsPerNode,
      int maxKeyLength,
      int maxIssues) {
    if (maxBytes < 1
        || maxCodePoints < 1
        || maxLines < 1
        || maxDepth < 1
        || maxNodes < 1
        || maxAliasExpansionsPerNode < 1
        || maxKeyLength < 1
        || maxIssues < 1) {
      throw new IllegalArgumentException("Validation limits must be positive");
    }
    this.maxBytes = maxBytes;
    this.maxCodePoints = maxCodePoints;
    this.maxLines = maxLines;
    this.maxDepth = maxDepth;
    this.maxNodes = maxNodes;
    this.maxAliasExpansionsPerNode = maxAliasExpansionsPerNode;
    this.maxKeyLength = maxKeyLength;
    this.maxIssues = maxIssues;
  }

  public static ValidationLimits defaults() {
    return new ValidationLimits(
        DEFAULT_MAX_BYTES,
        DEFAULT_MAX_CODE_POINTS,
        DEFAULT_MAX_LINES,
        DEFAULT_MAX_DEPTH,
        DEFAULT_MAX_NODES,
        DEFAULT_MAX_ALIAS_EXPANSIONS_PER_NODE,
        DEFAULT_MAX_KEY_LENGTH,
        DEFAULT_MAX_ISSUES);
  }

  /** A small-budget profile used by hostile-input tests and constrained hosts. */
  public static ValidationLimits small() {
    return new ValidationLimits(4096L, 4096, 128, 8, 128, 2, 64, 32);
  }

  public long maxBytes() {
    return maxBytes;
  }

  public int maxCodePoints() {
    return maxCodePoints;
  }

  public int maxLines() {
    return maxLines;
  }

  public int maxDepth() {
    return maxDepth;
  }

  public int maxNodes() {
    return maxNodes;
  }

  public int maxAliasExpansionsPerNode() {
    return maxAliasExpansionsPerNode;
  }

  public int maxKeyLength() {
    return maxKeyLength;
  }

  public int maxIssues() {
    return maxIssues;
  }
}
