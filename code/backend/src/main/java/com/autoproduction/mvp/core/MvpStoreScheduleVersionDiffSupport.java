package com.autoproduction.mvp.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleVersionDiffSupport {
  private MvpStoreScheduleVersionDiffSupport() {}

  static Map<String, Object> getVersionDiff(
    MvpStoreScheduleAlgorithmDomain store,
    String versionNo,
    String compareWith,
    String requestId
  ) {
    MvpDomain.ScheduleVersion base = store.getScheduleEntity(compareWith);
    MvpDomain.ScheduleVersion current = store.getScheduleEntity(versionNo);
    Map<String, Double> a = new HashMap<>();
    Map<String, Double> b = new HashMap<>();
    for (MvpDomain.Allocation row : base.allocations) {
      a.put(row.taskKey + "#" + row.shiftId, row.scheduledQty);
    }
    for (MvpDomain.Allocation row : current.allocations) {
      b.put(row.taskKey + "#" + row.shiftId, row.scheduledQty);
    }
    Set<String> allTaskKeys = new HashSet<>(a.keySet());
    allTaskKeys.addAll(b.keySet());
    int changedTaskCount = 0;
    for (String key : allTaskKeys) {
      if (Math.abs(a.getOrDefault(key, 0d) - b.getOrDefault(key, 0d)) > 1e-9) {
        changedTaskCount += 1;
      }
    }
    Set<String> baseOrders = new HashSet<>();
    Set<String> currentOrders = new HashSet<>();
    for (MvpDomain.Allocation row : base.allocations) {
      baseOrders.add(row.orderNo);
    }
    for (MvpDomain.Allocation row : current.allocations) {
      currentOrders.add(row.orderNo);
    }
    Set<String> allOrders = new HashSet<>(baseOrders);
    allOrders.addAll(currentOrders);
    int changedOrderCount = 0;
    for (String orderNo : allOrders) {
      if (baseOrders.contains(orderNo) != currentOrders.contains(orderNo)) {
        changedOrderCount += 1;
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("base_version_no", compareWith);
    out.put("compare_version_no", versionNo);
    out.put("changed_order_count", changedOrderCount);
    out.put("changed_task_count", changedTaskCount);
    out.put("delayed_order_delta", current.unscheduled.size() - base.unscheduled.size());
    out.put("overloaded_process_delta", store.countCapacityBlocked(current.unscheduled) - store.countCapacityBlocked(base.unscheduled));
    return out;
  }
}

