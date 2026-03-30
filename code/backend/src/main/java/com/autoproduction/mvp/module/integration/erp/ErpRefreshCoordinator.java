package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.module.integration.erp.manager.ErpMaterialCodeUtils;
import com.autoproduction.mvp.module.integration.erp.manager.ErpRefreshState;
import com.autoproduction.mvp.module.integration.erp.manager.ErpSnapshot;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpRefreshCoordinator {
  private static final Logger log = LoggerFactory.getLogger(ErpRefreshCoordinator.class);

  private final ErpDataManagerConfig config;
  private final ErpSnapshotLoader snapshotLoader;
  private volatile Runnable cacheInvalidator;
  private final Object refreshLock = new Object();
  private volatile ErpSnapshot snapshot = ErpSnapshot.empty();
  private volatile ErpRefreshState refreshState = ErpRefreshState.initial();

  ErpRefreshCoordinator(ErpDataManagerConfig config, ErpSnapshotLoader snapshotLoader) {
    this.config = config;
    this.snapshotLoader = snapshotLoader;
    this.cacheInvalidator = () -> {};
  }

  void setCacheInvalidator(Runnable cacheInvalidator) {
    this.cacheInvalidator = cacheInvalidator == null ? () -> {} : cacheInvalidator;
  }

  ErpSnapshot snapshot() {
    return snapshot;
  }

  ErpRefreshState refreshState() {
    return refreshState;
  }

  Map<String, Object> getRefreshStatus(String requestId) {
    ErpSnapshot currentSnapshot = snapshot;
    ErpRefreshState currentState = refreshState;

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("module", "ERP_DATA_MANAGER");
    body.put("refresh_enabled", config.refreshEnabled());
    body.put("trigger_min_interval_ms", config.triggerMinIntervalMs());
    body.put("read_through_on_empty", config.readThroughOnEmpty());
    body.put("erp_connection", config.connectionSummary());
    body.put("data_counts", currentSnapshot.toCountMap());
    body.put("refresh_state", currentState.toMap());
    return body;
  }

  Map<String, Object> refreshScheduled() {
    if (!config.refreshEnabled()) {
      return skip("SCHEDULED", "SCHEDULED", null, "scheduler", "ERP refresh is disabled.");
    }
    return refresh("SCHEDULED", "SCHEDULED", null, "scheduler", null);
  }

  Map<String, Object> refreshManual(String requestId, String operator, String reason) {
    String normalizedRequestId = ErpMaterialCodeUtils.normalizeText(requestId);
    String normalizedOperator = ErpMaterialCodeUtils.normalizeText(operator);
    if (normalizedOperator == null) {
      normalizedOperator = "manual";
    }
    String normalizedReason = ErpMaterialCodeUtils.normalizeText(reason);
    if (normalizedReason == null) {
      normalizedReason = "manual refresh";
    }
    return refresh("MANUAL", "MANUAL", normalizedRequestId, normalizedOperator, normalizedReason);
  }

  Map<String, Object> refreshTriggered(String triggerType, String requestId, String reason) {
    String normalizedTrigger = ErpMaterialCodeUtils.normalizeText(triggerType);
    if (normalizedTrigger == null) {
      normalizedTrigger = "UNKNOWN";
    }
    if (!config.refreshEnabled()) {
      return skip(
        "TRIGGERED",
        normalizedTrigger,
        ErpMaterialCodeUtils.normalizeText(requestId),
        "event",
        "ERP refresh is disabled."
      );
    }

    ErpRefreshState currentState = refreshState;
    long nowMillis = System.currentTimeMillis();
    if (
      currentState.lastSuccessEpochMs() > 0
        && config.triggerMinIntervalMs() > 0
        && nowMillis - currentState.lastSuccessEpochMs() < config.triggerMinIntervalMs()
    ) {
      return skip(
        "TRIGGERED",
        normalizedTrigger,
        ErpMaterialCodeUtils.normalizeText(requestId),
        "event",
        "Skipped by trigger refresh cooldown."
      );
    }
    return refresh(
      "TRIGGERED",
      normalizedTrigger,
      ErpMaterialCodeUtils.normalizeText(requestId),
      "event",
      ErpMaterialCodeUtils.normalizeText(reason)
    );
  }

  void ensureCacheReady() {
    if (!config.refreshEnabled()) {
      return;
    }
    if (!config.readThroughOnEmpty()) {
      return;
    }
    if (refreshState.successCount() > 0) {
      return;
    }
    refresh("READ_THROUGH", "READ_THROUGH", null, "system", "bootstrap on first read");
  }

  private Map<String, Object> refresh(
    String mode,
    String trigger,
    String requestId,
    String operator,
    String reason
  ) {
    synchronized (refreshLock) {
      String attemptedAt = now();
      long startNanos = System.nanoTime();
      ErpSnapshot before = snapshot;
      try {
        ErpSnapshot loaded = snapshotLoader.loadSnapshot();
        snapshot = loaded;
        cacheInvalidator.run();
        long durationMs = elapsedMillis(startNanos);
        String finishedAt = now();
        refreshState = refreshState.success(mode, trigger, requestId, operator, reason, attemptedAt, finishedAt, durationMs);
        return buildRefreshResponse(
          requestId,
          mode,
          trigger,
          operator,
          reason,
          "SUCCESS",
          false,
          "ERP data refreshed.",
          attemptedAt,
          finishedAt,
          durationMs,
          loaded,
          refreshState
        );
      } catch (RuntimeException ex) {
        long durationMs = elapsedMillis(startNanos);
        String finishedAt = now();
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        refreshState = refreshState.failure(mode, trigger, requestId, operator, reason, attemptedAt, finishedAt, durationMs, message);
        log.warn("ERP refresh failed. mode={}, trigger={}, requestId={}", mode, trigger, requestId, ex);
        return buildRefreshResponse(
          requestId,
          mode,
          trigger,
          operator,
          reason,
          "FAILED",
          false,
          message,
          attemptedAt,
          finishedAt,
          durationMs,
          before,
          refreshState
        );
      }
    }
  }

  private Map<String, Object> skip(
    String mode,
    String trigger,
    String requestId,
    String operator,
    String message
  ) {
    synchronized (refreshLock) {
      String now = now();
      refreshState = refreshState.skip(mode, trigger, requestId, operator, now, message);
      return buildRefreshResponse(
        requestId,
        mode,
        trigger,
        operator,
        null,
        "SKIPPED",
        true,
        message,
        now,
        now,
        0L,
        snapshot,
        refreshState
      );
    }
  }

  private static Map<String, Object> buildRefreshResponse(
    String requestId,
    String mode,
    String trigger,
    String operator,
    String reason,
    String status,
    boolean skipped,
    String message,
    String attemptedAt,
    String finishedAt,
    long durationMs,
    ErpSnapshot currentSnapshot,
    ErpRefreshState currentState
  ) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("request_id", requestId);
    body.put("mode", mode);
    body.put("trigger", trigger);
    body.put("operator", operator);
    body.put("reason", reason);
    body.put("status", status);
    body.put("skipped", skipped);
    body.put("message", message);
    body.put("attempted_at", attemptedAt);
    body.put("finished_at", finishedAt);
    body.put("duration_ms", durationMs);
    body.put("data_counts", currentSnapshot.toCountMap());
    body.put("refresh_state", currentState.toMap());
    return body;
  }

  private static String now() {
    return OffsetDateTime.now(ZoneOffset.UTC).toString();
  }

  private static long elapsedMillis(long startNanos) {
    return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
  }
}
