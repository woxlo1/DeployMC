package io.github.woxlo1.deploymc.core.doctor;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/** Formats doctor details and remediation through the English and Japanese core catalogs. */
public final class DoctorMessages {

  private static final String BUNDLE_NAME = "io.github.woxlo1.deploymc.core.doctor.doctor-messages";
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  public static final String DETAIL_ENVIRONMENT_PASS = "doctor.detail.environment.pass";
  public static final String DETAIL_ENVIRONMENT_FAIL = "doctor.detail.environment.fail";
  public static final String REMEDIATION_ENVIRONMENT = "doctor.remediation.environment";

  public static final String DETAIL_CAPABILITIES_PASS = "doctor.detail.capabilities.pass";
  public static final String DETAIL_CAPABILITIES_WARN = "doctor.detail.capabilities.warn";
  public static final String REMEDIATION_CAPABILITIES = "doctor.remediation.capabilities";

  public static final String DETAIL_BIND_PASS = "doctor.detail.bind.pass";
  public static final String DETAIL_BIND_OWNED = "doctor.detail.bind.owned";
  public static final String DETAIL_BIND_WARN = "doctor.detail.bind.warn";
  public static final String DETAIL_BIND_FAIL_NON_LOOPBACK = "doctor.detail.bind.fail.nonLoopback";
  public static final String DETAIL_BIND_FAIL_PORT = "doctor.detail.bind.fail.port";
  public static final String DETAIL_BIND_FAIL_IN_USE = "doctor.detail.bind.fail.inUse";
  public static final String REMEDIATION_BIND = "doctor.remediation.bind";

  public static final String DETAIL_AUTH_MODE_PASS = "doctor.detail.auth.mode.pass";
  public static final String DETAIL_AUTH_MODE_WARN = "doctor.detail.auth.mode.warn";
  public static final String REMEDIATION_AUTH_MODE = "doctor.remediation.auth.mode";

  public static final String DETAIL_AUTH_TUNNEL = "doctor.detail.auth.tunnel.notImplemented";
  public static final String REMEDIATION_AUTH_TUNNEL = "doctor.remediation.auth.tunnel";

  public static final String DETAIL_SANDBOX_PASS = "doctor.detail.sandbox.pass";
  public static final String DETAIL_SANDBOX_NO_RESOLVER = "doctor.detail.sandbox.warn.noResolver";
  public static final String DETAIL_SANDBOX_NO_SPACE = "doctor.detail.sandbox.warn.space";
  public static final String DETAIL_SANDBOX_FAIL_ROOT = "doctor.detail.sandbox.fail.root";
  public static final String DETAIL_SANDBOX_FAIL_PROTECTED = "doctor.detail.sandbox.fail.protected";
  public static final String REMEDIATION_SANDBOX = "doctor.remediation.sandbox";

  public static final String DETAIL_SNAPSHOT_PASS = "doctor.detail.snapshot.pass";
  public static final String DETAIL_SNAPSHOT_VERIFY = "doctor.detail.snapshot.warn.verify";
  public static final String DETAIL_SNAPSHOT_NO_STORE = "doctor.detail.snapshot.warn.noStore";
  public static final String DETAIL_SNAPSHOT_NO_SIZE = "doctor.detail.snapshot.warn.noSize";
  public static final String DETAIL_SNAPSHOT_UNAVAILABLE =
      "doctor.detail.snapshot.fail.unavailable";
  public static final String DETAIL_SNAPSHOT_SIZE = "doctor.detail.snapshot.fail.size";
  public static final String REMEDIATION_SNAPSHOT = "doctor.remediation.snapshot";

  public static final String DETAIL_DEPLOYMENT = "doctor.detail.deployment.warn";
  public static final String REMEDIATION_DEPLOYMENT = "doctor.remediation.deployment";

  public static final String DETAIL_JAR_REPLACEMENT =
      "doctor.detail.platform.jar-replacement.notImplemented";
  public static final String REMEDIATION_JAR_REPLACEMENT =
      "doctor.remediation.platform.jar-replacement";

  public static final String DETAIL_CONSOLE_CAPTURE =
      "doctor.detail.console.capture.notImplemented";
  public static final String REMEDIATION_CONSOLE_CAPTURE = "doctor.remediation.console.capture";

  public static final String DETAIL_CONFIGURATION_PASS = "doctor.detail.configuration.pass";
  public static final String DETAIL_CONFIGURATION_WARN = "doctor.detail.configuration.warn";
  public static final String DETAIL_CONFIGURATION_FAIL = "doctor.detail.configuration.fail";
  public static final String DETAIL_CONFIGURATION_NO_RESULT =
      "doctor.detail.configuration.notImplemented";
  public static final String REMEDIATION_CONFIGURATION = "doctor.remediation.configuration";

