package io.github.woxlo1.deploymc.core.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DoctorCheckTest {

  @Test
  void factoryKeepsStableFieldsAndEmptyRemediation() {
    DoctorCheck check = DoctorCheck.of("sandbox", DoctorStatus.PASS, true, "checked", null);

    assertEquals("sandbox", check.id());
    assertEquals(DoctorStatus.PASS, check.status());
    assertTrue(check.implemented());
    assertEquals("checked", check.detail());
    assertEquals("", check.remediation());
    assertTrue(check.evidence().isEmpty());
  }

  @Test
  void evidenceIsCopiedSortedAndLimitedToScalars() {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("warnings", Integer.valueOf(2));
    evidence.put("freeBytes", Long.valueOf(4096L));
    evidence.put("ok", Boolean.TRUE);
    evidence.put("mode", "stable-value");
    DoctorCheck check =
        DoctorCheck.of("configuration", DoctorStatus.WARN, true, "detail", "hint", evidence);
    evidence.clear();

    assertEquals(4, check.evidence().size());
    assertThrows(UnsupportedOperationException.class, () -> check.evidence().clear());
    assertEquals(
        java.util.Arrays.asList("freeBytes", "mode", "ok", "warnings"),
        new java.util.ArrayList<String>(check.evidence().keySet()));
    assertEquals(4096L, check.evidence().get("freeBytes"));
    assertTrue(DoctorCheck.allowedEvidenceKeys().contains("portOwned"));

    Map<String, Object> unknown = new HashMap<String, Object>();
    unknown.put("absolutePath", "C:/secret");
    assertThrows(
        IllegalArgumentException.class,
        () -> DoctorCheck.of("x", DoctorStatus.PASS, true, "d", "r", unknown));

    Map<String, Object> nonScalar = new HashMap<String, Object>();
    nonScalar.put("warnings", Collections.singletonList("1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> DoctorCheck.of("x", DoctorStatus.PASS, true, "d", "r", nonScalar));

    Map<String, Object> tooLong = new HashMap<String, Object>();
    tooLong.put("mode", repeat('x', 257));
    assertThrows(
        IllegalArgumentException.class,
        () -> DoctorCheck.of("x", DoctorStatus.PASS, true, "d", "r", tooLong));
  }

  @Test
  void redactedRemovesEvidenceAndPathLikeTokens() {
    DoctorCheck check =
        DoctorCheck.of(
            "sandbox",
            DoctorStatus.FAIL,
            true,
            "Bearer abc123 at C:\\secrets\\key and /home/user/file",
            "See /var/log/deploymc.log",
            Collections.<String, Object>singletonMap("freeBytes", Long.valueOf(8L)));

    DoctorCheck redacted = check.redacted();

    assertEquals(check.id(), redacted.id());
    assertEquals(check.status(), redacted.status());
    assertEquals(check.implemented(), redacted.implemented());
    assertTrue(redacted.evidence().isEmpty());
    assertFalse(redacted.detail().contains("abc123"));
    assertFalse(redacted.detail().contains("C:\\secrets"));
    assertFalse(redacted.detail().contains("/home/user/file"));
    assertFalse(redacted.remediation().contains("/var/log"));
    assertTrue(redacted.detail().contains(DoctorText.REDACTED));
    assertTrue(redacted.remediation().contains(DoctorText.REDACTED));
    assertFalse(check.evidence().isEmpty());
  }

  @Test
  void detailIsBoundedAndIdentifiersAreValidated() {
    DoctorCheck check =
        DoctorCheck.of("auth.mode", DoctorStatus.WARN, false, repeat('x', 1000), repeat('y', 1000));
    assertEquals(DoctorText.MAX_TEXT_LENGTH, check.detail().length());
    assertEquals(DoctorText.MAX_TEXT_LENGTH, check.remediation().length());
    assertThrows(
        IllegalArgumentException.class,
        () -> DoctorCheck.of("", DoctorStatus.PASS, true, "d", "r"));
    assertThrows(NullPointerException.class, () -> DoctorCheck.of("id", null, true, "d", "r"));
  }

  private static String repeat(char value, int count) {
    StringBuilder text = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      text.append(value);
    }
    return text.toString();
  }
}
