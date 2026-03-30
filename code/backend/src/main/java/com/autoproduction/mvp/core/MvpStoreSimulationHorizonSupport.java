package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class MvpStoreSimulationHorizonSupport {
  private MvpStoreSimulationHorizonSupport() {}

  static Map<String, Object> rebuildPlanningHorizon(
    MvpStoreSimulationEngineSupport domain,
    LocalDate startDate,
    String scenario,
    Random random,
    String requestId
  ) {
    domain.state.startDate = startDate;
    int horizonDays = Math.max(1, domain.state.horizonDays);
    int shiftsPerDay = Math.max(1, Math.min(2, domain.state.shiftsPerDay));
    String[] shiftCodes = {"D", "N"};
    double capacityFactor = MvpStoreCoreNormalizationSupport.scenarioCapacityFactor(scenario, random);

    String breakdownProcess = null;
    if ("BREAKDOWN".equals(scenario) && !domain.state.processes.isEmpty()) {
      breakdownProcess = domain.state.processes.get(random.nextInt(domain.state.processes.size())).processCode;
    }

    domain.state.shiftCalendar = new ArrayList<>();
    domain.state.workerPools = new ArrayList<>();
    domain.state.machinePools = new ArrayList<>();
    domain.state.initialWorkerOccupancy = new ArrayList<>();
    domain.state.initialMachineOccupancy = new ArrayList<>();
    domain.state.materialAvailability = new ArrayList<>();

    for (int i = 0; i < horizonDays; i += 1) {
      LocalDate date = startDate.plusDays(i);
      for (int j = 0; j < shiftsPerDay; j += 1) {
        String shiftCode = shiftCodes[j];
        domain.state.shiftCalendar.add(new MvpDomain.ShiftRow(date, shiftCode, true));

        for (MvpDomain.ProcessConfig process : domain.state.processes) {
          int baseWorkers = MvpStoreRuntimeBase.BASE_WORKERS_BY_PROCESS.getOrDefault(
            process.processCode,
            Math.max(2, process.requiredWorkers * 3)
          );
          int baseMachines = MvpStoreRuntimeBase.BASE_MACHINES_BY_PROCESS.getOrDefault(
            process.processCode,
            Math.max(1, process.requiredMachines * 2)
          );
          int workers = Math.max(process.requiredWorkers, (int) Math.round(baseWorkers * capacityFactor));
          int machines = Math.max(process.requiredMachines, (int) Math.round(baseMachines * capacityFactor));

          if (breakdownProcess != null
            && breakdownProcess.equals(process.processCode)
            && i == 0
            && "D".equals(shiftCode)) {
            workers = Math.max(1, workers / 3);
            machines = 0;
          }

          domain.state.workerPools.add(new MvpDomain.ResourceRow(date, shiftCode, process.processCode, workers));
          domain.state.machinePools.add(new MvpDomain.ResourceRow(date, shiftCode, process.processCode, machines));
        }

        double materialQty = switch (scenario) {
          case "TIGHT" -> 4200d;
          case "BREAKDOWN" -> 4600d;
          default -> 5000d;
        };
        for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : domain.state.processRoutes.entrySet()) {
          for (MvpDomain.ProcessStep step : route.getValue()) {
            domain.state.materialAvailability.add(
              new MvpDomain.MaterialRow(date, shiftCode, route.getKey(), step.processCode, materialQty)
            );
          }
        }
      }
    }

    domain.appendSimulationEvent(
      startDate,
      "CAPACITY_CHANGED",
      "已应用产能场景。",
      requestId,
      Map.of(
        "scenario", scenario,
        "capacity_factor", MvpStoreCoreNormalizationSupport.round2(capacityFactor),
        "breakdown_process", breakdownProcess == null ? "" : breakdownProcess
      )
    );

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("scenario", scenario);
    out.put("capacity_factor", MvpStoreCoreNormalizationSupport.round2(capacityFactor));
    out.put("breakdown_process", breakdownProcess);
    return out;
  }
}

