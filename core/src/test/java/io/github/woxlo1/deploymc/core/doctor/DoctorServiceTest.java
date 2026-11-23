package io.github.woxlo1.deploymc.core.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityCatalog;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.config.ConfigSchema;
import io.github.woxlo1.deploymc.core.config.ConfigValidationResult;
import io.github.woxlo1.deploymc.core.config.ConfigValidator;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxErrorCode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
  private static final DoctorService SERVICE = new DoctorService();

  @TempDir Path temp;

  @Test
  void reportsAllThirteenChecksInStableOrder() throws Exception {
    DoctorReport report = run(inputs(freePort()));

    assertEquals(
        Arrays.asList(
            "environment",
            "capabilities",
            "bind",
            "auth.mode",
            "auth.tunnel",
            "sandbox",
            "snapshot",
            "deployment",
            "platform.jar-replacement",
            "console.capture",
            "configuration",
            "localization",
            "updates"),
        DoctorService.checkIds());
    List<String> ids = new ArrayList<String>();
    for (DoctorCheck check : report.checks()) {
      ids.add(check.id());
    }
    assertEquals(DoctorService.checkIds(), ids);
    assertEquals("1.0", report.schemaVersion());
    assertEquals("limited", report.scope());
    assertEquals(NOW.toString(), report.generatedAt());
  }

  @Test
  void implementedChecksReachPassWhenProductInputsAreClean() throws Exception {
    int port = freePort();
    DoctorReport report =
        run(
            inputs(port)
                .sandboxResolver(new SandboxResolver(temp))
                .configValidation(validConfig()));

    assertEquals(DoctorStatus.PASS, check(report, "environment").status());
    assertEquals(DoctorStatus.PASS, check(report, "capabilities").status());
    assertEquals(DoctorStatus.PASS, check(report, "bind").status());
    assertEquals(DoctorStatus.PASS, check(report, "auth.mode").status());
    assertEquals(DoctorStatus.PASS, check(report, "sandbox").status());
    assertEquals(DoctorStatus.PASS, check(report, "configuration").status());
    assertEquals(DoctorStatus.PASS, check(report, "localization").status());
    assertEquals(DoctorStatus.WARN, check(report, "snapshot").status());
    assertFalse(check(report, "snapshot").implemented());
  }

  @Test
  void unimplementedChecksAreWarnAndSaySoInBothLocales() throws Exception {
    for (Locale locale : Arrays.asList(Locale.ROOT, Locale.JAPANESE)) {
      DoctorReport report = run(inputs(freePort()).locale(locale).configValidation(validConfig()));
      for (String id :
          Arrays.asList(
              "auth.tunnel",
              "platform.jar-replacement",
              "console.capture",
              "deployment",
              "updates")) {
        DoctorCheck check = check(report, id);
        assertEquals(DoctorStatus.WARN, check.status(), id + "/" + locale);
        assertFalse(check.implemented(), id + "/" + locale);
        assertTrue(isNotImplemented(check.detail()), id + "/" + locale);
        assertFalse(check.detail().isEmpty(), id + "/" + locale);
        assertFalse(check.remediation().isEmpty(), id + "/" + locale);
      }
    }
  }

  @Test
  void nonLoopbackBindFailsWithoutEchoingAddress() throws Exception {
    DoctorEnvironment environment = environmentBuilder(freePort(), false).build();
    DoctorReport report = run(inputs(freePort()).environment(environment));

    DoctorCheck bind = check(report, "bind");
    assertEquals(DoctorStatus.FAIL, bind.status());
    assertTrue(bind.implemented());
    assertFalse(report.toJson().contains("0.0.0.0"));
    assertFalse(report.renderHuman(Locale.ROOT).toString().contains("0.0.0.0"));
  }

  @Test
  void loopbackPortOwnedByAnotherProcessFails() throws Exception {
    try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      int port = occupied.getLocalPort();
      DoctorCheck bind = check(run(inputs(port)), "bind");
      assertEquals(DoctorStatus.FAIL, bind.status());
      assertEquals(Boolean.TRUE, bind.evidence().get("portInUse"));
    }
  }

  @Test
  void ownedTransportPortPassesWithoutReProbing() throws Exception {
    assertFalse(DoctorEnvironment.builder().build().bindPortOwnedByHost());
    try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      int port = occupied.getLocalPort();
      for (Locale locale : Arrays.asList(Locale.ROOT, Locale.JAPANESE)) {
        DoctorEnvironment environment =
            environmentBuilder(port, true).bindPortOwnedByHost(true).build();
        DoctorCheck bind = check(run(inputs(port).locale(locale).environment(environment)), "bind");

        assertEquals(DoctorStatus.PASS, bind.status(), locale.toString());
        assertTrue(bind.implemented(), locale.toString());
        assertEquals(Integer.valueOf(port), bind.evidence().get("port"));
        assertEquals(Boolean.TRUE, bind.evidence().get("portOwned"));
        assertFalse(bind.evidence().containsKey("loopbackProbe"));
        assertFalse(bind.evidence().containsKey("portInUse"));
        assertEquals(
            DoctorMessages.format(locale, DoctorMessages.DETAIL_BIND_OWNED, Integer.valueOf(port)),
            bind.detail());
        assertEquals(
            DoctorMessages.format(locale, DoctorMessages.REMEDIATION_BIND), bind.remediation());
      }
    }
  }

  @Test
  void invalidPortFailsClosed() throws Exception {
    DoctorReport report = run(inputs(70000));
    assertEquals(DoctorStatus.FAIL, check(report, "bind").status());
  }

  @Test
  void missingEnvironmentValuesFail() throws Exception {
    DoctorEnvironment incomplete =
        environmentBuilder(freePort(), true).deployMcVersion("").apiVersion(null).build();
    DoctorReport report = run(inputs(freePort()).environment(incomplete));

    assertEquals(DoctorStatus.FAIL, check(report, "environment").status());
    assertFalse(check(report, "environment").implemented());
    assertEquals(Boolean.FALSE, check(report, "environment").evidence().get("valuesPresent"));
  }

  @Test
  void secretLikeAndPathLikeHostValuesAreNeverStored() throws Exception {
    DoctorEnvironment hostile =
        environmentBuilder(freePort(), true)
            .platformInfo(
                new PlatformInfo(
                    "Bearer abc123 C:\\secrets\\key", "1.21.1", "1.21.1", "21.0.1", "TestOS"))
            .build();
    DoctorReport report =
        run(
            inputs(freePort())
                .environment(hostile)
                .configValidation(validConfig())
                .sandboxResolver(new SandboxResolver(temp)));

    for (String output :
        Arrays.asList(
            report.toJson(),
            report.renderHuman(Locale.ROOT).toString(),
            report.redacted().toJson(),
            report.redacted().renderHuman(Locale.ROOT).toString())) {
      assertFalse(output.contains("abc123"), output);
      assertFalse(output.contains("C:\\secrets"), output);
      assertFalse(output.contains("0.0.0.0"), output);
      assertFalse(output.contains("Bearer "), output);
    }
  }

  @Test
  void capabilitiesWarnForUnavailableAndUnknownEntries() throws Exception {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    for (String key : CapabilityCatalog.keys()) {
      states.put(key, CapabilityState.AVAILABLE);
    }
    states.put("log.capture", CapabilityState.UNAVAILABLE);
    states.put("capability.unknown", CapabilityState.UNKNOWN);
    DoctorReport report = run(inputs(freePort()).capabilities(PlatformCapabilities.of(states)));

    DoctorCheck capabilities = check(report, "capabilities");
    assertEquals(DoctorStatus.WARN, capabilities.status());
    assertTrue(capabilities.implemented());
    assertEquals(Integer.valueOf(1), capabilities.evidence().get("unavailable"));
    assertEquals(Integer.valueOf(1), capabilities.evidence().get("unknown"));
  }

  @Test
  void sandboxUsesRealResolverAndDetectsProtectedPathProbe(@TempDir Path sandboxRoot)
      throws Exception {
    SandboxResolver resolver = new SandboxResolver(sandboxRoot);
    // The doctor must rely on the product resolver, not on a reconstructed path.
    try {
      resolver.resolve(".deploymc", SandboxAccessMode.READ);
      throw new AssertionError("protected probe was accepted");
    } catch (SandboxRejectionException rejected) {
      assertEquals(SandboxErrorCode.SANDBOX_PROTECTED, rejected.code());
    }

    DoctorCheck sandbox = check(run(inputs(freePort()).sandboxResolver(resolver)), "sandbox");
    assertEquals(DoctorStatus.PASS, sandbox.status());
    assertTrue(sandbox.implemented());
    assertTrue(sandbox.evidence().containsKey("freeBytes"));
  }

  @Test
  void sandboxFailsWhenRootDisappears(@TempDir Path sandboxRoot) throws Exception {
    Path missing = sandboxRoot.resolve("removed-root");
    Files.createDirectories(missing);
    SandboxResolver resolver = new SandboxResolver(missing);
    Files.delete(missing);

    DoctorCheck sandbox = check(run(inputs(freePort()).sandboxResolver(resolver)), "sandbox");
    assertEquals(DoctorStatus.FAIL, sandbox.status());
    assertTrue(sandbox.implemented());
  }

  @Test
  void configurationErrorsFailAndWarningsWarn() throws Exception {
    DoctorReport errors =
        run(
            inputs(freePort())
                .configValidation(
                    ConfigValidator.validate(
                        ConfigYamlParser.parseSingleDocument(
                            "config-version: 1\nauth:\n  mode: invalid-mode\n"),
                        ConfigSchema.load(),
                        Locale.ROOT)));
    assertEquals(DoctorStatus.FAIL, check(errors, "configuration").status());
    assertTrue(((Integer) check(errors, "configuration").evidence().get("errors")) > 0);

    DoctorReport clean = run(inputs(freePort()).configValidation(validConfig()));
    assertEquals(DoctorStatus.PASS, check(clean, "configuration").status());
  }

  @Test
  void missingLocalizationCountWarnsAndUnsupervisedCountIsNotPass() throws Exception {
    DoctorCheck missing = check(run(inputs(freePort()).missingLocalizationKeys(3)), "localization");
    assertEquals(DoctorStatus.WARN, missing.status());
    assertTrue(missing.implemented());
    assertEquals(Integer.valueOf(3), missing.evidence().get("missingKeys"));

    DoctorCheck unknown =
        check(run(inputs(freePort()).missingLocalizationKeys(-1)), "localization");
    assertEquals(DoctorStatus.WARN, unknown.status());
    assertFalse(unknown.implemented());
    assertTrue(isNotImplemented(unknown.detail()));
  }

  @Test
  void snapshotStorePassVerifyWarnSizeFailAndClosedFail(@TempDir Path dataRoot) throws Exception {
    SandboxResolver resolver = new SandboxResolver(dataRoot);
    SnapshotStoreConfig limits = SnapshotStoreConfig.defaults();
    try (SnapshotStore store = SnapshotStore.open(dataRoot, resolver, limits)) {
      DoctorReport clean =
          run(
              inputs(freePort())
                  .sandboxResolver(resolver)
                  .snapshotStore(store)
                  .snapshotLimits(limits));
      assertEquals(DoctorStatus.PASS, check(clean, "snapshot").status());
      assertTrue(check(clean, "snapshot").evidence().containsKey("storeBytes"));

      Path invalidBlob = dataRoot.resolve(".deploymc/history/objects/zz/not-a-blob");
      Files.createDirectories(invalidBlob.getParent());
      Files.write(invalidBlob, new byte[] {1});
      DoctorReport corrupt =
          run(
              inputs(freePort())
                  .sandboxResolver(resolver)
                  .snapshotStore(store)
                  .snapshotLimits(limits));
      assertEquals(DoctorStatus.WARN, check(corrupt, "snapshot").status());
      assertTrue(((Integer) check(corrupt, "snapshot").evidence().get("verifyIssues")) > 0);

      DoctorReport oversized =
          run(
              inputs(freePort())
                  .sandboxResolver(resolver)
                  .snapshotStore(store)
                  .snapshotLimits(SnapshotStoreConfig.builder().maxStoreBytes(1L).build()));
      assertEquals(DoctorStatus.FAIL, check(oversized, "snapshot").status());
      assertEquals(Long.valueOf(1L), check(oversized, "snapshot").evidence().get("maxBytes"));

      store.registerDeployment("fake-deployment-id");
      DoctorReport deployment =
          run(
              inputs(freePort())
                  .sandboxResolver(resolver)
                  .snapshotStore(store)
                  .snapshotLimits(limits));
      assertEquals(DoctorStatus.WARN, check(deployment, "deployment").status());
      assertFalse(check(deployment, "deployment").implemented());
      assertEquals(
          Boolean.TRUE, check(deployment, "deployment").evidence().get("activeDeployment"));
    }

    SnapshotStore closed = SnapshotStore.open(dataRoot, resolver, limits);
    closed.close();
    DoctorReport report =
        run(
            inputs(freePort())
                .sandboxResolver(resolver)
                .snapshotStore(closed)
                .snapshotLimits(limits));
    assertEquals(DoctorStatus.FAIL, check(report, "snapshot").status());
    assertTrue(check(report, "snapshot").detail().length() <= 512);
  }

  @Test
  void authModeWarnsForReservedModes() throws Exception {
    DoctorEnvironment reserved =
        environmentBuilder(freePort(), true).authMode("cloudflare").build();
    DoctorReport report = run(inputs(freePort()).environment(reserved));
    assertEquals(DoctorStatus.WARN, check(report, "auth.mode").status());
    assertEquals(Boolean.FALSE, check(report, "auth.mode").evidence().get("tokenMode"));
  }

  @Test
  void resultsAreDeterministicForSameInputs() throws Exception {
    int port = freePort();
    DoctorInputs.Builder builder = inputs(port);
    DoctorReport first = run(builder);
    DoctorReport second = run(builder);

    assertEquals(first.toJson(), second.toJson());
    assertEquals(first.renderHuman(Locale.ROOT), second.renderHuman(Locale.ROOT));
  }

  private static DoctorReport run(DoctorInputs.Builder builder) {
    return SERVICE.run(builder.build());
  }

  private static DoctorInputs.Builder inputs(int port) throws Exception {
    return DoctorInputs.builder()
        .clock(Clock.fixed(NOW, ZoneOffset.UTC))
        .locale(Locale.ROOT)
        .environment(environmentBuilder(port, true).build())
        .capabilities(allAvailable())
        .configValidation(validConfig())
        .missingLocalizationKeys(0);
  }

  private static DoctorEnvironment.Builder environmentBuilder(int port, boolean loopback) {
    return DoctorEnvironment.builder()
        .platformInfo(new PlatformInfo("Paper", "1.21.1", "1.21.1", "21.0.1", "TestOS"))
        .deployMcVersion("0.1.0-test")
        .apiVersion("0.1.0-test")
        .bindAddress(loopback ? "127.0.0.1" : "0.0.0.0")
        .bindPort(port)
        .loopbackOnly(loopback)
        .authMode("token")
        .language("en");
  }

  private static PlatformCapabilities allAvailable() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    for (String key : CapabilityCatalog.keys()) {
      states.put(key, CapabilityState.AVAILABLE);
    }
    return PlatformCapabilities.of(states);
  }

  private static ConfigValidationResult validConfig() throws Exception {
    return ConfigValidator.validate(
        ConfigYamlParser.parseSingleDocument("config-version: 1"),
        ConfigSchema.load(),
        Locale.ROOT);
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private static DoctorCheck check(DoctorReport report, String id) {
    for (DoctorCheck check : report.checks()) {
      if (id.equals(check.id())) {
        return check;
      }
    }
    throw new AssertionError("missing doctor check: " + id);
  }

  private static boolean isNotImplemented(String detail) {
    return detail.contains("NOT IMPLEMENTED") || detail.contains("\u672a\u5b9f\u88c5");
  }
}
