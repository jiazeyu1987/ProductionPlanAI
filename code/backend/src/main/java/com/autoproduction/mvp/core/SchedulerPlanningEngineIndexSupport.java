package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.buildShifts;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.cumulativeComponentIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.cumulativeMaterialIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.cumulativeReportedByOrderProductProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.effectiveResourceIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.materialIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.maxResourceByProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.shiftIndexById;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.totalMaterialByProductProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.lineBindingsByProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SchedulerPlanningEngineIndexSupport {
  private SchedulerPlanningEngineIndexSupport() {}

  record Indexes(
    List<Map<String, Object>> shifts,
    Map<String, Integer> shiftIndexByShiftId,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Double> shiftMaterialArrivalByProductProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Integer> maxWorkersByProcess,
    Map<String, Integer> maxMachinesByProcess,
    Map<String, Double> totalMaterialByProductProcess,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess,
    Map<String, Double> cumulativeReportedByOrderProductProcess
  ) {}

  static Indexes build(MvpDomain.State state) {
    List<Map<String, Object>> shifts = buildShifts(state);
    Map<String, Integer> shiftIndexByShiftId = shiftIndexById(shifts);
    Map<String, Integer> workerByShiftProcess = effectiveResourceIndex(state.workerPools, state.initialWorkerOccupancy);
    Map<String, Integer> machineByShiftProcess = effectiveResourceIndex(state.machinePools, state.initialMachineOccupancy);
    Map<String, Double> shiftMaterialArrivalByProductProcess = materialIndex(state.materialAvailability);
    Map<String, Double> cumulativeMaterialByShiftProductProcess = cumulativeMaterialIndex(state.materialAvailability, shifts);
    Map<String, Double> cumulativeComponentByShift = cumulativeComponentIndex(state, shifts);
    Map<String, Integer> maxWorkersByProcess = maxResourceByProcess(workerByShiftProcess);
    Map<String, Integer> maxMachinesByProcess = maxResourceByProcess(machineByShiftProcess);
    Map<String, Double> totalMaterialByProductProcess = totalMaterialByProductProcess(state.materialAvailability);

    Map<String, MvpDomain.ProcessConfig> processConfigMap = new HashMap<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      String processCode = normalizeProcessCode(process.processCode);
      if (processCode != null) {
        processConfigMap.put(processCode, process);
      }
    }
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess = lineBindingsByProcess(state, processConfigMap);
    Map<String, Double> cumulativeReportedByOrderProductProcess = cumulativeReportedByOrderProductProcess(state.reportings);
    return new Indexes(
      shifts,
      shiftIndexByShiftId,
      workerByShiftProcess,
      machineByShiftProcess,
      shiftMaterialArrivalByProductProcess,
      cumulativeMaterialByShiftProductProcess,
      cumulativeComponentByShift,
      maxWorkersByProcess,
      maxMachinesByProcess,
      totalMaterialByProductProcess,
      processConfigMap,
      lineBindingsByProcess,
      cumulativeReportedByOrderProductProcess
    );
  }
}

