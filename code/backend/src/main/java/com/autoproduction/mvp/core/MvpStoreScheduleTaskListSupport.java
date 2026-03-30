package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleTaskListSupport {
  private MvpStoreScheduleTaskListSupport() {}

  static List<Map<String, Object>> listScheduleTasks(MvpStoreScheduleLoadDomain store, String versionNo) {
    MvpDomain.ScheduleVersion schedule = store.getScheduleEntity(versionNo);
    List<Map<String, Object>> tasks = new ArrayList<>();
    Map<String, MvpDomain.ScheduleTask> taskByTaskKey = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      taskByTaskKey.put(task.taskKey, task);
    }
    Map<String, Map<String, Object>> unscheduledByTaskKey = new HashMap<>();
    for (Map<String, Object> item : schedule.unscheduled) {
      String taskKey = store.firstString(item, "taskKey", "task_key");
      if (taskKey == null || taskKey.isBlank()) {
        continue;
      }
      unscheduledByTaskKey.put(taskKey, item);
    }
    Set<String> scheduledTaskKeys = new HashSet<>();
    int id = 1;
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      scheduledTaskKeys.add(allocation.taskKey);
      MvpDomain.ScheduleTask task = taskByTaskKey.get(allocation.taskKey);
      Map<String, Object> unscheduled = unscheduledByTaskKey.get(allocation.taskKey);
      String reasonCode = store.resolveUnscheduledReasonCode(unscheduled);
      String dependencyStatus = store.resolveTaskDependencyStatus(task, unscheduled);
      String lastBlockReason = store.resolveTaskLastBlockReason(task, unscheduled, reasonCode);
      String taskStatus = store.resolveTaskStatus(task, unscheduled, true);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", id++);
      row.put("version_no", schedule.versionNo);
      row.put("order_no", allocation.orderNo);
      row.put("order_type", "production");
      row.put("process_code", allocation.processCode);
      row.put("company_code", store.firstNonBlank(allocation.companyCode, store.companyCodeForProcess(allocation.processCode)));
      row.put("workshop_code", store.firstNonBlank(allocation.workshopCode, store.workshopCodeForProcess(allocation.processCode)));
      row.put("line_code", store.firstNonBlank(allocation.lineCode, store.lineCodeForProcess(allocation.processCode)));
      row.put("line_name", store.firstNonBlank(allocation.lineName, store.lineNameForCode(store.lineCodeForProcess(allocation.processCode))));
      row.put("calendar_date", allocation.date);
      row.put("shift_code", "D".equals(allocation.shiftCode) ? "DAY" : "NIGHT");
      row.put("plan_start_time", store.toDateTime(allocation.date, allocation.shiftCode, true));
      row.put("plan_finish_time", store.toDateTime(allocation.date, allocation.shiftCode, false));
      row.put("plan_qty", allocation.scheduledQty);
      row.put("lock_flag", store.findOrder(allocation.orderNo).lockFlag ? 1 : 0);
      row.put("priority", store.findOrder(allocation.orderNo).urgent ? 1 : 0);
      row.put("dependency_status", dependencyStatus);
      row.put("task_status", taskStatus);
      row.put("last_block_reason", lastBlockReason);
      row.put("unscheduled_reason_code", reasonCode);
      row.put("unscheduled_reason_cn", reasonCode == null ? null : store.scheduleReasonNameCn(reasonCode));
      tasks.add(store.localizeRow(row));
    }
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (scheduledTaskKeys.contains(task.taskKey)) {
        continue;
      }
      Map<String, Object> unscheduled = unscheduledByTaskKey.get(task.taskKey);
      if (unscheduled == null) {
        continue;
      }
      String reasonCode = store.resolveUnscheduledReasonCode(unscheduled);
      String dependencyStatus = store.resolveTaskDependencyStatus(task, unscheduled);
      String lastBlockReason = store.resolveTaskLastBlockReason(task, unscheduled, reasonCode);
      String taskStatus = store.resolveTaskStatus(task, unscheduled, false);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", id++);
      row.put("version_no", schedule.versionNo);
      row.put("order_no", task.orderNo);
      row.put("order_type", "production");
      row.put("process_code", task.processCode);
      row.put("company_code", store.companyCodeForProcess(task.processCode));
      row.put("workshop_code", store.workshopCodeForProcess(task.processCode));
      row.put("line_code", store.lineCodeForProcess(task.processCode));
      row.put("line_name", store.lineNameForCode(store.lineCodeForProcess(task.processCode)));
      row.put("calendar_date", null);
      row.put("shift_code", null);
      row.put("plan_start_time", null);
      row.put("plan_finish_time", null);
      row.put("plan_qty", 0d);
      row.put("lock_flag", store.findOrder(task.orderNo).lockFlag ? 1 : 0);
      row.put("priority", store.findOrder(task.orderNo).urgent ? 1 : 0);
      row.put("dependency_status", dependencyStatus);
      row.put("task_status", taskStatus);
      row.put("last_block_reason", lastBlockReason);
      row.put("unscheduled_reason_code", reasonCode);
      row.put("unscheduled_reason_cn", reasonCode == null ? null : store.scheduleReasonNameCn(reasonCode));
      tasks.add(store.localizeRow(row));
    }
    return tasks;
  }
}

