package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.api.CapabilityState;
import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import io.github.woxlo1.deploymc.core.doctor.DoctorCheck;
import io.github.woxlo1.deploymc.core.doctor.DoctorReport;
import io.github.woxlo1.deploymc.core.doctor.DoctorService;
import io.github.woxlo1.deploymc.core.doctor.DoctorStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server-free coverage for the host-supplied doctor inputs. */
class DoctorRuntimeTest {

  private static final PlatformInfo PLATFORM_INFO =
      new PlatformInfo("Paper", "1.21.1", "1.21.1", "21", "test-os");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  private ConfigManager manager;
  private StartupPlan plan;

  @BeforeEach
  void setUp() throws Exception {
    Path configFile = temporaryDirectory.resolve("config.yml");
    DefaultConfigWriter.writeIfMissing(configFile);
    manager = new ConfigManager(configFile);
    ConfigView view = manager.load();
    plan = StartupPlan.fromConfig(view, "0.1.0-test");
  }

  @Test
  void runsAllThirteenChecksWithHostFactsAndNoFabricatedSubsystems() {
    DoctorReport report = runtime(PlatformCapabilities.empty()).run();

    List<String> ids = new ArrayList<String>();
    for (DoctorCheck check : report.checks()) {
      ids.add(check.id());
    }
    assertEquals(DoctorService.checkIds(), ids);
    assertEquals("2026-11-23T00:00:00Z", report.generatedAt());

    DoctorCheck bind = check(report, DoctorService.ID_BIND);
    assertEquals(DoctorStatus.PASS, bind.status());
    assertEquals(Boolean.TRUE, bind.evidence().get("portOwned"));
    assertEquals(Integer.valueOf(8080), bind.evidence().get("port"));

    DoctorCheck sandbox = check(report, DoctorService.ID_SANDBOX);
    assertEquals(DoctorStatus.WARN, sandbox.status());
    assertFalse(sandbox.implemented());
    assertTrue(sandbox.evidence().isEmpty());

    DoctorCheck snapshot = check(report, DoctorService.ID_SNAPSHOT);
    assertEquals(DoctorStatus.WARN, snapshot.status());
    assertFalse(snapshot.implemented());
    assertTrue(snapshot.evidence().isEmpty());

    DoctorCheck deployment = check(report, DoctorService.ID_DEPLOYMENT);
    assertEquals(Boolean.FALSE, deployment.evidence().get("restartCapability"));
    assertFalse(deployment.implemented());

    DoctorCheck updates = check(report, DoctorService.ID_UPDATES);
    assertEquals(Boolean.FALSE, updates.evidence().get("updateCheckEnabled"));
    assertFalse(updates.implemented());

    DoctorCheck localization = check(report, DoctorService.ID_LOCALIZATION);
    assertEquals(DoctorStatus.PASS, localization.status());
    assertEquals(Integer.valueOf(0), localization.evidence().get("missingKeys"));
  }

  @Test
  void propagatesTheHostRestartCapabilityWithoutProbingTheServer() {
    Map<String, CapabilityState> states = new LinkedHashMap<String, CapabilityState>();
    states.put(CapabilityKeys.SERVER_RESTART_SCRIPT, CapabilityState.AVAILABLE);

    DoctorReport report = runtime(PlatformCapabilities.of(states)).run();

    DoctorCheck deployment = check(report, DoctorService.ID_DEPLOYMENT);
    assertEquals(Boolean.TRUE, deployment.evidence().get("restartCapability"));
    assertFalse(deployment.implemented());
  }

  private DoctorRuntime runtime(PlatformCapabilities capabilities) {
    return new DoctorRuntime(
        manager,
        plan,
        PLATFORM_INFO,
        capabilities,
        Locale.ENGLISH,
        "en",
        null,
        LocalizedMessages.of(Locale.ENGLISH),
        CLOCK);
  }

  private static DoctorCheck check(DoctorReport report, String id) {
    for (DoctorCheck check : report.checks()) {
      if (id.equals(check.id())) {
        return check;
      }
    }
    throw new AssertionError("missing doctor check: " + id);
  }
}
