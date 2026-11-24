package io.github.woxlo1.deploymc.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PART 33.3 fixture contract: committed Cloudflare Access JWT vectors and the localhost mock JWKS
 * server. These tests validate fixture and generator artifacts only; they claim neither verifier
 * behavior nor Cloudflare parity. Product verification is exercised by {@code
 * CloudflareJwtVerifierTest} in the {@code core.auth.cloudflare} package.
 */
class JwtVerificationContractTest {

  private static final String RESOURCE = "/jwt/";
  private static final int MAX_FILE_BYTES = 256 * 1024;
  private static final byte[] DIGEST_INFO =
      new byte[] {
        0x30,
        0x31,
        0x30,
        0x0d,
        0x06,
        0x09,
        0x60,
        (byte) 0x86,
        0x48,
        0x01,
        0x65,
        0x03,
        0x04,
        0x02,
        0x01,
        0x05,
        0x00,
        0x04,
        0x20
      };

  @TempDir Path tempDir;

  @Test
  void manifestPinsDeterministicGeneratorContract() throws Exception {
    Properties manifest = manifest();
    String generator = fixture("generate_jwt_vectors.py");
    assertEquals("1", manifest.getProperty("schema.version"));
    assertEquals("2026-11-22.1", manifest.getProperty("corpus.version"));
    assertEquals("tests/security/jwt/generate_jwt_vectors.py", manifest.getProperty("generator"));
    assertEquals("2026-11-22T00:00:00Z", manifest.getProperty("reference.utc"));
    assertEquals("2048", manifest.getProperty("key.size.bits"));
    assertEquals("false", manifest.getProperty("key.private.exported"));
    for (String marker :
        Arrays.asList(
            "PRIME_BITS = 1024",
            "REFERENCE_UTC = \"2026-11-22T00:00:00Z\"",
            "KEY_SEEDS = {",
            "encoding=\"ascii\"",
            "newline=\"\\n\"",
            "sort_keys=True",
            "separators=(\",\", \":\")",
            "hashlib.sha256",
            "sys.argv[1]")) {
      assertTrue(generator.contains(marker), "Generator lost determinism marker: " + marker);
    }
    String reference = manifest.getProperty("reference.utc");
    long epoch = Instant.parse(reference).getEpochSecond();
    assertEquals(1795305600L, epoch);
  }

