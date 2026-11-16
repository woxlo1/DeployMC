package io.github.woxlo1.deploymc.platform.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.transport.HttpBindConfig;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class StandaloneConfigTest {

  @Test
  void normalizesRootAndLogPaths() {
    StandaloneConfig config =
        StandaloneConfig.of(
            Paths.get(".", "plugins"),
            HttpBindConfig.of("127.0.0.1", 8080, false),
            Paths.get(".", "logs", "server.log"));

    assertTrue(config.rootDirectory().isAbsolute());
    assertTrue(config.hasLogFile());
    assertEquals(Paths.get("server.log"), config.logFile().getFileName());
    assertTrue(config.logFile().endsWith(Paths.get("logs", "server.log")));
  }

  @Test
  void logFileIsOptional() {
    StandaloneConfig config =
        StandaloneConfig.of(Paths.get("."), HttpBindConfig.of("127.0.0.1", 8080, false), null);

    assertFalse(config.hasLogFile());
    assertEquals(null, config.logFile());
  }

  @Test
  void rejectsNullRootAndBind() {
    assertThrows(
        NullPointerException.class,
        () -> StandaloneConfig.of(null, HttpBindConfig.of("127.0.0.1", 8080, false), null));
    assertThrows(NullPointerException.class, () -> StandaloneConfig.of(Paths.get("."), null, null));
  }

  @Test
  void rootIsNormalized() {
    Path expected = Paths.get("a", "..", "b").toAbsolutePath().normalize();
    StandaloneConfig config =
        StandaloneConfig.of(
            Paths.get("a", "..", "b"), HttpBindConfig.of("127.0.0.1", 8080, false), null);

    assertEquals(expected, config.rootDirectory());
  }
}