  public static final String DETAIL_LOCALIZATION_PASS = "doctor.detail.localization.pass";
  public static final String DETAIL_LOCALIZATION_WARN = "doctor.detail.localization.warn";
  public static final String DETAIL_LOCALIZATION_NO_COUNT =
      "doctor.detail.localization.notImplemented";
  public static final String REMEDIATION_LOCALIZATION = "doctor.remediation.localization";

  public static final String DETAIL_UPDATES = "doctor.detail.updates.notImplemented";
  public static final String REMEDIATION_UPDATES = "doctor.remediation.updates";

  public static final String FOOTER_NOT_SUPPORT_TIER = "doctor.footer.notSupportTier";

  private static final Set<String> KEYS =
      Collections.unmodifiableSet(
          new LinkedHashSet<String>(
              Arrays.asList(
                  DETAIL_ENVIRONMENT_PASS,
                  DETAIL_ENVIRONMENT_FAIL,
                  REMEDIATION_ENVIRONMENT,
                  DETAIL_CAPABILITIES_PASS,
                  DETAIL_CAPABILITIES_WARN,
                  REMEDIATION_CAPABILITIES,
                  DETAIL_BIND_PASS,
                  DETAIL_BIND_OWNED,
                  DETAIL_BIND_WARN,
                  DETAIL_BIND_FAIL_NON_LOOPBACK,
                  DETAIL_BIND_FAIL_PORT,
                  DETAIL_BIND_FAIL_IN_USE,
                  REMEDIATION_BIND,
                  DETAIL_AUTH_MODE_PASS,
                  DETAIL_AUTH_MODE_WARN,
                  REMEDIATION_AUTH_MODE,
                  DETAIL_AUTH_TUNNEL,
                  REMEDIATION_AUTH_TUNNEL,
                  DETAIL_SANDBOX_PASS,
                  DETAIL_SANDBOX_NO_RESOLVER,
                  DETAIL_SANDBOX_NO_SPACE,
                  DETAIL_SANDBOX_FAIL_ROOT,
                  DETAIL_SANDBOX_FAIL_PROTECTED,
                  REMEDIATION_SANDBOX,
                  DETAIL_SNAPSHOT_PASS,
                  DETAIL_SNAPSHOT_VERIFY,
                  DETAIL_SNAPSHOT_NO_STORE,
                  DETAIL_SNAPSHOT_NO_SIZE,
                  DETAIL_SNAPSHOT_UNAVAILABLE,
                  DETAIL_SNAPSHOT_SIZE,
                  REMEDIATION_SNAPSHOT,
                  DETAIL_DEPLOYMENT,
                  REMEDIATION_DEPLOYMENT,
                  DETAIL_JAR_REPLACEMENT,
                  REMEDIATION_JAR_REPLACEMENT,
                  DETAIL_CONSOLE_CAPTURE,
                  REMEDIATION_CONSOLE_CAPTURE,
                  DETAIL_CONFIGURATION_PASS,
                  DETAIL_CONFIGURATION_WARN,
                  DETAIL_CONFIGURATION_FAIL,
                  DETAIL_CONFIGURATION_NO_RESULT,
                  REMEDIATION_CONFIGURATION,
                  DETAIL_LOCALIZATION_PASS,
                  DETAIL_LOCALIZATION_WARN,
                  DETAIL_LOCALIZATION_NO_COUNT,
                  REMEDIATION_LOCALIZATION,
                  DETAIL_UPDATES,
                  REMEDIATION_UPDATES,
                  FOOTER_NOT_SUPPORT_TIER)));

  private DoctorMessages() {}

  /** Formats one catalog message. Untrusted parameters are sanitized before interpolation. */
  public static String format(Locale locale, String key, Object... parameters) {
    ResourceBundle bundle = bundle(locale);
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (MissingResourceException missing) {
      pattern = bundle(Locale.ROOT).getString(key);
    }
    Object[] safe = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      safe[i] = DoctorText.parameter(String.valueOf(parameters[i]));
    }
    return DoctorText.limit(new MessageFormat(pattern, Locale.ROOT).format(safe));
  }

  /** All catalog keys, exposed so tests can assert English and Japanese structural parity. */
  public static Set<String> keys() {
    return KEYS;
  }

  private static ResourceBundle bundle(Locale locale) {
    Locale effective = locale == null ? Locale.ROOT : locale;
    return ResourceBundle.getBundle(
        BUNDLE_NAME, effective, DoctorMessages.class.getClassLoader(), CONTROL);
  }
}
