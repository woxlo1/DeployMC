package io.github.woxlo1.deploymc.platform.bukkit;

import io.github.woxlo1.deploymc.core.audit.AuditActions;
import io.github.woxlo1.deploymc.core.audit.AuditException;
import io.github.woxlo1.deploymc.core.audit.AuditRecord;
import io.github.woxlo1.deploymc.core.audit.AuditResult;
import io.github.woxlo1.deploymc.core.audit.AuditTarget;
import io.github.woxlo1.deploymc.core.files.SnapshotHook;
import io.github.woxlo1.deploymc.core.files.SnapshotRequest;
import io.github.woxlo1.deploymc.core.sandbox.SandboxResolver;
import io.github.woxlo1.deploymc.transport.AuditRecorder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fail-closed audit wrapper for automatic snapshots taken before a destructive file operation. */
final class AuditedSnapshotHook implements SnapshotHook {

  private final SnapshotHook delegate;
  private final AuditRecorder audit;
  private final SandboxResolver resolver;

  AuditedSnapshotHook(SnapshotHook delegate, AuditRecorder audit, SandboxResolver resolver) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.audit = Objects.requireNonNull(audit, "audit");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
  }

  @Override
  public String capture(SnapshotRequest request) throws IOException {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("reason", request.reason());
    details.put("entry-count", Long.valueOf(request.entries().size()));
    AuditRecord pending;
    try {
      pending = audit.beginSystem(AuditActions.SNAPSHOTS_CREATE, target(request), details);
    } catch (AuditException failure) {
      throw fail(failure);
    }
    String snapshotId;
    try {
      snapshotId = delegate.capture(request);
    } catch (IOException | RuntimeException failure) {
      try {
        audit.complete(pending, AuditResult.FAILED, "capture-failed");
      } catch (AuditException auditFailure) {
        throw fail(auditFailure);
      }
      throw failure;
    }
    Map<String, Object> finalDetails = new LinkedHashMap<String, Object>();
    if (snapshotId != null) {
      finalDetails.put("snapshot-id", snapshotId);
    }
    try {
      audit.complete(pending, AuditResult.SUCCESS, null, finalDetails);
    } catch (AuditException failure) {
      throw fail(failure);
    }
    return snapshotId;
  }

  private AuditTarget target(SnapshotRequest request) {
    try {
      if (request.entries().isEmpty()) {
        return null;
      }
      Path root = resolver.root().toAbsolutePath().normalize();
      Path candidate = root.resolve(request.entries().get(0).relativePath()).normalize();
      if (!candidate.startsWith(root) || candidate.equals(root)) {
        return null;
      }
      return AuditTarget.path(root.relativize(candidate).toString().replace('\\', '/'));
    } catch (RuntimeException outsideSandbox) {
      return null;
    }
  }

  private static IOException fail(AuditException failure) {
    return new IOException(failure.problemCode());
  }
}
