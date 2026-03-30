package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningCandidateEvaluator.evaluateCandidateForShift;
import static com.autoproduction.mvp.core.SchedulerPlanningComponents.consumeComponent;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.MAX_ROUNDS_PER_SHIFT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MACHINE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MANPOWER;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_MATERIAL_SHORTAGE;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.addProducedByTaskShift;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.decrementRemainingQtyByOrder;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.registerProcessProductUsage;
import static com.autoproduction.mvp.core.SchedulerPlanningPriority.determineChunkQty;
import static com.autoproduction.mvp.core.SchedulerPlanningPriority.taskPriorityScore;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import com.autoproduction.mvp.core.SchedulerPlanningSupport.ReasonInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningShiftRounds {
  private SchedulerPlanningShiftRounds() {}

  static void scheduleShiftRounds(
    boolean urgentOnly,
    String shiftId,
    String shiftDate,
    String shiftCode,
    int shiftIndex,
    LocalDate shiftDateValue,
    List<MvpDomain.Order> schedulableOrders,
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> lineShiftScheduledQty,
    Map<String, Double> shiftMaterialArrivalByProductProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> shiftMaterialUsed,
    Map<String, Double> materialConsumedByProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Double> componentConsumed,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    Set<String> finalTaskKeys,
    Map<String, Double> producedBeforeShift,
    Map<String, Double> remainingQtyByOrder,
    Map<String, Double> urgentDailyProducedByOrderDate,
    Map<String, Double> processQuotaRemaining,
    String strategyCode,
    StrategyProfile strategyProfile,
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess,
    Map<String, ReasonInfo> lastBlockedByTask,
    List<MvpDomain.Allocation> allocations
  ) {
    Map<String, Double> quotaByProcess = new HashMap<>(processQuotaRemaining);
    for (int round = 0; round < MAX_ROUNDS_PER_SHIFT; round += 1) {
      List<CandidateEvaluation> candidates = new ArrayList<>();
      for (MvpDomain.Order order : schedulableOrders) {
        if (order == null || order.frozen) {
          continue;
        }
        if (urgentOnly && !order.urgent) {
          continue;
        }
        List<MvpDomain.ScheduleTask> orderTasks = tasksByOrder.getOrDefault(order.orderNo, List.of());
        for (MvpDomain.ScheduleTask task : orderTasks) {
          double remainingQty = round3(task.targetQty - task.producedQty);
          if (remainingQty <= EPS) {
            lastBlockedByTask.remove(task.taskKey);
            continue;
          }
          if (urgentOnly && quotaByProcess.getOrDefault(normalizeProcessCode(task.processCode), 0d) <= EPS) {
            continue;
          }
          CandidateEvaluation evaluation = evaluateCandidateForShift(
            order,
            task,
            shiftId,
            shiftCode,
            shiftIndex,
            shiftDateValue,
            tasks,
            processConfigMap,
            lineBindingsByProcess,
            workerByShiftProcess,
            machineByShiftProcess,
            shiftWorkersUsed,
            shiftMachinesUsed,
            lineShiftScheduledQty,
            shiftMaterialArrivalByProductProcess,
            cumulativeMaterialByShiftProductProcess,
            materialConsumedByProductProcess,
            cumulativeComponentByShift,
            componentConsumed,
            producedByTaskShift,
            finalTaskKeys,
            producedBeforeShift,
            lastProductByShiftProcess,
            productMixByShiftProcess
          );
          if (evaluation.feasibleQty <= EPS) {
            if (evaluation.blockedReason != null) {
              lastBlockedByTask.put(task.taskKey, evaluation.blockedReason);
            }
            continue;
          }
          evaluation.score = taskPriorityScore(
            evaluation,
            shiftDateValue,
            remainingQtyByOrder,
            urgentDailyProducedByOrderDate,
            quotaByProcess,
            strategyCode,
            strategyProfile
          );
          candidates.add(evaluation);
        }
      }
      if (candidates.isEmpty()) {
        return;
      }

      candidates.sort(Comparator
        .comparingDouble((CandidateEvaluation c) -> c.score).reversed()
        .thenComparing((CandidateEvaluation c) -> c.order.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(c -> c.order.orderNo)
        .thenComparing(c -> c.task.taskKey));

      boolean progressed = false;
      for (CandidateEvaluation candidate : candidates) {
        if (urgentOnly && quotaByProcess.getOrDefault(normalizeProcessCode(candidate.task.processCode), 0d) <= EPS) {
          continue;
        }
        CandidateEvaluation live = evaluateCandidateForShift(
          candidate.order,
          candidate.task,
          shiftId,
          shiftCode,
          shiftIndex,
          shiftDateValue,
          tasks,
          processConfigMap,
          lineBindingsByProcess,
          workerByShiftProcess,
          machineByShiftProcess,
          shiftWorkersUsed,
          shiftMachinesUsed,
          lineShiftScheduledQty,
          shiftMaterialArrivalByProductProcess,
          cumulativeMaterialByShiftProductProcess,
          materialConsumedByProductProcess,
          cumulativeComponentByShift,
          componentConsumed,
          producedByTaskShift,
          finalTaskKeys,
          producedBeforeShift,
          lastProductByShiftProcess,
          productMixByShiftProcess
        );
        if (live.feasibleQty <= EPS) {
          if (live.blockedReason != null) {
            lastBlockedByTask.put(candidate.task.taskKey, live.blockedReason);
          }
          continue;
        }

        double plannedQty = determineChunkQty(
          live,
          shiftDateValue,
          remainingQtyByOrder,
          urgentDailyProducedByOrderDate,
          quotaByProcess,
          strategyCode
        );
        if (urgentOnly) {
          plannedQty = Math.min(plannedQty, quotaByProcess.getOrDefault(normalizeProcessCode(live.task.processCode), 0d));
        }
        plannedQty = round3(Math.min(live.feasibleQty, plannedQty));
        if (plannedQty <= EPS) {
          continue;
        }

        int workersUsedDelta = live.workersNeededDelta;
        int machinesUsedDelta = live.machinesNeededDelta;
        int groupsUsed = (workersUsedDelta > 0 || machinesUsedDelta > 0) ? 1 : 0;
        if (plannedQty <= EPS) {
          continue;
        }

        shiftWorkersUsed.put(live.resourceUsageKey, live.workersUsed + workersUsedDelta);
        shiftMachinesUsed.put(live.resourceUsageKey, live.machinesUsed + machinesUsedDelta);
        lineShiftScheduledQty.put(live.processUsageKey, round3(live.lineScheduledQty + plannedQty));
        shiftMaterialUsed.put(live.materialShiftKey, round3(shiftMaterialUsed.getOrDefault(live.materialShiftKey, 0d) + plannedQty));
        materialConsumedByProductProcess.put(
          live.materialConsumedKey,
          round3(materialConsumedByProductProcess.getOrDefault(live.materialConsumedKey, 0d) + plannedQty)
        );
        consumeComponent(componentConsumed, live.componentKey, plannedQty);

        live.task.producedQty = round3(live.task.producedQty + plannedQty);
        decrementRemainingQtyByOrder(remainingQtyByOrder, live.task.orderNo, plannedQty, live.finalStep);
        addProducedByTaskShift(producedByTaskShift, live.task.taskKey, shiftIndex, plannedQty);
        registerProcessProductUsage(lastProductByShiftProcess, productMixByShiftProcess, live.processUsageKey, live.task.productCode);

        MvpDomain.Allocation allocation = new MvpDomain.Allocation();
        allocation.taskKey = live.task.taskKey;
        allocation.orderNo = live.task.orderNo;
        allocation.productCode = live.task.productCode;
        allocation.processCode = live.task.processCode;
        allocation.companyCode = live.lineBinding.companyCode;
        allocation.workshopCode = live.lineBinding.workshopCode;
        allocation.lineCode = live.lineBinding.lineCode;
        allocation.lineName = live.lineBinding.lineName;
        allocation.dependencyType = live.task.dependencyType;
        allocation.shiftId = shiftId;
        allocation.date = shiftDate;
        allocation.shiftCode = shiftCode;
        allocation.scheduledQty = plannedQty;
        allocation.workersUsed = workersUsedDelta;
        allocation.machinesUsed = machinesUsedDelta;
        allocation.groupsUsed = groupsUsed;
        allocations.add(allocation);

        if (live.order.urgent) {
          String dailyKey = shiftDate + "#" + live.order.orderNo;
          urgentDailyProducedByOrderDate.put(dailyKey, round3(urgentDailyProducedByOrderDate.getOrDefault(dailyKey, 0d) + plannedQty));
        }
        if (urgentOnly) {
          quotaByProcess.put(
            normalizeProcessCode(live.task.processCode),
            round3(Math.max(0d, quotaByProcess.getOrDefault(normalizeProcessCode(live.task.processCode), 0d) - plannedQty))
          );
        }

        double remainingAfter = round3(live.task.targetQty - live.task.producedQty);
        if (remainingAfter <= EPS) {
          lastBlockedByTask.remove(live.task.taskKey);
        } else if (live.allowanceQty <= live.capacityQty + EPS && live.allowanceQty <= live.materialRemainingQty + EPS) {
          lastBlockedByTask.put(
            live.task.taskKey,
            SchedulerDiagnosticsSupport.dependencyBlockedReason(
              live.task,
              tasks,
              producedBeforeShift,
              producedByTaskShift,
              shiftIndex,
              EPS
            )
          );
        } else if (live.materialRemainingQty <= live.capacityQty + EPS) {
          Map<String, Object> evidence = new LinkedHashMap<>();
          evidence.put("process_code", live.task.processCode);
          evidence.put("product_code", live.task.productCode);
          evidence.put("material_remaining", round3(live.materialRemainingQty));
          evidence.put("remaining_after_shift", remainingAfter);
          lastBlockedByTask.put(
            live.task.taskKey,
            new ReasonInfo(
              REASON_MATERIAL_SHORTAGE,
              "Material cumulative balance is the binding constraint in current shift.",
              "ENGINE",
              "MATERIAL",
              SchedulerDependencySupport.dependencyStatusAtShift(live.task, tasks, producedBeforeShift, EPS),
              evidence
            )
          );
        } else {
          String reasonCode =
              SchedulerExplainSupport.capacityReasonCode(
                  live.groupsByWorkers, live.groupsByMachines);
          Map<String, Object> evidence = new LinkedHashMap<>();
          evidence.put("process_code", live.task.processCode);
          evidence.put("capacity_by_resources", round3(live.capacityQty));
          evidence.put("remaining_after_shift", remainingAfter);
          evidence.put("workers_remaining", live.workersRemaining);
          evidence.put("machines_remaining", live.machinesRemaining);
          evidence.put("workers_required_per_shift", live.processConfig.requiredWorkers);
          evidence.put("machines_required_per_shift", live.processConfig.requiredMachines);
          if (live.changeoverPenaltyApplied) {
            evidence.put("changeover_penalty_applied", true);
          }
          lastBlockedByTask.put(
            live.task.taskKey,
            new ReasonInfo(
              reasonCode,
              reasonCode.equals(REASON_CAPACITY_MANPOWER)
                ? "Worker capacity is the binding constraint in current shift."
                : reasonCode.equals(REASON_CAPACITY_MACHINE)
                  ? "Machine capacity is the binding constraint in current shift."
                  : "Resource capacity is the binding constraint in current shift.",
              "ENGINE",
              "CAPACITY",
              SchedulerDependencySupport.dependencyStatusAtShift(live.task, tasks, producedBeforeShift, EPS),
              evidence
            )
          );
        }
        progressed = true;
      }
      if (!progressed) {
        return;
      }
    }
  }
}
