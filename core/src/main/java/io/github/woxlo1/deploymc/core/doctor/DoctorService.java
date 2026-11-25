package io.github.woxlo1.deploymc.core.doctor;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityCatalog;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.auth.cloudflare.JwtDiagnosticsSnapshot;
import io.github.woxlo1.deploymc.core.auth.cloudflare.JwtRejection;
import io.github.woxlo1.deploymc.core.config.ConfigValidationResult;
import io.github.woxlo1.deploymc.core.sandbox.SandboxAccessMode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxErrorCode;
import io.github.woxlo1.deploymc.core.sandbox.SandboxRejectionException;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStore;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreConfig;
import io.github.woxlo1.deploymc.core.snapshot.SnapshotStoreException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Runs the deterministic, limited-scope PART 36 doctor baseline. */
public final class DoctorService {

  public static final String ID_ENVIRONMENT = "environment";
  public static final String ID_CAPABILITIES = "capabilities";
  public static final String ID_BIND = "bind";
  public static final String ID_AUTH_MODE = "auth.mode";
  public static final String ID_AUTH_TUNNEL = "auth.tunnel";
  public static final String ID_AUTH_JWT = "auth.jwt";
  public static final String ID_SANDBOX = "sandbox";
  public static final String ID_SNAPSHOT = "snapshot";
  public static final String ID_DEPLOYMENT = "deployment";
  public static final String ID_JAR_REPLACEMENT = "platform.jar-replacement";
  public static final String ID_CONSOLE_CAPTURE = "console.capture";
  public static final String ID_CONFIGURATION = "configuration";
  public static final String ID_LOCALIZATION = "localization";
  public static final String ID_UPDATES = "updates";

  private static final List<String> CHECK_IDS =
      Collections.unmodifiableList(
          Arrays.asList(
              ID_ENVIRONMENT,
              ID_CAPABILITIES,
              ID_BIND,
              ID_AUTH_MODE,
              ID_AUTH_TUNNEL,
              ID_AUTH_JWT,
              ID_SANDBOX,
              ID_SNAPSHOT,
              ID_DEPLOYMENT,
              ID_JAR_REPLACEMENT,
              ID_CONSOLE_CAPTURE,
              ID_CONFIGURATION,
              ID_LOCALIZATION,
              ID_UPDATES));

  public DoctorService() {}

  /** Stable check identifiers in report order. */
  public static List<String> checkIds() {
    return CHECK_IDS;
  }

  /** Runs all checks without mutating server, deployment, or configuration state. */
  public DoctorReport run(DoctorInputs inputs) {
    Objects.requireNonNull(inputs, "inputs");
    Locale locale = inputs.locale();
    DoctorEnvironment environment = inputs.environment();
    SnapshotFacts snapshots = readSnapshotFacts(inputs);

    List<DoctorCheck> checks = new ArrayList<DoctorCheck>(CHECK_IDS.size());
    checks.add(environment(locale, environment));
    checks.add(capabilities(locale, inputs.capabilities()));
    checks.add(bind(locale, environment));
    checks.add(authMode(locale, environment));
    checks.add(
        DoctorCheck.of(
            ID_AUTH_TUNNEL,
            DoctorStatus.WARN,
            false,
            detail(locale, DoctorMessages.DETAIL_AUTH_TUNNEL),
            detail(locale, DoctorMessages.REMEDIATION_AUTH_TUNNEL)));
    checks.add(authJwt(locale, inputs.jwtDiagnosticsSnapshot()));
    checks.add(sandbox(locale, inputs.sandboxResolver()));
    checks.add(snapshot(locale, snapshots));
    checks.add(deployment(locale, environment, snapshots));
    checks.add(
        DoctorCheck.of(
            ID_JAR_REPLACEMENT,
            DoctorStatus.WARN,
            false,
            detail(locale, DoctorMessages.DETAIL_JAR_REPLACEMENT),
            detail(locale, DoctorMessages.REMEDIATION_JAR_REPLACEMENT)));
    checks.add(consoleCapture(locale, inputs.capabilities()));
    checks.add(configuration(locale, inputs.configValidation()));
    checks.add(localization(locale, environment, inputs.missingLocalizationKeys()));
    checks.add(updates(locale, environment));
    return DoctorReport.of(inputs.clock(), checks);
  }

