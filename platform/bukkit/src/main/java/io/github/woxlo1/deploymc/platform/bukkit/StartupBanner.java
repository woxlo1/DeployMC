package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.api.PlatformInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Renders the structured startup banner from catalog entries only. */
public final class StartupBanner {

  private final LocalizedMessages messages;

  public StartupBanner(LocalizedMessages messages) {
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  /** Returns one catalog-formatted line per banner row, with warnings last. */
  public List<String> render(PlatformInfo info, StartupPlan plan) {
    Objects.requireNonNull(info, "info");
    Objects.requireNonNull(plan, "plan");

    List<String> lines = new ArrayList<String>();
    lines.add(messages.format(MessageKeys.STARTUP_TITLE, values("version", plan.pluginVersion())));
    lines.add(
        messages.format(
            MessageKeys.STARTUP_PLATFORM,
            values(
                "software",
                info.softwareName(),
                "implementation",
                info.softwareVersion(),
                "minecraftVersion",
                info.minecraftVersion())));
    lines.add(
        messages.format(
            MessageKeys.STARTUP_JAVA,
            values("javaVersion", info.javaVersion(), "operatingSystem", info.operatingSystem())));
    lines.add(
        messages.format(
            MessageKeys.STARTUP_BIND,
            values(
                "address", plan.bind().address(), "port", Integer.toString(plan.bind().port()))));
    lines.add(messages.format(MessageKeys.STARTUP_AUTH, values("authMode", plan.authMode())));
    for (String warningKey : plan.warningKeys()) {
      lines.add(messages.format(warningKey));
    }
    return Collections.unmodifiableList(lines);
  }

  private static Map<String, String> values(String... pairs) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    for (int index = 0; index < pairs.length; index += 2) {
      values.put(pairs[index], pairs[index + 1]);
    }
    return values;
  }
}