  @Test
  void regenerationIsByteIdenticalOrRemainsStaticallyPinned() throws Exception {
    String python = pythonExecutable();
    if (python == null) {
      // No external interpreter on this host: the pinned seeds, time, encoding and digests are
      // still checked by manifestPinsDeterministicGeneratorContract and the manifest test below.
      assertTrue(
          fixture("generate_jwt_vectors.py").contains("KEY_SEEDS = {"),
          "Pinned generator markers must remain present without Python");
      return;
    }
    Path generator = repositoryRoot().resolve("tests/security/jwt/generate_jwt_vectors.py");
    assertTrue(Files.isRegularFile(generator), "Missing generator: " + generator);
    Process process =
        new ProcessBuilder(python, generator.toString(), tempDir.toString())
            .redirectErrorStream(true)
            .start();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    InputStream stream = process.getInputStream();
    int read;
    while ((read = stream.read(buffer)) != -1 && output.size() < 8192) {
      output.write(buffer, 0, read);
    }
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("JWT generator exceeded 30 seconds");
    }
    assertEquals(0, process.exitValue(), "Generator failed: " + output.toString("US-ASCII"));
    for (String name :
        Arrays.asList(
            "jwt-vectors.jsonl",
            "jwks-initial.json",
            "jwks-rotated.json",
            "jwt-corpus-manifest.properties")) {
      byte[] regenerated = Files.readAllBytes(tempDir.resolve(name));
      byte[] committed = bytes(name);
      assertTrue(
          Arrays.equals(committed, regenerated), "Regeneration is not byte-identical for " + name);
    }
  }

  @Test
  void manifestExpectationsMatchAllCommittedVectors() throws Exception {
    Properties manifest = manifest();
    List<Map<String, Object>> vectors = vectors();
    assertEquals(11, vectors.size());
    assertEquals(11, Integer.parseInt(manifest.getProperty("source.vectors.entries")));
    for (Map<String, Object> vector : vectors) {
      String id = string(vector, "id");
      assertEquals(string(vector, "category"), manifest.getProperty("vector." + id + ".category"));
      assertEquals(
          outcome(vector, "initial_jwks"), manifest.getProperty("vector." + id + ".initial"), id);
      assertEquals(
          outcome(vector, "rotated_jwks"), manifest.getProperty("vector." + id + ".rotated"), id);
    }
    assertEquals(join(ids(vectors)), manifest.getProperty("vector.list"));
    assertDigest(manifest, "vectors", "jwt-vectors.jsonl");
    assertDigest(manifest, "jwks.initial", "jwks-initial.json");
    assertDigest(manifest, "jwks.rotated", "jwks-rotated.json");
    assertTrue(manifest.getProperty("issuer").endsWith(".test"));
    assertTrue(manifest.getProperty("audience").endsWith(".test"));
  }

  @Test
  void vectorsCarryExpectedHeadersClaimsAndSignatureCategories() throws Exception {
    Properties manifest = manifest();
    String expectedIssuer = manifest.getProperty("issuer");
    String expectedAudience = manifest.getProperty("audience");
    Map<String, Object> initial = object(parse(fixture("jwks-initial.json")));
    Map<String, Object> rotated = object(parse(fixture("jwks-rotated.json")));
    Map<String, Map<String, Object>> initialKeys = keys(initial);
    Map<String, Map<String, Object>> rotatedKeys = keys(rotated);
    assertEquals(2, initialKeys.size());
    assertEquals(1, rotatedKeys.size());
    assertTrue(rotatedKeys.containsKey("dmctest-key-rotated-2026-11-22"));
    long epoch = Instant.parse("2026-11-22T00:00:00Z").getEpochSecond();

    for (Map<String, Object> vector : vectors()) {
      String id = string(vector, "id");
      Map<String, Object> header = object(vector.get("header"));
      Map<String, Object> claims = object(vector.get("claims"));
      String[] parts = string(vector, "token").split("\\.", -1);
      assertEquals(3, parts.length, id);
      assertEquals(header, object(parse(decodeSegment(parts[0]))), id + " header");
      assertEquals(claims, object(parse(decodeSegment(parts[1]))), id + " claims");
      assertEquals("JWT", header.get("typ"), id);
      assertNotNull(header.get("kid"), id);
      if ("wrong-iss".equals(id)) {
        assertEquals("https://other-team.example.test", claims.get("iss"), id);
      } else {
        assertEquals(expectedIssuer, claims.get("iss"), id);
      }
      assertNotNull(claims.get("sub"), id);
      assertNotNull(claims.get("jti"), id);
      String algorithm = string(header, "alg");
      String kid = string(header, "kid");
      String signingInput = parts[0] + "." + parts[1];

      if ("alg-none".equals(id)) {
        assertEquals("none", algorithm);
        assertEquals("", parts[2]);
      } else if ("alg-confusion".equals(id)) {
        assertEquals("HS256", algorithm);
        assertEquals(32, Base64.getUrlDecoder().decode(pad(parts[2])).length);
        assertFalse(verifyRs256(signingInput, parts[2], initialKeys.get(kid)), id);
      } else if ("tampered".equals(id)) {
        assertEquals("RS256", algorithm);
        assertTrue(initialKeys.containsKey(kid), id);
        assertFalse(verifyRs256(signingInput, parts[2], initialKeys.get(kid)), id);
      } else if ("unknown-kid".equals(id)) {
        assertEquals("RS256", algorithm);
        assertFalse(initialKeys.containsKey(kid), id);
        assertFalse(rotatedKeys.containsKey(kid), id);
      } else if ("retired-key".equals(id)) {
        assertEquals("RS256", algorithm);
        assertTrue(initialKeys.containsKey(kid), id);
        assertFalse(rotatedKeys.containsKey(kid), id);
        assertTrue(verifyRs256(signingInput, parts[2], initialKeys.get(kid)), id);
      } else {
        assertEquals("RS256", algorithm, id);
        assertTrue(initialKeys.containsKey(kid), id);
        assertTrue(verifyRs256(signingInput, parts[2], initialKeys.get(kid)), id);
      }

      if ("valid".equals(id)) {
        assertEquals(expectedAudience, claims.get("aud"));
        assertEquals(Long.valueOf(epoch + 7200L), claims.get("exp"));
        assertEquals(Long.valueOf(epoch - 60L), claims.get("nbf"));
        assertEquals(Long.valueOf(epoch - 60L), claims.get("iat"));
      } else if ("expired".equals(id)) {
        assertEquals(Long.valueOf(epoch - 3600L), claims.get("exp"));
      } else if ("nbf-in-future".equals(id)) {
        assertEquals(Long.valueOf(epoch + 3600L), claims.get("nbf"));
      }
    }
  }

  @Test
  void rejectionVectorsAreFailClosedCategories() throws Exception {
    Map<String, String> expected = new HashMap<String, String>();
    expected.put("expired", "auth.expired");
    expected.put("nbf-in-future", "auth.not-yet-valid");
    expected.put("wrong-aud", "auth.invalid");
    expected.put("wrong-iss", "auth.invalid");
    expected.put("alg-none", "auth.invalid");
    expected.put("alg-confusion", "auth.invalid");
    expected.put("tampered", "auth.invalid");
    expected.put("unknown-kid", "auth.invalid");
    Set<String> rejections = new HashSet<String>();
    Set<String> accepted = new HashSet<String>();
    for (Map<String, Object> vector : vectors()) {
      String id = string(vector, "id");
      String initial = outcome(vector, "initial_jwks");
      String rotated = outcome(vector, "rotated_jwks");
      Map<String, Object> rotatedExpectation =
          object(object(vector.get("expectations")).get("rotated_jwks"));
      if ("retired-key".equals(id)) {
        assertEquals("accept", initial, id);
        assertEquals("auth.invalid", rotated, id);
        assertEquals("auth.invalid", rotatedExpectation.get("error_code"), id);
      } else if (expected.containsKey(id)) {
        rejections.add(id);
        assertEquals(expected.get(id), initial, id);
        assertEquals(initial, rotated, id);
        assertEquals("reject", string(rotatedExpectation, "outcome"), id);
      } else {
        accepted.add(id);
        assertEquals("accept", initial, id);
        assertEquals("accept", rotated, id);
        assertFalse(rotatedExpectation.containsKey("error_code"), id);
      }
    }
    assertEquals(expected.keySet(), rejections);
    assertEquals(new HashSet<String>(Arrays.asList("valid", "rotated-key")), accepted);
    assertEquals(8, rejections.size());
    String errors = fixtureAtRoot("specification/api/errors.md");
    for (String code : expected.values()) {
      assertTrue(errors.contains("`" + code + "`"), "Missing error code: " + code);
    }
  }

  @Test
  void noPrivateKeyMaterialIsCommitted() throws Exception {
    Properties manifest = manifest();
    assertEquals("false", manifest.getProperty("key.private.exported"));
    for (String name :
        Arrays.asList(
            "jwt-vectors.jsonl",
            "jwks-initial.json",
            "jwks-rotated.json",
            "jwt-corpus-manifest.properties")) {
      String text = fixture(name);
      assertFalse(text.contains("PRIVATE KEY"), name);
      for (String field :
          Arrays.asList("\"d\":", "\"p\":", "\"q\":", "\"dp\":", "\"dq\":", "\"qi\":")) {
        assertFalse(text.contains(field), name + " contains " + field);
      }
    }
    Map<String, Object> initial = object(parse(fixture("jwks-initial.json")));
    for (Map.Entry<String, Map<String, Object>> entry : keys(initial).entrySet()) {
      Set<String> fields = new HashSet<String>(entry.getValue().keySet());
      assertEquals(
          new HashSet<String>(Arrays.asList("alg", "e", "kid", "kty", "n", "use")),
          fields,
          entry.getKey());
      assertEquals("RSA", entry.getValue().get("kty"));
      assertEquals("sig", entry.getValue().get("use"));
    }
  }

  @Test
  void mockJwksServerIsLocalhostOnlyAndBounded() throws Exception {
    String server = fixture("mock_jwks_server.py");
    for (String marker :
        Arrays.asList(
            "(\"127.0.0.1\", 0)",
            "JWKS_PATHS = (\"/cdn-cgi/access/certs\", \"/jwks\")",
            "ThreadingHTTPServer",
            "def rotate(",
            "def reset(",
            "def set_unavailable(",
            "Cache-Control",
            "ETag",
            "503",
            "Retry-After")) {
      assertTrue(server.contains(marker), "Mock server lost marker: " + marker);
    }
    for (String forbidden :
        Arrays.asList(
            "0.0.0.0", "::", "cloudflareaccess.com", "https://", "urlopen", "requests.", "8080")) {
      assertFalse(server.contains(forbidden), "Mock server must not contain " + forbidden);
    }
    String readme = fixture("README.md");
    assertTrue(readme.contains("binds only to `127.0.0.1`"));
    assertTrue(readme.contains("CloudflareJwtVerifierTest"));
    for (String marker :
        Arrays.asList(
            "/control/rotate", "/control/reset", "/control/unavailable", "If-None-Match", "304")) {
      assertTrue(server.contains(marker), "Mock server lost control marker: " + marker);
    }
    assertTrue(readme.contains("/control/unavailable?value=1"));
  }

  @Test
  void productVerifierIgnoresUnsignedIdentityHeaders() throws Exception {
    Path root = repositoryRoot();
    Path verifier =
        root.resolve(
            "core/src/main/java/io/github/woxlo1/deploymc/core/auth/cloudflare/CloudflareJwtVerifier.java");
    assertTrue(Files.isRegularFile(verifier), "Missing product verifier source");
    String source = new String(Files.readAllBytes(verifier), StandardCharsets.UTF_8);
    assertTrue(source.contains("cf-access-jwt-assertion"));
    assertTrue(source.contains("cf-access-authenticated-user-email"));
    assertTrue(source.contains("FORWARDED_EMAIL_HEADER"));
    assertFalse(
        source.contains("headers().get(FORWARDED_EMAIL_HEADER)"),
        "The forwarded email header must never be read as an identity source");
    assertTrue(fixtureAtRoot("specification/api/errors.md").contains("`auth.jwks-unavailable`"));
  }

  private static String outcome(Map<String, Object> vector, String state) {
    Map<String, Object> expectation = object(object(vector.get("expectations")).get(state));
    Object code = expectation.get("error_code");
    return code == null ? string(expectation, "outcome") : (String) code;
  }

  private static String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) {
        result.append(',');
      }
      result.append(value);
    }
    return result.toString();
  }

  private static List<String> ids(List<Map<String, Object>> vectors) {
    List<String> result = new ArrayList<String>();
    for (Map<String, Object> vector : vectors) {
      result.add(string(vector, "id"));
    }
    return result;
  }

  private static void assertDigest(Properties manifest, String source, String name)
      throws Exception {
    assertEquals(name, manifest.getProperty("source." + source + ".file"));
    String expected = manifest.getProperty("source." + source + ".sha256");
    assertEquals(64, expected.length());
    assertEquals(expected, sha256(bytes(name)));
  }

  private static Map<String, Map<String, Object>> keys(Map<String, Object> jwks) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    List<Object> keys = list(jwks.get("keys"));
    for (Object key : keys) {
      Map<String, Object> jwk = object(key);
      result.put(string(jwk, "kid"), jwk);
    }
    return result;
  }

  private static boolean verifyRs256(
      String signingInput, String signatureSegment, Map<String, Object> jwk) {
    if (jwk == null) {
      return false;
    }
    byte[] signature = Base64.getUrlDecoder().decode(pad(signatureSegment));
    BigInteger modulus = bigInteger(string(jwk, "n"));
    BigInteger exponent = bigInteger(string(jwk, "e"));
    int length = (modulus.bitLength() + 7) / 8;
    if (signature.length != length || length < 64) {
      return false;
    }
    byte[] digest = digest(signingInput.getBytes(StandardCharsets.US_ASCII));
    byte[] encoded = new byte[DIGEST_INFO.length + digest.length];
    System.arraycopy(DIGEST_INFO, 0, encoded, 0, DIGEST_INFO.length);
    System.arraycopy(digest, 0, encoded, DIGEST_INFO.length, digest.length);
    if (length - encoded.length - 3 < 8) {
      return false;
    }
    byte[] expected = new byte[length];
    expected[0] = 0;
    expected[1] = 1;
    Arrays.fill(expected, 2, length - encoded.length - 1, (byte) 0xff);
    expected[length - encoded.length - 1] = 0;
    System.arraycopy(encoded, 0, expected, length - encoded.length, encoded.length);
    byte[] restored = new BigInteger(1, signature).modPow(exponent, modulus).toByteArray();
    if (restored.length > length) {
      if (restored.length != length + 1 || restored[0] != 0) {
        return false;
      }
      restored = Arrays.copyOfRange(restored, 1, restored.length);
    }
    if (restored.length < length) {
      byte[] padded = new byte[length];
      System.arraycopy(restored, 0, padded, length - restored.length, restored.length);
      restored = padded;
    }
    return MessageDigest.isEqual(expected, restored);
  }

  private static BigInteger bigInteger(String base64Url) {
    return new BigInteger(1, Base64.getUrlDecoder().decode(pad(base64Url)));
  }

  private static byte[] digest(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private static String sha256(byte[] input) {
    StringBuilder result = new StringBuilder();
    for (byte value : digest(input)) {
      result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
    }
    return result.toString();
  }

  private static String decodeSegment(String segment) {
    return new String(Base64.getUrlDecoder().decode(pad(segment)), StandardCharsets.US_ASCII);
  }

  private static String pad(String value) {
    StringBuilder result = new StringBuilder(value);
    while (result.length() % 4 != 0) {
      result.append('=');
    }
    return result.toString();
  }

  private static String pythonExecutable() {
    for (String candidate : Arrays.asList("python", "python3")) {
      try {
        Process process =
            new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
        if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
          return candidate;
        }
        process.destroyForcibly();
      } catch (IOException exception) {
        continue;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  private static List<Map<String, Object>> vectors() throws IOException {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    String text = fixture("jwt-vectors.jsonl");
    for (String line : text.split("\n")) {
      if (!line.isEmpty()) {
        result.add(object(parse(line)));
      }
    }
    return result;
  }

  private static Properties manifest() throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(fixture("jwt-corpus-manifest.properties")));
    return properties;
  }

  private static String fixture(String name) throws IOException {
    return new String(bytes(name), StandardCharsets.US_ASCII);
  }

  private static byte[] bytes(String name) throws IOException {
    String resource = RESOURCE + name;
    try (InputStream stream = JwtVerificationContractTest.class.getResourceAsStream(resource)) {
      assertNotNull(stream, "Missing JWT fixture resource: " + resource);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      byte[] result = output.toByteArray();
      assertTrue(result.length > 0 && result.length <= MAX_FILE_BYTES, resource + " size");
      for (byte value : result) {
        assertTrue(
            value == '\n' || (value >= 0x20 && value < 0x7f),
            resource + " must be ASCII with LF endings");
      }
      return result;
    }
  }

  private static String fixtureAtRoot(String relative) throws IOException {
    Path path = repositoryRoot().resolve(relative);
    assertTrue(Files.isRegularFile(path), "Missing repository artifact: " + relative);
    byte[] bytes = Files.readAllBytes(path);
    assertTrue(bytes.length <= 4 * MAX_FILE_BYTES, relative + " is too large");
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static Path repositoryRoot() {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    for (int attempt = 0; attempt < 6; attempt++) {
      if (Files.isRegularFile(current.resolve("tests/security/jwt/jwt-vectors.jsonl"))
          && Files.isRegularFile(
              current.resolve(
                  "docs/development/DeployMC_Complete_Master_Development_Specification.md"))) {
        return current;
      }
      Path parent = current.getParent();
      if (parent == null) {
        break;
      }
      current = parent;
    }
    throw new AssertionError("Repository root not found from " + current);
  }

  private static Object parse(String text) {
    return new JsonParser(text).parse();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    assertTrue(value instanceof Map, "Expected JSON object but got " + value);
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    assertTrue(value instanceof List, "Expected JSON array but got " + value);
    return (List<Object>) value;
  }

  private static String string(Map<String, Object> object, String key) {
    Object value = object.get(key);
    assertTrue(value instanceof String, "Expected string for " + key);
    return (String) value;
  }

  /** Bounded JSON reader for the small, deterministic fixture documents. */
  private static final class JsonParser {
    private static final int MAX_NODES = 4096;
    private static final int MAX_DEPTH = 16;
    private final String text;
    private int index;
    private int nodes;

    JsonParser(String text) {
      assertTrue(text.length() <= MAX_FILE_BYTES, "JSON input too large");
      this.text = text;
    }

    Object parse() {
      Object value = value(0);
      whitespace();
      if (index != text.length()) {
        throw fail("trailing characters");
      }
      return value;
    }

    private Object value(int depth) {
      if (depth > MAX_DEPTH || ++nodes > MAX_NODES) {
        throw fail("input too complex");
      }
      whitespace();
      if (index >= text.length()) {
        throw fail("unexpected end");
      }
      char current = text.charAt(index);
      if (current == '{') {
        return objectValue(depth);
      }
      if (current == '[') {
        return arrayValue(depth);
      }
      if (current == '"') {
        return stringValue();
      }
      if (current == 't') {
        return literal("true", Boolean.TRUE);
      }
      if (current == 'f') {
        return literal("false", Boolean.FALSE);
      }
      if (current == 'n') {
        return literal("null", null);
      }
      return numberValue();
    }

    private Map<String, Object> objectValue(int depth) {
      expect('{');
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      whitespace();
      if (consume('}')) {
        return result;
      }
      while (true) {
        whitespace();
        String key = stringValue();
        whitespace();
        expect(':');
        result.put(key, value(depth + 1));
        whitespace();
        if (consume('}')) {
          return result;
        }
        expect(',');
      }
    }

    private List<Object> arrayValue(int depth) {
      expect('[');
      List<Object> result = new ArrayList<Object>();
      whitespace();
      if (consume(']')) {
        return result;
      }
      while (true) {
        result.add(value(depth + 1));
        whitespace();
        if (consume(']')) {
          return result;
        }
        expect(',');
      }
    }

    private String stringValue() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (index < text.length()) {
        char current = text.charAt(index++);
        if (current == '"') {
          if (result.length() > 4096) {
            throw fail("string too long");
          }
          return result.toString();
        }
        if (current == '\\') {
          if (index >= text.length()) {
            throw fail("bad escape");
          }
          char escaped = text.charAt(index++);
          if (escaped == 'u') {
            if (index + 4 > text.length()) {
              throw fail("bad unicode escape");
            }
            result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
            index += 4;
          } else if ("\"\\/bfnrt".indexOf(escaped) >= 0) {
            result.append(escaped);
          } else {
            throw fail("bad escape");
          }
        } else {
          result.append(current);
        }
      }
      throw fail("unterminated string");
    }

    private Object numberValue() {
      int start = index;
      while (index < text.length() && "-+.0123456789eE".indexOf(text.charAt(index)) >= 0) {
        index++;
      }
      String number = text.substring(start, index);
      if (number.isEmpty()) {
        throw fail("expected number");
      }
      if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
        return Double.valueOf(number);
      }
      return Long.valueOf(number);
    }

    private Object literal(String token, Object value) {
      if (!text.startsWith(token, index)) {
        throw fail("bad literal");
      }
      index += token.length();
      return value;
    }

    private void whitespace() {
      while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
        index++;
      }
    }

    private boolean consume(char expected) {
      if (index < text.length() && text.charAt(index) == expected) {
        index++;
        return true;
      }
      return false;
    }

    private void expect(char expected) {
      if (!consume(expected)) {
        throw fail("expected " + expected);
      }
    }

    private AssertionError fail(String message) {
      return new AssertionError(message + " at offset " + index);
    }
  }
}
