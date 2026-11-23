package io.github.woxlo1.deploymc.core.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DoctorStatusTest {

  @Test
  void severityOrderIsPassWarnFail() {
    assertEquals(0, DoctorStatus.PASS.severity());
    assertEquals(1, DoctorStatus.WARN.severity());
    assertEquals(2, DoctorStatus.FAIL.severity());
    assertTrue(DoctorStatus.PASS.compareTo(DoctorStatus.WARN) < 0);
    assertTrue(DoctorStatus.WARN.compareTo(DoctorStatus.FAIL) < 0);
  }

  @Test
  void moreSevereReturnsTheHigherSeverityValueDeterministically() {
    assertSame(DoctorStatus.WARN, DoctorStatus.PASS.moreSevere(DoctorStatus.WARN));
    assertSame(DoctorStatus.FAIL, DoctorStatus.WARN.moreSevere(DoctorStatus.FAIL));
    assertSame(DoctorStatus.FAIL, DoctorStatus.FAIL.moreSevere(DoctorStatus.PASS));
    assertSame(DoctorStatus.WARN, DoctorStatus.WARN.moreSevere(DoctorStatus.WARN));
  }

  @Test
  void worstAggregatesFixedAndVariableArguments() {
    assertSame(DoctorStatus.FAIL, DoctorStatus.worst(DoctorStatus.PASS, DoctorStatus.FAIL));
    assertSame(
        DoctorStatus.FAIL,
        DoctorStatus.worst(
            DoctorStatus.PASS, DoctorStatus.WARN, DoctorStatus.FAIL, DoctorStatus.PASS));
    assertSame(DoctorStatus.WARN, DoctorStatus.worst());
    assertThrows(NullPointerException.class, () -> DoctorStatus.PASS.moreSevere(null));
    assertThrows(NullPointerException.class, () -> DoctorStatus.worst(null, DoctorStatus.PASS));
  }
}
