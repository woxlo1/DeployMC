package io.github.woxlo1.deploymc.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused WP-3.4 token-owner and closed-registry scope tests. */
class TokenOwnerContractTest {

  @TempDir Path tempDir;

  @Test
  void loadsLegacyRecordWithoutOwner() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore.load(directory);
    String hash = TestHashes.hex(TestHashes.sha256("dmc_legacy_owner_value"));
    Files.write(
        directory.resolve("t-legacy.json"),
        ("{\"id\":\"t-legacy\",\"label\":\"old\",\"secretHash\":\"" + hash + "\"}")
            .getBytes(StandardCharsets.UTF_8));

    TokenRecord loaded = TokenStore.load(directory).records().get(0);

    assertNull(loaded.owner());
    assertFalse(loaded.identity().claims().containsKey("token.owner"));
    assertFalse(loaded.identity().adminEquivalent());
  }

  @Test
  void ownerRoundTripsThroughWriteReloadAndMetadataUpdates() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    TokenRecord initial =
        new TokenRecord(
            "t-owned",
            "ci-deployer",
            "ci-service-token",
            TestHashes.sha256("dmc_owned_token_value"),
            Instant.parse("2030-01-01T00:00:00Z"),
            Arrays.asList("files.read", "server.read"),
            null,
            null);
    store.write(initial);

    String serialized =
        new String(Files.readAllBytes(directory.resolve("t-owned.json")), StandardCharsets.UTF_8);
    assertTrue(serialized.indexOf("\"owner\"") < serialized.indexOf("\"secretHash\""));

    TokenStore reloadedStore = TokenStore.load(directory);
    TokenRecord loaded = reloadedStore.records().get(0);
    assertEquals("ci-service-token", loaded.owner());
    TokenRecord used = reloadedStore.updateLastUsed(loaded, Instant.parse("2029-01-01T00:00:00Z"));
    assertEquals("ci-service-token", used.owner());
    TokenRecord revoked = reloadedStore.revoke("t-owned", Instant.parse("2029-02-01T00:00:00Z"));
    assertEquals("ci-service-token", revoked.owner());
    assertEquals("ci-service-token", TokenStore.load(directory).records().get(0).owner());
  }

  @Test
  void identityExposesOwnerWithoutAdminEquivalenceOrSecret() {
    byte[] hash = TestHashes.sha256("dmc_identity_owner_value");
    TokenRecord record =
        new TokenRecord(
            "t-identity",
            "label",
            "owner-key",
            hash,
            null,
            Collections.singletonList("files.read"),
            null,
            null);

    Identity identity = record.identity();

    assertFalse(identity.adminEquivalent());
    assertEquals("owner-key", identity.claims().get("token.owner"));
    assertEquals("t-identity", identity.claims().get("token.id"));
    assertEquals("label", identity.claims().get("token.label"));
    assertEquals("token", identity.claims().get("auth.method"));
    assertEquals("files.read", identity.claims().get("token.scopes"));
    assertFalse(identity.claims().containsKey("secretHash"));
    assertFalse(identity.toString().contains(TestHashes.hex(hash)));
  }

  @Test
  void rejectsWildcardAndUnknownScopes() {
    assertTrue(TokenRecord.isValidScope("files.read"));
    assertTrue(TokenRecord.isValidScope("deployments.create.jar"));
    assertFalse(TokenRecord.isValidScope("*"));
    assertFalse(TokenRecord.isValidScope("roles.*"));
    assertFalse(TokenRecord.isValidScope("server.status"));
    assertFalse(TokenRecord.isValidScope("not a scope"));

    byte[] hash = TestHashes.sha256("dmc_scope_owner_value");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TokenRecord(
                "t-wild",
                "x",
                "owner-key",
                hash,
                null,
                Collections.singletonList("*"),
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TokenRecord(
                "t-unknown",
                "x",
                "owner-key",
                hash,
                null,
                Collections.singletonList("server.status"),
                null,
                null));
  }

  @Test
  void legacySevenArgConstructorStillWorks() throws Exception {
    TokenRecord record =
        new TokenRecord(
            "t-seven",
            "seven",
            TestHashes.sha256("dmc_seven_arg_value"),
            null,
            Collections.singletonList("files.read"),
            null,
            null);

    assertNull(record.owner());
    assertFalse(record.identity().adminEquivalent());
    assertEquals("files.read", record.identity().claims().get("token.scopes"));
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    store.write(record);
    assertNull(TokenStore.load(directory).records().get(0).owner());
  }

  @Test
  void provisionerPersistsSuppliedOwnerAndRejectsInvalidOwners() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore store = TokenStore.load(directory);
    TokenProvisioner provisioner =
        new TokenProvisioner(store, Clock.systemUTC(), new SecureRandom());

    CreatedToken created =
        provisioner.create(
            "ci", "ci-service-token", Duration.ofDays(1), Arrays.asList("files.read"));
    assertEquals("ci-service-token", created.record().owner());
    assertEquals("ci-service-token", TokenStore.load(directory).records().get(0).owner());

    assertThrows(
        IllegalArgumentException.class, () -> provisioner.create("ci", "", Duration.ofDays(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> provisioner.create("ci", "bad\nowner", Duration.ofDays(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> provisioner.create("ci", longOwner(), Duration.ofDays(1)));
  }

  @Test
  void storeRejectsMalformedOwnerFields() throws Exception {
    Path directory = tempDir.resolve("tokens.d");
    TokenStore.load(directory);

    assertRejectedOwner(directory, "5");
    assertRejectedOwner(directory, "\"\"");
    assertRejectedOwner(directory, "\"" + longOwner() + "\"");
    assertThrows(IllegalArgumentException.class, () -> TokenRecord.validateOwner("bad\u0000owner"));
  }

  private static void assertRejectedOwner(Path directory, String ownerJson) throws Exception {
    Path file = directory.resolve("t-owner.json");
    String hash = TestHashes.hex(TestHashes.sha256("dmc_malformed_owner_value"));
    Files.write(
        file,
        ("{\"id\":\"t-owner\",\"label\":\"x\",\"owner\":"
                + ownerJson
                + ",\"secretHash\":\""
                + hash
                + "\"}")
            .getBytes(StandardCharsets.UTF_8));
    assertEquals(
        TokenStoreException.Reason.RECORD,
        assertThrows(TokenStoreException.class, () -> TokenStore.load(directory)).reason());
    Files.delete(file);
  }

  private static String longOwner() {
    StringBuilder owner = new StringBuilder();
    for (int index = 0; index < 321; index++) {
      owner.append('a');
    }
    return owner.toString();
  }
}
