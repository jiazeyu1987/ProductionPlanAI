package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.LinkedHashMap;
import java.util.Map;

public record ErpRefreshState(
  long attemptCount,
  long successCount,
  long failureCount,
  long skippedCount,
  String lastMode,
  String lastTrigger,
  String lastRequestId,
  String lastOperator,
  String lastReason,
  String lastStatus,
  String lastAttemptAt,
  String lastFinishedAt,
  String lastSuccessAt,
  String lastFailureAt,
  String lastErrorMessage,
  long lastDurationMs,
  long lastSuccessEpochMs
) {
  public static ErpRefreshState initial() {
    return new ErpRefreshState(
      0L,
      0L,
      0L,
      0L,
      null,
      null,
      null,
      null,
      null,
      "INIT",
      null,
      null,
      null,
      null,
      null,
      0L,
      0L
    );
  }

  public ErpRefreshState success(
    String mode,
    String trigger,
    String requestId,
    String operator,
    String reason,
    String attemptedAt,
    String finishedAt,
    long durationMs
  ) {
    return new ErpRefreshState(
      attemptCount + 1L,
      successCount + 1L,
      failureCount,
      skippedCount,
      mode,
      trigger,
      requestId,
      operator,
      reason,
      "SUCCESS",
      attemptedAt,
      finishedAt,
      finishedAt,
      lastFailureAt,
      null,
      durationMs,
      System.currentTimeMillis()
    );
  }

  public ErpRefreshState failure(
    String mode,
    String trigger,
    String requestId,
    String operator,
    String reason,
    String attemptedAt,
    String finishedAt,
    long durationMs,
    String errorMessage
  ) {
    return new ErpRefreshState(
      attemptCount + 1L,
      successCount,
      failureCount + 1L,
      skippedCount,
      mode,
      trigger,
      requestId,
      operator,
      reason,
      "FAILED",
      attemptedAt,
      finishedAt,
      lastSuccessAt,
      finishedAt,
      errorMessage,
      durationMs,
      lastSuccessEpochMs
    );
  }

  public ErpRefreshState skip(
    String mode,
    String trigger,
    String requestId,
    String operator,
    String finishedAt,
    String message
  ) {
    return new ErpRefreshState(
      attemptCount,
      successCount,
      failureCount,
      skippedCount + 1L,
      mode,
      trigger,
      requestId,
      operator,
      null,
      "SKIPPED",
      finishedAt,
      finishedAt,
      lastSuccessAt,
      lastFailureAt,
      message,
      0L,
      lastSuccessEpochMs
    );
  }

  public Map<String, Object> toMap() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("attempt_count", attemptCount);
    body.put("success_count", successCount);
    body.put("failure_count", failureCount);
    body.put("skipped_count", skippedCount);
    body.put("last_mode", lastMode);
    body.put("last_trigger", lastTrigger);
    body.put("last_request_id", lastRequestId);
    body.put("last_operator", lastOperator);
    body.put("last_reason", lastReason);
    body.put("last_status", lastStatus);
    body.put("last_attempt_at", lastAttemptAt);
    body.put("last_finished_at", lastFinishedAt);
    body.put("last_success_at", lastSuccessAt);
    body.put("last_failure_at", lastFailureAt);
    body.put("last_error_message", lastErrorMessage);
    body.put("last_duration_ms", lastDurationMs);
    return body;
  }
}

