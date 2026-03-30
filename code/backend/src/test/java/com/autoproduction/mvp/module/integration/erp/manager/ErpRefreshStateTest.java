package com.autoproduction.mvp.module.integration.erp.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ErpRefreshStateTest {

  @Test
  void initialAndStateTransitionsKeepExpectedCounters() {
    ErpRefreshState initial = ErpRefreshState.initial();
    assertEquals("INIT", initial.lastStatus());
    assertEquals(0L, initial.attemptCount());
    assertEquals(0L, initial.successCount());
    assertEquals(0L, initial.failureCount());
    assertEquals(0L, initial.skippedCount());

    ErpRefreshState success = initial.success(
      "MANUAL",
      "MANUAL",
      "req-1",
      "tester",
      "first",
      "2026-03-29T00:00:00Z",
      "2026-03-29T00:00:01Z",
      120L
    );
    assertEquals("SUCCESS", success.lastStatus());
    assertEquals(1L, success.attemptCount());
    assertEquals(1L, success.successCount());
    assertEquals(0L, success.failureCount());
    assertEquals(0L, success.skippedCount());
    assertTrue(success.lastSuccessEpochMs() > 0L);

    ErpRefreshState failed = success.failure(
      "TRIGGERED",
      "MES_REPORTING",
      "req-2",
      "event",
      "retry",
      "2026-03-29T00:10:00Z",
      "2026-03-29T00:10:01Z",
      80L,
      "boom"
    );
    assertEquals("FAILED", failed.lastStatus());
    assertEquals(2L, failed.attemptCount());
    assertEquals(1L, failed.successCount());
    assertEquals(1L, failed.failureCount());
    assertEquals(success.lastSuccessAt(), failed.lastSuccessAt());
    assertEquals("boom", failed.lastErrorMessage());

    ErpRefreshState skipped = failed.skip(
      "SCHEDULED",
      "SCHEDULED",
      null,
      "scheduler",
      "2026-03-29T00:20:00Z",
      "disabled"
    );
    assertEquals("SKIPPED", skipped.lastStatus());
    assertEquals(2L, skipped.attemptCount());
    assertEquals(1L, skipped.successCount());
    assertEquals(1L, skipped.failureCount());
    assertEquals(1L, skipped.skippedCount());
  }

  @Test
  void toMapContainsExpectedKeys() {
    ErpRefreshState state = ErpRefreshState.initial().success(
      "MANUAL",
      "MANUAL",
      "req-1",
      "tester",
      "first",
      "2026-03-29T00:00:00Z",
      "2026-03-29T00:00:01Z",
      120L
    );
    Map<String, Object> body = state.toMap();
    assertNotNull(body);
    assertEquals(1L, ((Number) body.get("attempt_count")).longValue());
    assertEquals(1L, ((Number) body.get("success_count")).longValue());
    assertEquals("SUCCESS", body.get("last_status"));
  }
}

