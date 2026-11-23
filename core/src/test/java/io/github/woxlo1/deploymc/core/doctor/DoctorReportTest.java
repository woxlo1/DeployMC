package io.github.woxlo1.deploymc.core.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DoctorReportTest {

  private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

  @Test
  void jsonUsesExactSchemaFieldNamesAndStableOrder() {
    DoctorReport report =
        DoctorReport.of(
            NOW,
            Collections.singletonList(
                DoctorCheck.of("environment", DoctorStatus.PASS, true, "ok", "")));

    assertEquals(
        "{\"schemaVersion\":\"1.0\",\"generatedAt\":\"2026-11-23T00:00:00Z\","
            + "\"overall\":\"PASS\",\"scope\":\"limited\",\"checks\":["
            + "{\"id\":\"environment\",\"status\":\"PASS\",\"implemented\":true,"
            + "\"detail\":\"ok\",\"remediation\":\"\"}],\"redacted\":false}",
        report.toJson());
    assertEquals("1.0", report.schemaVersion());
    assertEquals("limited", report.scope());
    assertEquals(NOW.toString(), report.generatedAt());
    assertEquals(DoctorStatus.PASS, report.overall());
    assertFalse(report.isRedacted());
  }

  @Test
  void generatedAtComesFromInjectedClock() {
    DoctorReport report =
        DoctorReport.of(
            Clock.fixed(NOW, ZoneOffset.UTC),
            Collections.singletonList(
                DoctorCheck.of("environment", DoctorStatus.PASS, true, "ok", "")));
    assertEquals(NOW.toString(), report.generatedAt());
  }

  @Test
  void jsonEscapesStringsAndControlCharacters() {
    String detail = "quote \" backslash \\ newline \n tab \t control " + (char) 1;
    DoctorReport report =
        DoctorReport.of(
            NOW,
            Collections.singletonList(
                DoctorCheck.of("sandbox", DoctorStatus.WARN, true, detail, "hint")));

    String json = report.toJson();
    assertTrue(json.contains("quote \\\" backslash \\\\ newline \\n tab \\t control \\u0001"));
    assertFalse(json.contains("\n"));
    assertFalse(json.contains((char) 1 + ""));
  }

  @Test
  void overallAggregatesSeverityWithoutRecomputingCheckStatuses() {
    DoctorCheck pass = DoctorCheck.of("a", DoctorStatus.PASS, true, "pass", "");
    DoctorCheck warn = DoctorCheck.of("b", DoctorStatus.WARN, true, "warn", "");
    DoctorCheck fail = DoctorCheck.of("c", DoctorStatus.FAIL, true, "fail", "");

    assertEquals(
        DoctorStatus.FAIL, DoctorReport.of(NOW, Arrays.asList(pass, warn, fail)).overall());
    assertEquals(DoctorStatus.WARN, DoctorReport.of(NOW, Arrays.asList(pass, warn)).overall());
    assertEquals(DoctorStatus.PASS, DoctorReport.of(NOW, Arrays.asList(pass)).overall());
    assertEquals(
        DoctorStatus.WARN,
        DoctorReport.of(NOW, Arrays.asList(DoctorCheck.of("x", DoctorStatus.WARN, false, "d", "r")))
            .overall());
  }

  @Test
  void humanRenderingUsesStoredStringsAndLocalizedFooter() {
    String japaneseDetail =
        DoctorMessages.format(Locale.JAPANESE, DoctorMessages.DETAIL_SNAPSHOT_PASS);
    String japaneseHint =
        DoctorMessages.format(Locale.JAPANESE, DoctorMessages.REMEDIATION_SNAPSHOT);
    DoctorReport report =
        DoctorReport.of(
            NOW,
            Arrays.asList(
                DoctorCheck.of("snapshot", DoctorStatus.PASS, true, japaneseDetail, japaneseHint),
                DoctorCheck.of("updates", DoctorStatus.WARN, false, "not implemented", "")));

    List<String> lines = report.renderHuman(Locale.JAPANESE);

    assertEquals(4, lines.size());
    assertEquals("[PASS] snapshot: " + japaneseDetail, lines.get(0));
    assertEquals("  " + japaneseHint, lines.get(1));
    assertEquals("[WARN] updates: not implemented", lines.get(2));
    assertEquals(
        DoctorMessages.format(Locale.JAPANESE, DoctorMessages.FOOTER_NOT_SUPPORT_TIER),
        lines.get(lines.size() - 1));
  }

  @Test
  void redactedCopyDropsEvidenceAndReplacesPaths() {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("freeBytes", Long.valueOf(8L));
    evidence.put("mode", "/var/lib/deploymc");
    DoctorReport original =
        DoctorReport.of(
            NOW,
            Collections.singletonList(
                DoctorCheck.of(
                    "sandbox",
                    DoctorStatus.FAIL,
                    true,
                    "Root is /srv/minecraft/plugins",
                    "Check /etc/deploymc.yml",
                    evidence)));

    DoctorReport redacted = original.redacted();

    assertTrue(redacted.isRedacted());
    assertFalse(original.isRedacted());
    assertTrue(redacted.toJson().contains("\"redacted\":true"));
    assertFalse(redacted.toJson().contains("/srv/minecraft"));
    assertFalse(redacted.toJson().contains("/etc/deploymc"));
    assertFalse(redacted.toJson().contains("freeBytes"));
    assertFalse(redacted.toJson().contains("\"mode\""));
    assertFalse(redacted.renderHuman(Locale.ROOT).toString().contains("/srv/minecraft"));
    assertTrue(redacted.renderHuman(Locale.ROOT).toString().contains(DoctorText.REDACTED));
    assertEquals(redacted, redacted.redacted());
  }

  @Test
  void evidenceScalarsUseSchemaTypes() {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("warnings", Integer.valueOf(2));
    evidence.put("freeBytes", Long.valueOf(4096L));
    evidence.put("ok", Boolean.TRUE);
    evidence.put("mode", "stable-value");
    DoctorReport report =
        DoctorReport.of(
            NOW,
            Collections.singletonList(
                DoctorCheck.of("configuration", DoctorStatus.WARN, true, "d", "r", evidence)));

    assertTrue(
        report
            .toJson()
            .contains(
                "\"evidence\":{\"freeBytes\":4096,\"mode\":\"stable-value\",\"ok\":true,"
                    + "\"warnings\":2}"));
  }
}