  private static DoctorCheck environment(Locale locale, DoctorEnvironment environment) {
    PlatformInfo info = environment.platformInfo();
    String[] values =
        info == null
            ? new String[] {"", "", "", "", "", "", ""}
            : new String[] {
              info.softwareName(),
              info.softwareVersion(),
              info.minecraftVersion(),
              info.javaVersion(),
              info.operatingSystem(),
              environment.deployMcVersion(),
              environment.apiVersion()
            };
    boolean complete = true;
    for (String value : values) {
      if (value == null || value.trim().isEmpty()) {
        complete = false;
        break;
      }
    }
    return DoctorCheck.of(
        ID_ENVIRONMENT,
        complete ? DoctorStatus.PASS : DoctorStatus.FAIL,
        complete,
        detail(
            locale,
            complete
                ? DoctorMessages.DETAIL_ENVIRONMENT_PASS
                : DoctorMessages.DETAIL_ENVIRONMENT_FAIL,
            (Object[]) values),
        detail(locale, DoctorMessages.REMEDIATION_ENVIRONMENT),
        evidence("valuesPresent", complete));
  }

  private static DoctorCheck capabilities(Locale locale, PlatformCapabilities capabilities) {
    int unavailable = 0;
    int unknown = 0;
    for (String key : CapabilityCatalog.keys()) {
      switch (capabilities.state(key)) {
        case UNAVAILABLE:
          unavailable++;
          break;
        case UNKNOWN:
          unknown++;
          break;
        default:
          break;
      }
    }
    for (String key : capabilities.asMap().keySet()) {
      if (!CapabilityCatalog.keys().contains(key)) {
        unknown++;
      }
    }
    boolean complete = unavailable == 0 && unknown == 0;
    return DoctorCheck.of(
        ID_CAPABILITIES,
        complete ? DoctorStatus.PASS : DoctorStatus.WARN,
        true,
        detail(
            locale,
            complete
                ? DoctorMessages.DETAIL_CAPABILITIES_PASS
                : DoctorMessages.DETAIL_CAPABILITIES_WARN,
            Integer.valueOf(CapabilityCatalog.keys().size()),
            Integer.valueOf(unavailable),
            Integer.valueOf(unknown)),
        detail(locale, DoctorMessages.REMEDIATION_CAPABILITIES),
        evidence("unavailable", Integer.valueOf(unavailable), "unknown", Integer.valueOf(unknown)));
  }

