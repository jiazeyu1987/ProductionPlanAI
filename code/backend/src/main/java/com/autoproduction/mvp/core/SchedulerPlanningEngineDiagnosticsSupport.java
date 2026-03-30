package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentTotalForOrder;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import com.autoproduction.mvp.core.SchedulerPlanningSupport.ReasonInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningEngineDiagnosticsSupport {
  private SchedulerPlanningEngineDiagnosticsSupport() {}

  static List<Map<String, Object>> populateTasksAndBuildUnscheduled(
    SchedulerPlanningEngineIndexSupport.Indexes indexes,
    SchedulerPlanningEngineTaskBuildSupport.TaskBuild taskBuild,
    Set<String> lockedOrderSet,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> preservedLockedTaskKeys,
    Set<String> lockPreemptedTaskKeys,
    Map<String, ReasonInfo> lastBlockedByTask
  ) {
    List<Map<String, Object>> unscheduled = new ArrayList<>();
    for (MvpDomain.ScheduleTask task : taskBuild.tasks().values()) {
      double remaining = round3(task.targetQty - task.producedQty);
      MvpDomain.Order order = taskBuild.orderByNo().get(task.orderNo);
      task.dependencyStatus = SchedulerDependencySupport.dependencyStatusAtVersionEnd(task, taskBuild.tasks(), EPS);
      task.taskStatus =
          SchedulerExplainSupport.resolveTaskStatus(
              order != null && order.frozen,
              order != null && order.lockFlag,
              task.producedQty,
              remaining,
              EPS);
      if (remaining <= EPS) {
        task.lastBlockReason = null;
        task.lastBlockReasonDetail = null;
        task.lastBlockingDimension = null;
        task.lastBlockEvidence = new LinkedHashMap<>();
        continue;
      }
      ReasonInfo reasonInfo;
      if (order != null && order.frozen) {
        reasonInfo = SchedulerDiagnosticsSupport.frozenReason(task, task.dependencyStatus);
      } else if (order != null && lockedOrderSet.contains(order.orderNo)) {
        reasonInfo = SchedulerDiagnosticsSupport.lockedPreservedReason(
          task,
          preservedLockedTaskKeys.contains(task.taskKey),
          baseVersion == null ? null : baseVersion.versionNo,
          task.dependencyStatus,
          lockPreemptedTaskKeys.contains(task.taskKey)
        );
      } else {
        reasonInfo = lastBlockedByTask.get(task.taskKey);
      }
      if (reasonInfo == null) {
        reasonInfo = SchedulerDiagnosticsSupport.diagnoseUnscheduledReason(
          task,
          order,
          taskBuild.tasks(),
          indexes.processConfigMap(),
          indexes.maxWorkersByProcess(),
          indexes.maxMachinesByProcess(),
          indexes.totalMaterialByProductProcess(),
          componentTotalForOrder(order),
          EPS
        );
      }
      task.lastBlockReason = reasonInfo.reasonCode;
      task.lastBlockReasonDetail = reasonInfo.reasonDetail;
      task.lastBlockingDimension = reasonInfo.blockingDimension;
      task.lastBlockEvidence = new LinkedHashMap<>(reasonInfo.evidence);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("taskKey", task.taskKey);
      row.put("orderNo", task.orderNo);
      row.put("productCode", task.productCode);
      row.put("processCode", task.processCode);
      row.put("remainingQty", remaining);
      row.put("reason_code", reasonInfo.reasonCode);
      row.put("reason_detail", reasonInfo.reasonDetail);
      row.put("reason_source", reasonInfo.reasonSource);
      row.put("blocking_dimension", reasonInfo.blockingDimension);
      row.put("dependency_status", reasonInfo.dependencyStatus);
      row.put("task_status", task.taskStatus);
      row.put("last_block_reason", reasonInfo.reasonCode);
      row.put("evidence", new LinkedHashMap<>(reasonInfo.evidence));
      row.put("reasons", SchedulerExplainSupport.reasonCodesForCompatibility(reasonInfo.reasonCode));
      unscheduled.add(row);
    }
    return unscheduled;
  }
}

