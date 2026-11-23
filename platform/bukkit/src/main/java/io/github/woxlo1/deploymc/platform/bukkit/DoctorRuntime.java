package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import io.github.woxlo1.deploymc.core.CapabilityKeys;
import io.github.woxlo1.deploymc.core.PlatformCapabilities;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.doctor.DoctorEnvironment;
import io.github.woxlo1.deploymc.core.doctor.DoctorInputs;
import io.github.woxlo1.deploymc.core.doctor.DoctorReport;
import io.github.woxlo1.deploymc.core.doctor.DoctorService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/** Server-free host facts for one deterministic doctor run. */
public final class DoctorRuntime {

  private final ConfigManager manager;
  private final StartupPlan plan;
  private final PlatformInfo platformInfo;
  private final PlatformCapabilities capabilities;
  private final Locale locale;
  private final String language;
  private final FileApiRuntime fileApi;
  private final LocalizedMessages messages;
  private final Clock clock;

  public DoctorRuntime(
      ConfigManager manager,
      StartupPlan plan,
      PlatformInfo platformInfo,
      PlatformCapabilities capabilities,
      Locale locale,
      String language,
      FileApiRuntime fileApi,
      LocalizedMessages messages,
      Clock clock) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.plan = Objects.requireNonNull(plan, "plan");
    this.platformInfo = Objects.requireNonNull(platformInfo, "platformInfo");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.locale = locale == null ? Locale.ROOT : locale;
    this.language = Objects.requireNonNull(language, "language");
    this.fileApi = fileApi;
    this.messages = Objects.requireNonNull(messages, "messages");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Builds a runtime from the live platform adapter. The adapter itself is not used for I/O. */
  public static DoctorRuntime create(
      ConfigManager manager,
      StartupPlan plan,
      BukkitPlatformAdapter adapter,
      Locale locale,
      String language,
      FileApiRuntime fileApi,
      LocalizedMessages messages) {
    Objects.requireNonNull(adapter, "adapter");
    return new DoctorRuntime(
        manager,
        plan,
        adapter.info(),
        adapter.capabilities(),
        locale,
        language,
        fileApi,
        messages,
        Clock.systemUTC());
  }

  /** Runs the 13 PART 36 checks. Absent subsystems stay null and are reported as WARN. */
  public DoctorReport run() {
    DoctorEnvironment environment =
        DoctorEnvironment.builder()
            .platformInfo(platformInfo)
            .deployMcVersion(plan.pluginVersion())
            .apiVersion(ServerInfoHandler.API_VERSION)
            .bindAddress(plan.bind().address())
            .bindPort(plan.bind().port())
            .loopbackOnly(plan.bind().isLoopbackOnly())
            .bindPortOwnedByHost(true)
            .authMode(plan.authMode())
            .language(language)
            .restartCapabilityAvailable(
                capabilities.isAvailable(CapabilityKeys.SERVER_RESTART_SCRIPT))
            .updateCheckEnabled(false)
            .build();
    DoctorInputs inputs =
        DoctorInputs.builder()
            .clock(clock)
            .locale(locale)
            .environment(environment)
            .capabilities(capabilities)
            .sandboxResolver(fileApi == null ? null : fileApi.sandboxResolver())
            .snapshotStore(fileApi == null ? null : fileApi.snapshotStore())
            .snapshotLimits(fileApi == null ? null : fileApi.snapshotConfig())
            .configValidation(manager.lastValidation().orElse(null))
            .missingLocalizationKeys(missingMessageCount(messages))
            .build();
    return new DoctorService().run(inputs);
  }

  private static int missingMessageCount(LocalizedMessages messages) {
    int missing = 0;
    for (Field field : MessageKeys.class.getFields()) {
      if (String.class.equals(field.getType())) {
        try {
          if (!messages.contains((String) field.get(null))) {
            missing++;
          }
        } catch (IllegalAccessException exception) {
          throw new IllegalStateException("message key constant is inaccessible", exception);
        }
      }
    }
    return missing;
  }
}
