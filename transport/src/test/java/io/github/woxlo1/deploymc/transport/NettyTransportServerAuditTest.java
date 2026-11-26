package io.github.woxlo1.deploymc.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditConfig;
import io.github.woxlo1.deploymc.core.audit.AuditQuery;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditStore;
import io.github.woxlo1.deploymc.core.auth.AuthenticationFailure;
import io.github.woxlo1.deploymc.core.auth.AuthenticationResult;
import io.github.woxlo1.deploymc.core.auth.Authenticator;
import io.github.woxlo1.deploymc.core.auth.Identity;
import io.github.woxlo1.deploymc.core.rbac.RbacEvaluator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Embedded-socket tests for transport denial auditing; no running server or Bukkit runtime. */
class NettyTransportServerAuditTest {

  private static final String TOKEN = "dmc_audit_test_token_0123456789";
  private static final Identity USER =
      new Identity(
          "audit-actor",
          "Audit Actor",
          "cloudflare-access",
          false,
          Collections.<String>emptyList());
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-26T00:00:00.000Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  private AuditStore store;

  @BeforeEach
  void setUp() throws Exception {
    store =
        AuditStore.open(
            tempDir,
            AuditConfig.of("audit", 1024L * 1024L, 365, AuditConfig.FsyncPolicy.RECORD, 1),
            CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (store != null) {
      store.close();
    }
  }

  @Test
  void recordsUnauthenticatedFileDenial() throws Exception {
    TransportConfig config = config();
    NettyTransportServer server =
        newServer(
            config,
            RbacTestSupport.permitAll(),
            request -> AuthenticationResult.failed(AuthenticationFailure.REQUIRED));

    try {
      server.start();
      String response =
          rawRequest(
              server.port(),
              "GET /api/v1/files?path=config.yml HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
      assertTrue(response.startsWith("HTTP/1.1 401"), response);
    } finally {
      server.stop();
    }

    List<AuditRecord> records = records(AuditActions.FILES_LIST);
    assertEquals(1, records.size());
    assertEquals(AuditResult.DENIED, records.get(0).result());
    assertEquals("system", records.get(0).actor().id());
    assertTrue(records.get(0).details().containsKey("reason-code"));
  }

  @Test
  void recordsAuthenticatedRbacDenialWithoutAdminEquivalence() throws Exception {
    TransportConfig config = config();
    RbacEvaluator denyAll = RbacTestSupport.denyAll();
    NettyTransportServer server =
        newServer(
            config,
            denyAll,
            request ->
                ("Bearer " + TOKEN).equals(request.header("Authorization"))
                    ? AuthenticationResult.authenticated(USER)
                    : AuthenticationResult.failed(AuthenticationFailure.INVALID));

    try {
      server.start();
      String response =
          rawRequest(
              server.port(),
              "GET /api/v1/server HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer "
                  + TOKEN
                  + "\r\n\r\n");
      assertTrue(response.startsWith("HTTP/1.1 403"), response);
    } finally {
      server.stop();
    }

    List<AuditRecord> records = records(AuditActions.AUTH_DENIED);
    assertEquals(1, records.size());
    assertEquals(AuditResult.DENIED, records.get(0).result());
    assertEquals("audit-actor", records.get(0).actor().id());
    assertEquals("rbac.denied", records.get(0).details().get("reason-code"));
  }

  @Test
  void recordsIdentityRateLimitDenial() throws Exception {
    TransportConfig config =
        TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false))
            .observability(ObservabilityConfig.builder().identityRateLimitPerMinute(1).build())
            .build();
    NettyTransportServer server =
        newServer(
            config,
            RbacTestSupport.permitAll(),
            request ->
                ("Bearer " + TOKEN).equals(request.header("Authorization"))
                    ? AuthenticationResult.authenticated(USER)
                    : AuthenticationResult.failed(AuthenticationFailure.INVALID));
    String request =
        "GET /api/v1/server HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer "
            + TOKEN
            + "\r\n\r\n";

    try {
      server.start();
      assertTrue(rawRequest(server.port(), request).startsWith("HTTP/1.1 200"));
      assertTrue(rawRequest(server.port(), request).startsWith("HTTP/1.1 429"));
    } finally {
      server.stop();
    }

    List<AuditRecord> records = records(AuditActions.AUTH_DENIED);
    assertEquals(1, records.size());
    assertEquals("auth.rate-limited", records.get(0).details().get("reason-code"));
  }

  private TransportConfig config() {
    return TransportConfig.builder(HttpBindConfig.of("127.0.0.1", 0, false)).build();
  }

  private NettyTransportServer newServer(
      TransportConfig config, RbacEvaluator evaluator, Authenticator authenticator) {
    return new NettyTransportServer(
        config,
        request -> HttpResponse.json(200, "{}"),
        new NoopWebSocketListener(),
        new DefaultTransportPolicy(config),
        new NoopLogger(),
        authenticator,
        TransportMessages.of(Locale.ENGLISH),
        evaluator,
        new MetricsRegistry(),
        AuditRecorder.of(store, evaluator));
  }

  private List<AuditRecord> records(String action) throws Exception {
    List<AuditRecord> matched = new ArrayList<AuditRecord>();
    for (AuditRecord record : store.query(AuditQuery.defaults()).items()) {
      if (action.equals(record.action())) {
        matched.add(record);
      }
    }
    return matched;
  }

  private static String rawRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5000);
      OutputStream output = socket.getOutputStream();
      output.write(request.getBytes(StandardCharsets.UTF_8));
      output.flush();
      ByteArrayOutputStream collected = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      try {
        int read;
        while ((read = socket.getInputStream().read(buffer)) != -1) {
          collected.write(buffer, 0, read);
        }
      } catch (SocketTimeoutException ignored) {
        // A closed connection is the expected end of a one-shot HTTP response.
      }
      return new String(collected.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static final class NoopWebSocketListener implements WebSocketListener {

    @Override
    public void onOpen(WebSocketSession session) {}

    @Override
    public void onText(WebSocketSession session, String text) {}

    @Override
    public void onClose(WebSocketSession session) {}
  }

  private static final class NoopLogger implements RequestLogger {

    @Override
    public void started(String address) {}

    @Override
    public void stopped() {}

    @Override
    public void nonLoopbackWarning(String address) {}

    @Override
    public void log(RequestLogEntry entry) {}

    @Override
    public void rejected(String requestId, String reason) {}

    @Override
    public void websocketRejected(String remoteAddress, String reason) {}

    @Override
    public void websocketOpened(String connectionId, String remoteAddress) {}

    @Override
    public void websocketClosed(String connectionId) {}

    @Override
    public void websocketQueueFull(String connectionId) {}
  }
}
