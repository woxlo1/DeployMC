package io.github.woxlo1.deploymc.core.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.config.ConfigManager;
import io.github.woxlo1.deploymc.core.config.ConfigView;
import io.github.woxlo1.deploymc.core.config.DefaultConfigWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RbacPolicyConfigTest {

  @TempDir Path root;

  @Test
  void convertsHostConfigurationIntoAnImmutablePolicySnapshot() throws Exception {
    ConfigView view = load(roles("none"));

    RbacPolicy policy = RbacPolicy.fromConfig(view);
    RbacEvaluator evaluator = new RbacEvaluator(policy, null);

    assertTrue(policy.valid());
    assertEquals(2, policy.roleCount());
    assertEquals(1, policy.userCount());
    assertEquals(1, policy.registryVersion());
    assertTrue(evaluator.authorize(owner(), "files.read", null).allowed());
    assertFalse(evaluator.authorize(owner(), "files.delete", null).allowed());
  }

  @Test
  void readsRoleRateLimitFromHostConfiguration() throws Exception {
    String configured =
        roles("none")
            .replace(
                "      - files.read\n",
                "      - files.read\n    limits:\n      rate-limit-per-minute: 12\n");
    ConfigView view = load(configured);

    RbacPolicy policy = RbacPolicy.fromConfig(view);
    RbacEvaluator evaluator = new RbacEvaluator(policy, null);

    assertTrue(policy.valid());
    assertEquals(12, evaluator.identityRateLimitPerMinute(owner(), 300));
  }

  @Test
  void invalidReloadDenyAllReplacesThePreviousSnapshot() throws Exception {
    ConfigView valid = load(roles("none"));
    RbacPolicyManager manager = RbacPolicyManager.from(valid);
    RbacEvaluator evaluator = new RbacEvaluator(manager.current(), null);
    assertTrue(evaluator.authorize(owner(), "files.read", null).allowed());

    writeConfig(roles("none").replace("files.read", "server.status"));
    manager.denyAll();

    assertFalse(manager.current().valid());
    assertEquals(0, manager.current().roleCount());
    assertFalse(
        new RbacEvaluator(manager.current(), null)
            .authorize(owner(), "files.read", null)
            .allowed());
  }

  @Test
  void malformedSnapshotFailsClosed() {
    assertFalse(RbacPolicy.denyAll().valid());
    assertTrue(new RbacEvaluator(RbacPolicy.denyAll(), null).effective(owner()).isEmpty());
    assertEquals(
        DecisionReason.NO_ROLE,
        new RbacEvaluator(RbacPolicy.denyAll(), null)
            .authorize(owner(), "files.read", null)
            .reason());
  }

  private ConfigView load(String source) throws Exception {
    writeConfig(source);
    return new ConfigManager(root.resolve("config.yml")).load();
  }

  private void writeConfig(String source) throws Exception {
    Files.write(root.resolve("config.yml"), source.getBytes(StandardCharsets.UTF_8));
  }

  private static String roles(String defaultRole) throws Exception {
    String template = DefaultConfigWriter.template();
    String configured =
        replaceSection(
            template,
            "roles: {}",
            "roles:\n"
                + "  viewer:\n"
                + "    permissions:\n"
                + "      - files.read\n"
                + "  denied:\n"
                + "    permissions: []\n"
                + "    paths:\n"
                + "      deny:\n"
                + "        - \"**/secrets.yml\"\n");
    configured =
        replaceSection(
            configured,
            "users: {}",
            "users:\n  owner:\n    roles:\n      - viewer\n      - denied\n");
    return configured.replace("default-role: none", "default-role: " + defaultRole);
  }

  private static String replaceSection(String template, String marker, String replacement) {
    int start = template.indexOf(marker);
    if (start < 0) {
      throw new IllegalStateException("Missing config marker: " + marker);
    }
    return template.substring(0, start) + replacement + template.substring(start + marker.length());
  }

  private static Identity owner() {
    return new Identity(
        "owner",
        "owner",
        "cloudflare-access",
        false,
        Collections.<String>emptyList(),
        Collections.<String, String>emptyMap());
  }
}
