package io.github.woxlo1.deploymc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

  @Test
  void parsesStableVersion() {
    SemanticVersion version = SemanticVersion.parse("1.21.1");

    assertEquals(1, version.major());
    assertEquals(21, version.minor());
    assertEquals(1, version.patch());
    assertEquals(null, version.preRelease());
    assertEquals("1.21.1", version.toString());
  }

  @Test
  void parsesPreReleaseAndBuildMetadata() {
    SemanticVersion version = SemanticVersion.parse("1.0.0-rc.1+build.42");

    assertEquals("rc.1", version.preRelease());
    assertEquals("build.42", version.buildMetadata());
    assertEquals("1.0.0-rc.1+build.42", version.toString());
  }

  @Test
  void comparesNumericComponents() {
    assertTrue(SemanticVersion.parse("1.9.9").compareTo(SemanticVersion.parse("1.10.0")) < 0);
    assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.99.99")) > 0);
    assertEquals(0, SemanticVersion.parse("1.2.3").compareTo(SemanticVersion.parse("1.2.3")));
  }

  @Test
  void preReleaseSortsBeforeRelease() {
    assertTrue(SemanticVersion.parse("1.0.0-alpha").compareTo(SemanticVersion.parse("1.0.0")) < 0);
    assertTrue(
        SemanticVersion.parse("1.0.0-alpha").compareTo(SemanticVersion.parse("1.0.0-alpha.1")) < 0);
    assertTrue(
        SemanticVersion.parse("1.0.0-alpha.1").compareTo(SemanticVersion.parse("1.0.0-beta")) < 0);
  }

  @Test
  void buildMetadataDoesNotAffectOrdering() {
    assertEquals(
        0,
        SemanticVersion.parse("1.0.0+build.1").compareTo(SemanticVersion.parse("1.0.0+build.2")));
  }

  @Test
  void rejectsInvalidVersions() {
    assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2"));
    assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("v1.2.3"));
    assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("01.2.3"));
    assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2.3-"));
  }
}
