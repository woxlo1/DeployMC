package io.github.woxlo1.deploymc.core.sandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the shared, escaped security corpora from the test classpath. */
final class SandboxCorpus {

  private SandboxCorpus() {}

  static List<String> lines(String name) throws IOException {
    String resource = "/sandbox/" + name;
    InputStream stream = SandboxCorpus.class.getResourceAsStream(resource);
    if (stream == null) {
      throw new IOException("Missing test resource: " + resource);
    }
    List<String> lines = new ArrayList<String>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("#")) {
          lines.add(line);
        }
      }
    }
    return lines;
  }

  /** Decodes the Java-style escapes emitted by generate_corpus.py. */
  static String decode(String encoded) {
    StringBuilder decoded = new StringBuilder();
    for (int i = 0; i < encoded.length(); i++) {
      char c = encoded.charAt(i);
      if (c != '\\') {
        decoded.append(c);
        continue;
      }
      if (++i >= encoded.length()) {
        throw new IllegalArgumentException("Dangling escape in corpus line: [" + encoded + "]");
      }
      char marker = encoded.charAt(i);
      if (marker == 'u') {
        decoded.append((char) hex(encoded, i + 1, 4));
        i += 4;
      } else if (marker == 'x') {
        decoded.append((char) hex(encoded, i + 1, 2));
        i += 2;
      } else if (marker == 'n') {
        decoded.append('\n');
      } else if (marker == 'r') {
        decoded.append('\r');
      } else if (marker == 't') {
        decoded.append('\t');
      } else if (marker == 'b') {
        decoded.append('\b');
      } else if (marker == 'f') {
        decoded.append('\f');
      } else if (marker == 'a') {
        decoded.append('\u0007');
      } else if (marker == 'v') {
        decoded.append('\u000b');
      } else if (marker == '\\' || marker == '\'' || marker == '"') {
        decoded.append(marker);
      } else {
        throw new IllegalArgumentException("Unknown corpus escape: \\" + marker);
      }
    }
    return decoded.toString();
  }

  private static int hex(String value, int start, int length) {
    if (start + length > value.length()) {
      throw new IllegalArgumentException("Truncated hex escape in corpus line");
    }
    int result = 0;
    for (int i = start; i < start + length; i++) {
      int digit = Character.digit(value.charAt(i), 16);
      if (digit < 0) {
        throw new IllegalArgumentException("Invalid hex escape in corpus line");
      }
      result = (result << 4) | digit;
    }
    return result;
  }
}
