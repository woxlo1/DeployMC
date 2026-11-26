package io.github.woxlo1.deploymc.core.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/** First integrity failure, with a file name but never an absolute host path. */
public final class AuditVerificationFailure {

  private final String file;
  private final int line;
  private final long seq;
  private final String code;

  public AuditVerificationFailure(String file, int line, long seq, String code) {
    this.file = file;
    this.line = line;
    this.seq = seq;
    this.code = code;
  }

  public String file() {
    return file;
  }

  public int line() {
    return line;
  }

  public long seq() {
    return seq;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> toJsonObject() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("file", file);
    result.put("line", Long.valueOf(line));
    result.put("seq", Long.valueOf(seq));
    result.put("code", code);
    return result;
  }
}
