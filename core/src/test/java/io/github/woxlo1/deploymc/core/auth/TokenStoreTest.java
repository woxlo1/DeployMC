package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenStoreTest {

  @TempDir Path tempDir;

  @Test
  void loadsEmptyDirectoryAndRoundTripsRecord() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    assertTrue(directory.toFile().isDirectory());
    assertTrue(store.records().isEmpty());

    TokenRecord record =
        new TokenRecord(
            "t-abc123", "admin console", TestHashes.sha256("dmc_test_token_value_1"), null);
    store.write(record);

    TokenStore reloaded = TokenStore.load(directory);
    assertEquals(1, reloaded.records().size());
    assertEquals("t-abc123", reloaded.records().get(0).id());
    assertEquals("admin console", reloaded.records().get(0).label());
    assertTrue(reloaded.records().get(0).matches(TestHashes.sha256("dmc_test_token_value_1")));
  }

  @Test
  void parsesOptionalExpiry() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    Instant expiry = Instant.parse("2030-01-02T03:04:05Z");
    store.write(
        new TokenRecord("t-expiry", "expiring", TestHashes.sha256("dmc_expiring_token_1"), expiry));

    TokenStore reloaded = TokenStore.load(directory);
    assertEquals(expiry, reloaded.records().get(0).expiresAt());
    assertTrue(reloaded.records().get(0).isExpired(expiry));
    assertTrue(!reloaded.records().get(0).isExpired(expiry.minusSeconds(1)));
  }

  @Test
  void rejectsInvalidRecordAndFileName() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore.load(directory);
    Files.write(
        directory.resolve("bad.json"),
        "{\"id\":\"t-abc123\",\"label\":\"x\",\"secretHash\":\"zz\"}"
            .getBytes(StandardCharsets.UTF_8));

    TokenStoreException failure =
        assertThrows(TokenStoreException.class, () -> TokenStore.load(directory));
    assertEquals(TokenStoreException.Reason.RECORD, failure.reason());

    Files.delete(directory.resolve("bad.json"));
    Files.write(
        directory.resolve("different.json"),
        ("{\"id\":\"t-abc123\",\"label\":\"x\",\"secretHash\":\""
                + TestHashes.hex(TestHashes.sha256("dmc_test_token_value_1"))
                + "\"}")
            .getBytes(StandardCharsets.UTF_8));
    assertEquals(
        TokenStoreException.Reason.RECORD,
        assertThrows(TokenStoreException.class, () -> TokenStore.load(directory)).reason());
  }

  @Test
  void rejectsDuplicateHashes() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    byte[] hash = TestHashes.sha256("dmc_duplicate_token_value");
    store.write(new TokenRecord("t-first", "first", hash, null));

    TokenStoreException failure =
        assertThrows(
            TokenStoreException.class,
            () -> store.write(new TokenRecord("t-second", "second", hash, null)));
    assertEquals(TokenStoreException.Reason.RECORD, failure.reason());
  }

  @Test
  void refusesNonDirectoryProvisioningPath() throws Exception {
    Path file = tempDir.resolve("tokens.d");
    Files.write(file, "not a directory".getBytes(StandardCharsets.UTF_8));

    assertEquals(
        TokenStoreException.Reason.DIRECTORY,
        assertThrows(TokenStoreException.class, () -> TokenStore.load(file)).reason());
  }

  @Test
  void roundTripsLifecycleFieldsAndScopes() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    Instant revoked = Instant.parse("2031-03-04T05:06:07Z");
    Instant used = Instant.parse("2031-03-03T05:06:07Z");
    store.write(
        new TokenRecord(
            "t-lifecycle",
            "ci-deployer",
            TestHashes.sha256("dmc_lifecycle_token_value"),
            Instant.parse("2032-01-01T00:00:00Z"),
            Arrays.asList("files.read", "server.read"),
            revoked,
            used));

    TokenRecord loaded = TokenStore.load(directory).records().get(0);
    assertEquals(Arrays.asList("files.read", "server.read"), loaded.scopes());
    assertEquals(revoked, loaded.revokedAt());
    assertEquals(used, loaded.lastUsedAt());
    assertTrue(loaded.isRevoked());
  }

  @Test
  void rejectsInvalidAndDuplicateScopesInRecords() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore.load(directory);
    String hash = TestHashes.hex(TestHashes.sha256("dmc_scope_token_value"));
    Files.write(
        directory.resolve("t-scope.json"),
        ("{\"id\":\"t-scope\",\"label\":\"x\",\"secretHash\":\""
                + hash
                + "\","
                + "\"scopes\":[\"not a scope\"]}")
            .getBytes(StandardCharsets.UTF_8));

    assertEquals(
        TokenStoreException.Reason.RECORD,
        assertThrows(TokenStoreException.class, () -> TokenStore.load(directory)).reason());

    Files.write(
        directory.resolve("t-scope.json"),
        ("{\"id\":\"t-scope\",\"label\":\"x\",\"secretHash\":\""
                + hash
                + "\","
                + "\"scopes\":[\"files.read\",\"files.read\"]}")
            .getBytes(StandardCharsets.UTF_8));
    assertEquals(
        TokenStoreException.Reason.RECORD,
        assertThrows(TokenStoreException.class, () -> TokenStore.load(directory)).reason());
  }

  @Test
  void persistsLastUsedAndRevocationWithAtomicMetadataUpdates() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    TokenRecord initial =
        new TokenRecord(
            "t-metadata", "metadata", TestHashes.sha256("dmc_metadata_token_value"), null);
    store.write(initial);
    Instant used = Instant.parse("2031-04-01T00:00:00Z");
    Instant revoked = Instant.parse("2031-04-02T00:00:00Z");

    TokenRecord afterUse = store.updateLastUsed(initial, used);
    assertEquals(used, afterUse.lastUsedAt());
    TokenRecord afterRevoke = store.revoke("t-metadata", revoked);
    assertEquals(revoked, afterRevoke.revokedAt());

    TokenRecord loaded = TokenStore.load(directory).records().get(0);
    assertEquals(used, loaded.lastUsedAt());
    assertEquals(revoked, loaded.revokedAt());
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path entry : stream) {
        assertFalse(entry.getFileName().toString().endsWith(".tmp"));
      }
    }
  }

  @Test
  void failsClosedWhenRecordChangesBeforeMetadataWrite() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    TokenRecord initial =
        new TokenRecord("t-race", "race", TestHashes.sha256("dmc_race_token_value"), null);
    store.write(initial);
    Files.write(
        directory.resolve("t-race.json"),
        ("{\"id\":\"t-race\",\"label\":\"changed\",\"secretHash\":\""
                + TestHashes.hex(initial.secretHash())
                + "\"}")
            .getBytes(StandardCharsets.UTF_8));

    TokenStoreException failure =
        assertThrows(
            TokenStoreException.class,
            () -> store.updateLastUsed(initial, Instant.parse("2031-05-01T00:00:00Z")));
    assertEquals(TokenStoreException.Reason.RECORD, failure.reason());
  }

  @Test
  void removesStaleTemporaryFilesDuringLoad() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore.load(directory);
    Path stale = directory.resolve(".token-t-stale-123.tmp");
    Files.write(stale, "metadata only".getBytes(StandardCharsets.UTF_8));

    TokenStore reloaded = TokenStore.load(directory);

    assertFalse(Files.exists(stale));
    assertTrue(reloaded.records().isEmpty());
    assertEquals(Collections.emptyList(), reloaded.records());
  }
}
