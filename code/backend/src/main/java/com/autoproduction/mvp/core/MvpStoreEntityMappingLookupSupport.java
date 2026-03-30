package com.autoproduction.mvp.core;

import java.util.HashSet;
import java.util.Set;

final class MvpStoreEntityMappingLookupSupport {
  private MvpStoreEntityMappingLookupSupport() {}

  static MvpDomain.Order findOrder(MvpStoreEntityMappingSupport store, String orderNo) {
    return store.state.orders.stream().filter(order -> order.orderNo.equals(orderNo)).findFirst()
      .orElseThrow(() -> store.notFound("Order %s not found.".formatted(orderNo)));
  }

  static MvpDomain.ScheduleVersion getScheduleEntity(MvpStoreEntityMappingSupport store, String versionNo) {
    if (versionNo == null || versionNo.isBlank()) {
      if (store.state.schedules.isEmpty()) {
        throw store.badRequest("No schedule generated.");
      }
      return store.state.schedules.get(store.state.schedules.size() - 1);
    }
    return store.state.schedules.stream()
      .filter(item -> item.versionNo.equals(versionNo))
      .findFirst()
      .orElseThrow(() -> store.notFound("Schedule %s not found.".formatted(versionNo)));
  }

  static MvpStoreRuntimeBase.ReportVersionBinding resolveReportVersionBinding(MvpStoreEntityMappingSupport store, String versionNo) {
    String requestedVersionNo = versionNo == null ? "" : versionNo.trim();
    MvpDomain.ScheduleVersion schedule = null;

    if (!requestedVersionNo.isBlank()) {
      schedule = store.getScheduleEntity(requestedVersionNo);
    } else if (store.state.publishedVersionNo != null && !store.state.publishedVersionNo.isBlank()) {
      schedule = store.getScheduleEntity(store.state.publishedVersionNo);
    } else if (!store.state.schedules.isEmpty()) {
      schedule = store.state.schedules.get(store.state.schedules.size() - 1);
    }

    if (schedule == null) {
      return new MvpStoreRuntimeBase.ReportVersionBinding(
        MvpStoreRuntimeBase.LIVE_REPORT_VERSION_NO,
        MvpStoreRuntimeBase.LIVE_REPORT_STATUS,
        null
      );
    }

    Set<String> orderNos = new HashSet<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (task.orderNo != null && !task.orderNo.isBlank()) {
        orderNos.add(task.orderNo);
      }
    }
    return new MvpStoreRuntimeBase.ReportVersionBinding(schedule.versionNo, schedule.status, orderNos);
  }
}
