package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class JsonRequestLoggerTest {

  @Test
  void requestLogUsesClosedPathAndMethodLabels() {
    CapturingLogger capturing = new CapturingLogger();
    JsonRequestLogger logger = new JsonRequestLogger(capturing.logger);

    logger.log(
        new RequestLogEntry(
            "req-\"secret\"", "gEt", "/api/v1/files/private-config.yml", 200, 12L, "completed"));

    String line = capturing.onlyRecord();
    assertTrue(line.contains("\"requestId\":\"req-\\\"secret\\\"\""));
    assertTrue(line.contains("\"method\":\"GET\""));
    assertTrue(line.contains("\"path\":\"files\""));
    assertTrue(line.contains("\"status\":200"));
    assertTrue(line.contains("\"durationMillis\":12"));
    assertTrue(line.contains("\"outcome\":\"completed\""));
    assertFalse(line.contains("private-config"));
    assertFalse(line.contains("/api/v1/files"));
  }

  @Test
  void unknownPathsAndMethodsAreCollapsedToOther() {
    CapturingLogger capturing = new CapturingLogger();
    JsonRequestLogger logger = new JsonRequestLogger(capturing.logger);

    logger.log(
        new RequestLogEntry("req-1", "TRACE", "/user-12345/secret.txt", 404, 1L, "bad\nreason"));

    String line = capturing.onlyRecord();
    assertTrue(line.contains("\"method\":\"OTHER\""));
    assertTrue(line.contains("\"path\":\"other\""));
    assertTrue(line.contains("\"outcome\":\"other\""));
    assertFalse(line.contains("user-12345"));
    assertFalse(line.contains("secret.txt"));
  }

  @Test
  void rejectionAndLifecycleEventsNeverLogAddressesOrConnectionIds() {
    CapturingLogger capturing = new CapturingLogger();
    JsonRequestLogger logger = new JsonRequestLogger(capturing.logger);

    logger.rejected("req-2", "auth.invalid\" injected");
    logger.websocketRejected("192.0.2.99", "forbidden");
    logger.websocketOpened("ws-secret-id", "192.0.2.99");
    logger.started("0.0.0.0:8080");
    logger.websocketQueueFull("ws-secret-id");

    List<String> records = capturing.records;
    assertTrue(records.get(0).contains("\"requestId\":\"req-2\""));
    assertTrue(records.get(0).contains("\"reason\":\"other\""));
    for (String record : records) {
      assertFalse(record.contains("192.0.2.99"), record);
      assertFalse(record.contains("ws-secret-id"), record);
      assertFalse(record.contains("0.0.0.0"), record);
      assertFalse(record.contains("injected"), record);
    }
    assertTrue(records.get(2).contains("\"event\":\"websocket-opened\""));
  }

  private static final class CapturingLogger {

    private final Logger logger = Logger.getAnonymousLogger();
    private final List<String> records = new ArrayList<String>();

    private CapturingLogger() {
      logger.setUseParentHandlers(false);
      logger.addHandler(
          new Handler() {
            @Override
            public void publish(LogRecord record) {
              records.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          });
    }

    private String onlyRecord() {
      if (records.size() != 1) {
        throw new AssertionError("expected one log record but got " + records);
      }
      return records.get(0);
    }
  }
}
