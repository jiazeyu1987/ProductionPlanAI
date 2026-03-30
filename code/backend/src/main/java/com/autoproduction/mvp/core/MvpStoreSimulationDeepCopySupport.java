package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreSimulationDeepCopySupport {
  private MvpStoreSimulationDeepCopySupport() {}

  static MvpDomain.State deepCopyState(MvpStoreSimulationEngineSupport domain, MvpDomain.State source) {
    MvpDomain.State target = new MvpDomain.State();
    target.startDate = source.startDate;
    target.horizonDays = source.horizonDays;
    target.shiftsPerDay = source.shiftsPerDay;
    target.shiftHours = source.shiftHours;
    target.skipStatutoryHolidays = source.skipStatutoryHolidays;
    target.weekendRestMode = MvpStoreRuntimeBase.normalizeWeekendRestMode(source.weekendRestMode);
    target.dateShiftModeByDate = source.dateShiftModeByDate == null
      ? new LinkedHashMap<>()
      : new LinkedHashMap<>(source.dateShiftModeByDate);
    target.strictRoute = source.strictRoute;

    target.processes = new ArrayList<>(source.processes.stream()
      .map(row -> new MvpDomain.ProcessConfig(row.processCode, row.capacityPerShift, row.requiredWorkers, row.requiredMachines))
      .toList());

    Map<String, List<MvpDomain.ProcessStep>> processRoutes = new LinkedHashMap<>();
    for (Map.Entry<String, List<MvpDomain.ProcessStep>> entry : source.processRoutes.entrySet()) {
      List<MvpDomain.ProcessStep> steps = new ArrayList<>(entry.getValue().stream()
        .map(step -> new MvpDomain.ProcessStep(step.processCode, step.dependencyType))
        .toList());
      processRoutes.put(entry.getKey(), steps);
    }
    target.processRoutes = processRoutes;

    target.shiftCalendar = new ArrayList<>(source.shiftCalendar.stream()
      .map(row -> new MvpDomain.ShiftRow(row.date, row.shiftCode, row.open))
      .toList());
    target.workerPools = new ArrayList<>(source.workerPools.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.machinePools = new ArrayList<>(source.machinePools.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.initialWorkerOccupancy = new ArrayList<>(source.initialWorkerOccupancy.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.initialMachineOccupancy = new ArrayList<>(source.initialMachineOccupancy.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.materialAvailability = new ArrayList<>(source.materialAvailability.stream()
      .map(row -> new MvpDomain.MaterialRow(row.date, row.shiftCode, row.productCode, row.processCode, row.availableQty))
      .toList());
    target.lineProcessBindings = new ArrayList<>(source.lineProcessBindings.stream()
      .map(row -> new MvpDomain.LineProcessBinding(
        MvpStoreCoreNormalizationSupport.normalizeCompanyCode(row.companyCode),
        row.workshopCode,
        row.lineCode,
        row.lineName,
        row.processCode,
        row.enabled,
        row.capacityPerShift,
        row.requiredWorkers,
        row.requiredMachines
      ))
      .toList());

    target.orders = new ArrayList<>(source.orders.stream().map(domain::deepCopyOrder).toList());
    target.schedules = new ArrayList<>(source.schedules.stream().map(domain::deepCopyScheduleVersion).toList());
    target.publishedVersionNo = source.publishedVersionNo;
    target.reportings = new ArrayList<>(source.reportings.stream().map(domain::deepCopyReporting).toList());
    target.scheduleResultWrites = new ArrayList<>(domain.deepCopyList(source.scheduleResultWrites));
    target.scheduleStatusWrites = new ArrayList<>(domain.deepCopyList(source.scheduleStatusWrites));
    target.wipLots = new ArrayList<>(domain.deepCopyList(source.wipLots));
    target.wipLotEvents = new ArrayList<>(domain.deepCopyList(source.wipLotEvents));
    target.replanJobs = new ArrayList<>(domain.deepCopyList(source.replanJobs));
    target.alerts = new ArrayList<>(domain.deepCopyList(source.alerts));
    target.auditLogs = new ArrayList<>(domain.deepCopyList(source.auditLogs));
    target.dispatchCommands = new ArrayList<>(domain.deepCopyList(source.dispatchCommands));
    target.dispatchApprovals = new ArrayList<>(domain.deepCopyList(source.dispatchApprovals));
    target.integrationInbox = new ArrayList<>(domain.deepCopyList(source.integrationInbox));
    target.integrationOutbox = new ArrayList<>(domain.deepCopyList(source.integrationOutbox));

    target.idempotencyLedger = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Object>> entry : source.idempotencyLedger.entrySet()) {
      target.idempotencyLedger.put(entry.getKey(), domain.deepCopyMap(entry.getValue()));
    }
    return target;
  }

  static MvpDomain.Order deepCopyOrder(MvpDomain.Order source) {
    List<MvpDomain.OrderItem> items = source.items.stream()
      .map(item -> new MvpDomain.OrderItem(item.productCode, item.qty, item.completedQty))
      .toList();
    return new MvpDomain.Order(
      source.orderNo,
      source.orderType,
      source.dueDate,
      source.expectedStartDate,
      source.urgent,
      source.frozen,
      source.lockFlag,
      source.status,
      items,
      source.businessData == null ? new MvpDomain.OrderBusinessData() : new MvpDomain.OrderBusinessData(source.businessData)
    );
  }

  static MvpDomain.ScheduleVersion deepCopyScheduleVersion(MvpStoreSimulationEngineSupport domain, MvpDomain.ScheduleVersion source) {
    MvpDomain.ScheduleVersion target = new MvpDomain.ScheduleVersion();
    target.requestId = source.requestId;
    target.versionNo = source.versionNo;
    target.generatedAt = source.generatedAt;
    target.shiftHours = source.shiftHours;
    target.shiftsPerDay = source.shiftsPerDay;
    target.shifts = domain.deepCopyList(source.shifts);

    target.tasks = source.tasks.stream().map(task -> {
      MvpDomain.ScheduleTask row = new MvpDomain.ScheduleTask();
      row.taskKey = task.taskKey;
      row.orderNo = task.orderNo;
      row.itemIndex = task.itemIndex;
      row.stepIndex = task.stepIndex;
      row.productCode = task.productCode;
      row.processCode = task.processCode;
      row.dependencyType = task.dependencyType;
      row.predecessorTaskKey = task.predecessorTaskKey;
      row.targetQty = task.targetQty;
      row.producedQty = task.producedQty;
      return row;
    }).toList();

    target.allocations = source.allocations.stream().map(allocation -> {
      MvpDomain.Allocation row = new MvpDomain.Allocation();
      row.taskKey = allocation.taskKey;
      row.orderNo = allocation.orderNo;
      row.productCode = allocation.productCode;
      row.processCode = allocation.processCode;
      row.companyCode = allocation.companyCode;
      row.workshopCode = allocation.workshopCode;
      row.lineCode = allocation.lineCode;
      row.lineName = allocation.lineName;
      row.dependencyType = allocation.dependencyType;
      row.shiftId = allocation.shiftId;
      row.date = allocation.date;
      row.shiftCode = allocation.shiftCode;
      row.scheduledQty = allocation.scheduledQty;
      row.workersUsed = allocation.workersUsed;
      row.machinesUsed = allocation.machinesUsed;
      row.groupsUsed = allocation.groupsUsed;
      return row;
    }).toList();

    target.unscheduled = domain.deepCopyList(source.unscheduled);
    target.metrics = domain.deepCopyMap(source.metrics);
    target.metadata = domain.deepCopyMap(source.metadata);
    target.status = source.status;
    target.basedOnVersion = source.basedOnVersion;
    target.ruleVersionNo = source.ruleVersionNo;
    target.publishTime = source.publishTime;
    target.createdBy = source.createdBy;
    target.createdAt = source.createdAt;
    target.rollbackFrom = source.rollbackFrom;
    return target;
  }

  static MvpDomain.Reporting deepCopyReporting(MvpDomain.Reporting source) {
    MvpDomain.Reporting target = new MvpDomain.Reporting();
    target.reportingId = source.reportingId;
    target.requestId = source.requestId;
    target.orderNo = source.orderNo;
    target.productCode = source.productCode;
    target.processCode = source.processCode;
    target.reportQty = source.reportQty;
    target.reportTime = source.reportTime;
    target.triggeredReplanJobNo = source.triggeredReplanJobNo;
    target.triggeredAlertId = source.triggeredAlertId;
    return target;
  }

  static MvpStoreRuntimeBase.SimulationState deepCopySimulationState(
    MvpStoreSimulationEngineSupport domain,
    MvpStoreRuntimeBase.SimulationState source
  ) {
    MvpStoreRuntimeBase.SimulationState target = new MvpStoreRuntimeBase.SimulationState();
    target.currentDate = source.currentDate;
    target.seed = source.seed;
    target.scenario = source.scenario;
    target.dailySalesOrderCount = source.dailySalesOrderCount;
    target.salesOrders = new ArrayList<>(domain.deepCopyList(source.salesOrders));
    target.events = new ArrayList<>(domain.deepCopyList(source.events));
    target.lastRunSummary = domain.deepCopyMap(source.lastRunSummary);
    return target;
  }

  static void restoreSimulationState(
    MvpStoreSimulationEngineSupport domain,
    MvpStoreRuntimeBase.SimulationState source
  ) {
    domain.simulationState.currentDate = source.currentDate;
    domain.simulationState.seed = source.seed;
    domain.simulationState.scenario = source.scenario;
    domain.simulationState.dailySalesOrderCount = source.dailySalesOrderCount;
    domain.simulationState.salesOrders = new ArrayList<>(domain.deepCopyList(source.salesOrders));
    domain.simulationState.events = new ArrayList<>(domain.deepCopyList(source.events));
    domain.simulationState.lastRunSummary = domain.deepCopyMap(source.lastRunSummary);
  }
}

