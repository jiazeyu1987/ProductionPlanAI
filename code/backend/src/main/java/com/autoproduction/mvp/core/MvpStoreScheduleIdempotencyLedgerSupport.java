package com.autoproduction.mvp.core;

import java.util.Map;
import java.util.function.Supplier;

final class MvpStoreScheduleIdempotencyLedgerSupport {
  private MvpStoreScheduleIdempotencyLedgerSupport() {}

  static Map<String, Object> runIdempotent(
    MvpStoreScheduleExplainMetricsSupport store,
    String requestId,
    String action,
    Supplier<Map<String, Object>> executor
  ) {
    if (requestId == null || requestId.isBlank()) {
      return executor.get();
    }
    String key = action + "#" + requestId;
    if (store.state.idempotencyLedger.containsKey(key)) {
      return store.deepCopyMap(store.state.idempotencyLedger.get(key));
    }
    Map<String, Object> result = executor.get();
    store.state.idempotencyLedger.put(key, store.deepCopyMap(result));
    return store.deepCopyMap(result);
  }
}