  private static DoctorCheck bind(Locale locale, DoctorEnvironment environment) {
    int port = environment.bindPort();
    Map<String, Object> portEvidence = evidence("port", Integer.valueOf(port));
    if (!environment.loopbackOnly()) {
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_FAIL_NON_LOOPBACK),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          portEvidence);
    }
    if (port < 1 || port > 65535) {
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_FAIL_PORT, Integer.valueOf(port)),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          portEvidence);
    }
    if (environment.bindPortOwnedByHost()) {
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.PASS,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_OWNED, Integer.valueOf(port)),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          evidence("port", Integer.valueOf(port), "portOwned", Boolean.TRUE));
    }
    ServerSocket socket = null;
    try {
      socket = new ServerSocket();
      socket.setReuseAddress(false);
      socket.setSoTimeout(250);
      socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1);
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.PASS,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_PASS, Integer.valueOf(port)),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          evidence("port", Integer.valueOf(port), "loopbackProbe", Boolean.TRUE));
    } catch (BindException inUse) {
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_FAIL_IN_USE, Integer.valueOf(port)),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          evidence("port", Integer.valueOf(port), "portInUse", Boolean.TRUE));
    } catch (IOException | SecurityException inconclusive) {
      return DoctorCheck.of(
          ID_BIND,
          DoctorStatus.WARN,
          true,
          detail(locale, DoctorMessages.DETAIL_BIND_WARN, Integer.valueOf(port)),
          detail(locale, DoctorMessages.REMEDIATION_BIND),
          portEvidence);
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ignored) {
          // The probe never serves requests; a close failure cannot make the check safer.
        }
      }
    }
  }

  private static DoctorCheck authMode(Locale locale, DoctorEnvironment environment) {
    String mode = value(environment.authMode());
    boolean token = "token".equals(mode);
    boolean cloudflare = "cloudflare".equals(mode);
    boolean oneTime = "local-onetime".equals(mode);
    String detailKey =
        token
            ? DoctorMessages.DETAIL_AUTH_MODE_PASS
            : (cloudflare
                ? DoctorMessages.DETAIL_AUTH_MODE_CLOUDFLARE_PASS
                : (oneTime
                    ? DoctorMessages.DETAIL_AUTH_MODE_ONE_TIME_PASS
                    : DoctorMessages.DETAIL_AUTH_MODE_WARN));
    return DoctorCheck.of(
        ID_AUTH_MODE,
        token || cloudflare || oneTime ? DoctorStatus.PASS : DoctorStatus.WARN,
        true,
        detail(locale, detailKey, mode),
        detail(locale, DoctorMessages.REMEDIATION_AUTH_MODE),
        evidence("tokenMode", Boolean.valueOf(token)));
  }

  private static DoctorCheck authJwt(Locale locale, JwtDiagnosticsSnapshot snapshot) {
    if (snapshot == null) {
      return DoctorCheck.of(
          ID_AUTH_JWT,
          DoctorStatus.WARN,
          false,
          detail(locale, DoctorMessages.DETAIL_AUTH_JWT_NO_DIAGNOSTICS),
          detail(locale, DoctorMessages.REMEDIATION_AUTH_JWT));
    }
    Map<String, Long> counts = closedRejectionCounts(snapshot);
    Long required = counts.get("auth.required");
    Long invalid = counts.get("auth.invalid");
    Long expired = counts.get("auth.expired");
    Long notYetValid = counts.get("auth.not-yet-valid");
    Long jwksUnavailable = counts.get("auth.jwks-unavailable");
    long total =
        required.longValue()
            + invalid.longValue()
            + expired.longValue()
            + notYetValid.longValue()
            + jwksUnavailable.longValue();
    return DoctorCheck.of(
        ID_AUTH_JWT,
        total == 0L ? DoctorStatus.PASS : DoctorStatus.WARN,
        true,
        detail(
            locale,
            DoctorMessages.DETAIL_AUTH_JWT_COUNTS,
            required,
            invalid,
            expired,
            notYetValid,
            jwksUnavailable),
        detail(locale, DoctorMessages.REMEDIATION_AUTH_JWT));
  }

  private static Map<String, Long> closedRejectionCounts(JwtDiagnosticsSnapshot snapshot) {
    Map<String, Long> counts = new LinkedHashMap<String, Long>();
    for (JwtRejection reason : JwtRejection.values()) {
      counts.put(reason.errorCode(), Long.valueOf(0L));
    }
    for (Map.Entry<String, Long> entry : snapshot.countsByReason().entrySet()) {
      JwtRejection reason;
      try {
        reason = JwtRejection.valueOf(entry.getKey());
      } catch (IllegalArgumentException unknown) {
        continue;
      }
      Long count = entry.getValue();
      if (count == null || count.longValue() < 0L) {
        continue;
      }
      String code = reason.errorCode();
      counts.put(code, Long.valueOf(counts.get(code).longValue() + count.longValue()));
    }
    return counts;
  }

  private static DoctorCheck sandbox(Locale locale, SandboxResolver resolver) {
    if (resolver == null) {
      return DoctorCheck.of(
          ID_SANDBOX,
          DoctorStatus.WARN,
          false,
          detail(locale, DoctorMessages.DETAIL_SANDBOX_NO_RESOLVER),
          detail(locale, DoctorMessages.REMEDIATION_SANDBOX));
    }
    Path root = resolver.root();
    if (root == null
        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
        || !Files.isWritable(root)) {
      return DoctorCheck.of(
          ID_SANDBOX,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_SANDBOX_FAIL_ROOT),
          detail(locale, DoctorMessages.REMEDIATION_SANDBOX));
    }
    try {
      resolver.resolve(".deploymc", SandboxAccessMode.READ);
      return DoctorCheck.of(
          ID_SANDBOX,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_SANDBOX_FAIL_PROTECTED),
          detail(locale, DoctorMessages.REMEDIATION_SANDBOX));
    } catch (SandboxRejectionException rejected) {
      if (rejected.code() != SandboxErrorCode.SANDBOX_PROTECTED) {
        return DoctorCheck.of(
            ID_SANDBOX,
            DoctorStatus.FAIL,
            true,
            detail(locale, DoctorMessages.DETAIL_SANDBOX_FAIL_PROTECTED),
            detail(locale, DoctorMessages.REMEDIATION_SANDBOX));
      }
    }
    long freeBytes = -1L;
    try {
      freeBytes = Files.getFileStore(root).getUsableSpace();
    } catch (IOException | SecurityException unavailable) {
      freeBytes = -1L;
    }
    if (freeBytes < 0L) {
      return DoctorCheck.of(
          ID_SANDBOX,
          DoctorStatus.WARN,
          true,
          detail(locale, DoctorMessages.DETAIL_SANDBOX_NO_SPACE),
          detail(locale, DoctorMessages.REMEDIATION_SANDBOX));
    }
    return DoctorCheck.of(
        ID_SANDBOX,
        DoctorStatus.PASS,
        true,
        detail(locale, DoctorMessages.DETAIL_SANDBOX_PASS),
        detail(locale, DoctorMessages.REMEDIATION_SANDBOX),
        evidence("freeBytes", Long.valueOf(freeBytes)));
  }

  private static DoctorCheck snapshot(Locale locale, SnapshotFacts facts) {
    if (!facts.supplied) {
      return DoctorCheck.of(
          ID_SNAPSHOT,
          DoctorStatus.WARN,
          false,
          detail(locale, DoctorMessages.DETAIL_SNAPSHOT_NO_STORE),
          detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT));
    }
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("verifyIssues", Integer.valueOf(facts.verifyIssues));
    if (facts.storeBytes >= 0L) {
      evidence.put("storeBytes", Long.valueOf(facts.storeBytes));
    }
    evidence.put("maxBytes", Long.valueOf(facts.maxBytes));
    if (facts.unavailable) {
      return DoctorCheck.of(
          ID_SNAPSHOT,
          DoctorStatus.FAIL,
          true,
          detail(locale, DoctorMessages.DETAIL_SNAPSHOT_UNAVAILABLE),
          detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT),
          evidence);
    }
    if (facts.storeBytes >= 0L && facts.storeBytes > facts.maxBytes) {
      return DoctorCheck.of(
          ID_SNAPSHOT,
          DoctorStatus.FAIL,
          true,
          detail(
              locale,
              DoctorMessages.DETAIL_SNAPSHOT_SIZE,
              Long.valueOf(facts.storeBytes),
              Long.valueOf(facts.maxBytes)),
          detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT),
          evidence);
    }
    if (facts.storeBytes < 0L) {
      return DoctorCheck.of(
          ID_SNAPSHOT,
          DoctorStatus.WARN,
          true,
          detail(locale, DoctorMessages.DETAIL_SNAPSHOT_NO_SIZE),
          detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT),
          evidence);
    }
    if (facts.verifyIssues > 0) {
      return DoctorCheck.of(
          ID_SNAPSHOT,
          DoctorStatus.WARN,
          true,
          detail(
              locale, DoctorMessages.DETAIL_SNAPSHOT_VERIFY, Integer.valueOf(facts.verifyIssues)),
          detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT),
          evidence);
    }
    return DoctorCheck.of(
        ID_SNAPSHOT,
        DoctorStatus.PASS,
        true,
        detail(locale, DoctorMessages.DETAIL_SNAPSHOT_PASS),
        detail(locale, DoctorMessages.REMEDIATION_SNAPSHOT),
        evidence);
  }

  private static DoctorCheck deployment(
      Locale locale, DoctorEnvironment environment, SnapshotFacts facts) {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("restartCapability", Boolean.valueOf(environment.restartCapabilityAvailable()));
    if (facts.activeKnown) {
      evidence.put("activeDeployment", Boolean.valueOf(facts.activeDeployment));
    }
    return DoctorCheck.of(
        ID_DEPLOYMENT,
        DoctorStatus.WARN,
        false,
        detail(locale, DoctorMessages.DETAIL_DEPLOYMENT),
        detail(locale, DoctorMessages.REMEDIATION_DEPLOYMENT),
        evidence);
  }

  private static DoctorCheck consoleCapture(Locale locale, PlatformCapabilities capabilities) {
    return DoctorCheck.of(
        ID_CONSOLE_CAPTURE,
        DoctorStatus.WARN,
        false,
        detail(locale, DoctorMessages.DETAIL_CONSOLE_CAPTURE),
        detail(locale, DoctorMessages.REMEDIATION_CONSOLE_CAPTURE),
        evidence(
            "logCaptureCapability",
            Boolean.valueOf(capabilities.isAvailable(CapabilityKeys.LOG_CAPTURE))));
  }

  private static DoctorCheck configuration(Locale locale, ConfigValidationResult validation) {
    if (validation == null) {
      return DoctorCheck.of(
          ID_CONFIGURATION,
          DoctorStatus.WARN,
          false,
          detail(locale, DoctorMessages.DETAIL_CONFIGURATION_NO_RESULT),
          detail(locale, DoctorMessages.REMEDIATION_CONFIGURATION));
    }
    int errors = validation.errors().size();
    int warnings = validation.warnings().size();
    DoctorStatus status =
        errors > 0 ? DoctorStatus.FAIL : (warnings > 0 ? DoctorStatus.WARN : DoctorStatus.PASS);
    String key =
        errors > 0
            ? DoctorMessages.DETAIL_CONFIGURATION_FAIL
            : (warnings > 0
                ? DoctorMessages.DETAIL_CONFIGURATION_WARN
                : DoctorMessages.DETAIL_CONFIGURATION_PASS);
    return DoctorCheck.of(
        ID_CONFIGURATION,
        status,
        true,
        detail(locale, key, Integer.valueOf(errors), Integer.valueOf(warnings)),
        detail(locale, DoctorMessages.REMEDIATION_CONFIGURATION),
        evidence("errors", Integer.valueOf(errors), "warnings", Integer.valueOf(warnings)));
  }

  private static DoctorCheck localization(
      Locale locale, DoctorEnvironment environment, int missingKeys) {
    String language = value(environment.language());
    if (missingKeys < 0) {
      return DoctorCheck.of(
          ID_LOCALIZATION,
          DoctorStatus.WARN,
          false,
          detail(locale, DoctorMessages.DETAIL_LOCALIZATION_NO_COUNT),
          detail(locale, DoctorMessages.REMEDIATION_LOCALIZATION));
    }
    boolean complete = missingKeys == 0;
    return DoctorCheck.of(
        ID_LOCALIZATION,
        complete ? DoctorStatus.PASS : DoctorStatus.WARN,
        true,
        detail(
            locale,
            complete
                ? DoctorMessages.DETAIL_LOCALIZATION_PASS
                : DoctorMessages.DETAIL_LOCALIZATION_WARN,
            language,
            Integer.valueOf(missingKeys)),
        detail(locale, DoctorMessages.REMEDIATION_LOCALIZATION),
        evidence("missingKeys", Integer.valueOf(missingKeys)));
  }

  private static DoctorCheck updates(Locale locale, DoctorEnvironment environment) {
    return DoctorCheck.of(
        ID_UPDATES,
        DoctorStatus.WARN,
        false,
        detail(locale, DoctorMessages.DETAIL_UPDATES),
        detail(locale, DoctorMessages.REMEDIATION_UPDATES),
        evidence("updateCheckEnabled", Boolean.valueOf(environment.updateCheckEnabled())));
  }

  private static SnapshotFacts readSnapshotFacts(DoctorInputs inputs) {
    SnapshotFacts facts = new SnapshotFacts();
    SnapshotStore store = inputs.snapshotStore();
    if (store == null) {
      return facts;
    }
    facts.supplied = true;
    SnapshotStoreConfig limits = inputs.snapshotLimits();
    facts.maxBytes = (limits == null ? SnapshotStoreConfig.defaults() : limits).maxStoreBytes();
    try {
      facts.verifyIssues = store.verify().issues().size();
    } catch (SnapshotStoreException failure) {
      if (isUnavailable(failure)) {
        facts.unavailable = true;
      } else {
        facts.verifyIssues = 1;
      }
    }
    try {
      facts.storeBytes = store.storeSizeBytes();
    } catch (SnapshotStoreException failure) {
      if (isUnavailable(failure)) {
        facts.unavailable = true;
      }
    }
    try {
      facts.activeDeployment = store.hasActiveDeployment();
      facts.activeKnown = true;
    } catch (SnapshotStoreException failure) {
      facts.activeKnown = false;
    }
    return facts;
  }

  private static boolean isUnavailable(SnapshotStoreException failure) {
    if (failure.reason() == SnapshotStoreException.Reason.STORE_UNAVAILABLE) {
      return true;
    }
    String text = failure.localizedMessage(Locale.ROOT);
    return text.contains("busy") || text.contains("closed") || text.contains("locked");
  }

  private static Map<String, Object> evidence(Object... pairs) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException("evidence requires key/value pairs");
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) {
      result.put((String) pairs[i], pairs[i + 1]);
    }
    return result;
  }

  private static String detail(Locale locale, String key, Object... parameters) {
    return DoctorMessages.format(locale, key, parameters);
  }

  private static String value(String text) {
    return text == null ? "" : text;
  }

  private static final class SnapshotFacts {
    private boolean supplied;
    private boolean unavailable;
    private int verifyIssues;
    private long storeBytes = -1L;
    private long maxBytes = SnapshotStoreConfig.DEFAULT_MAX_STORE_BYTES;
    private boolean activeDeployment;
    private boolean activeKnown;
  }
}
