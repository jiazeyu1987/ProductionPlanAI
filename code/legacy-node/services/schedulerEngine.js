const {
  buildShiftSlots,
  buildResourcePoolIndex,
  buildMaterialPoolIndex,
  getPoolValue,
  getMaterialValue,
  toNumber,
} = require("./planningModel");

const EPS = 1e-9;

function generateSchedule(input, options = {}) {
  const planningInput = normalizePlanningInput(input);
  const shiftSlots = buildShiftSlots(planningInput);

  const workerPoolIndex = buildResourcePoolIndex(planningInput.workerPools, "availableWorkers", 0);
  const machinePoolIndex = buildResourcePoolIndex(planningInput.machinePools, "availableMachines", 0);
  const materialPoolIndex = buildMaterialPoolIndex(planningInput.materialAvailability);

  const processConfigMap = new Map();
  for (const process of planningInput.processes) {
    processConfigMap.set(process.processCode, {
      processCode: process.processCode,
      capacityPerShift: Math.max(1, toNumber(process.capacityPerShift, 0)),
      requiredWorkers: Math.max(1, toNumber(process.requiredWorkers, 1)),
      requiredMachines: Math.max(1, toNumber(process.requiredMachines, 1)),
    });
  }

  const routeMap = buildRouteMap(planningInput.processRoutes);
  const taskBundle = buildTasks(planningInput.orders, routeMap, planningInput.strictRoute);
  const tasks = taskBundle.tasks;
  const orderTaskGroups = taskBundle.orderTaskGroups;

  const shiftUsage = new Map();
  const materialUsage = new Map();
  const allocations = [];

  for (const shift of shiftSlots) {
    const producedBeforeShift = snapshotProduced(tasks);

    for (const orderGroup of orderTaskGroups) {
      if (orderGroup.frozen) {
        continue;
      }
      for (const itemGroup of orderGroup.items) {
        for (const taskKey of itemGroup.stepTaskKeys) {
          const task = tasks.get(taskKey);
          if (!task) {
            continue;
          }

          const processConfig = processConfigMap.get(task.processCode);
          if (!processConfig) {
            task.reasons.add("PROCESS_CONFIG_MISSING");
            continue;
          }

          const remainingQty = roundQty(task.targetQty - task.producedQty);
          if (remainingQty <= EPS) {
            continue;
          }

          const allowance = calcAllowance(task, tasks, producedBeforeShift);
          if (allowance <= EPS) {
            task.reasons.add("PREDECESSOR_NOT_READY");
            continue;
          }

          const usageKey = `${shift.shiftId}#${task.processCode}`;
          const usage = shiftUsage.get(usageKey) || { workersUsed: 0, machinesUsed: 0 };

          const workersAvailable = getPoolValue(workerPoolIndex, shift.shiftId, task.processCode);
          const machinesAvailable = getPoolValue(machinePoolIndex, shift.shiftId, task.processCode);

          const workersRemaining = Math.max(0, workersAvailable - usage.workersUsed);
          const machinesRemaining = Math.max(0, machinesAvailable - usage.machinesUsed);

          const maxGroupsByWorkers = Math.floor(workersRemaining / processConfig.requiredWorkers);
          const maxGroupsByMachines = Math.floor(machinesRemaining / processConfig.requiredMachines);
          const maxGroups = Math.min(maxGroupsByWorkers, maxGroupsByMachines);

          if (maxGroups <= 0) {
            if (workersRemaining < processConfig.requiredWorkers) {
              task.reasons.add("WORKER_SHORTAGE");
            }
            if (machinesRemaining < processConfig.requiredMachines) {
              task.reasons.add("MACHINE_SHORTAGE");
            }
            continue;
          }

          const capacityByResources = maxGroups * processConfig.capacityPerShift;

          const materialKey = `${shift.shiftId}#${task.productCode}#${task.processCode}`;
          const materialConsumed = toNumber(materialUsage.get(materialKey), 0);
          const materialAvailable = getMaterialValue(
            materialPoolIndex,
            shift.shiftId,
            task.productCode,
            task.processCode
          );
          const materialRemaining = Math.max(0, materialAvailable - materialConsumed);

          if (materialRemaining <= EPS) {
            task.reasons.add("MATERIAL_SHORTAGE");
            continue;
          }

          const maxSchedulable = Math.min(remainingQty, allowance, capacityByResources, materialRemaining);
          if (maxSchedulable <= EPS) {
            task.reasons.add("CAPACITY_LIMIT");
            continue;
          }

          const scheduledQty = roundQty(maxSchedulable);
          const groupsUsed = Math.ceil(scheduledQty / processConfig.capacityPerShift);
          usage.workersUsed += groupsUsed * processConfig.requiredWorkers;
          usage.machinesUsed += groupsUsed * processConfig.requiredMachines;
          shiftUsage.set(usageKey, usage);

          materialUsage.set(materialKey, materialConsumed + scheduledQty);
          task.producedQty = roundQty(task.producedQty + scheduledQty);

          if (scheduledQty + EPS < remainingQty && materialRemaining <= scheduledQty + EPS) {
            task.reasons.add("MATERIAL_SHORTAGE");
          }
          if (scheduledQty + EPS < remainingQty && capacityByResources <= scheduledQty + EPS) {
            task.reasons.add("CAPACITY_LIMIT");
          }

          allocations.push({
            taskKey: task.taskKey,
            orderNo: task.orderNo,
            productCode: task.productCode,
            processCode: task.processCode,
            dependencyType: task.dependencyType,
            shiftId: shift.shiftId,
            date: shift.date,
            shiftCode: shift.shiftCode,
            scheduledQty,
            workersUsed: groupsUsed * processConfig.requiredWorkers,
            machinesUsed: groupsUsed * processConfig.requiredMachines,
            groupsUsed,
          });
        }
      }
    }
  }

  const unscheduled = [];
  for (const task of tasks.values()) {
    const remaining = roundQty(task.targetQty - task.producedQty);
    if (remaining <= EPS) {
      continue;
    }
    const reasons = Array.from(task.reasons);
    unscheduled.push({
      taskKey: task.taskKey,
      orderNo: task.orderNo,
      productCode: task.productCode,
      processCode: task.processCode,
      remainingQty: remaining,
      reasons: reasons.length === 0 ? ["CAPACITY_LIMIT"] : reasons,
    });
  }

  return {
    requestId: options.requestId || `req-${Date.now()}`,
    versionNo: options.versionNo || `V${new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14)}`,
    generatedAt: new Date().toISOString(),
    shiftHours: planningInput.shiftHours,
    shiftsPerDay: planningInput.shiftsPerDay,
    shifts: shiftSlots,
    tasks: Array.from(tasks.values()).map(toPublicTask),
    allocations,
    unscheduled,
    metrics: buildMetrics(tasks, allocations, unscheduled),
    metadata: {
      hardConstraints: ["MAN", "MACHINE", "MATERIAL"],
      dependencyTypes: ["FS", "SS"],
      filteredFrozenOrders: orderTaskGroups.filter((it) => it.frozen).map((it) => it.orderNo),
    },
  };
}

function normalizePlanningInput(input) {
  if (!input || typeof input !== "object") {
    throw new Error("Planning input is required.");
  }

  const result = {
    startDate: input.startDate,
    horizonDays: toNumber(input.horizonDays, 7),
    shiftsPerDay: toNumber(input.shiftsPerDay, 1),
    shiftHours: toNumber(input.shiftHours, 12),
    shiftCalendar: Array.isArray(input.shiftCalendar) ? input.shiftCalendar : [],
    processes: Array.isArray(input.processes) ? input.processes : [],
    processRoutes: input.processRoutes || {},
    workerPools: Array.isArray(input.workerPools) ? input.workerPools : [],
    machinePools: Array.isArray(input.machinePools) ? input.machinePools : [],
    materialAvailability: Array.isArray(input.materialAvailability) ? input.materialAvailability : [],
    orders: Array.isArray(input.orders) ? input.orders : [],
    strictRoute: input.strictRoute !== false,
  };

  if (!result.startDate) {
    throw new Error("startDate is required.");
  }
  if (result.shiftHours !== 12) {
    throw new Error("P0 requires shiftHours = 12.");
  }
  if (result.shiftsPerDay < 1 || result.shiftsPerDay > 2) {
    throw new Error("P0 requires shiftsPerDay in range [1,2].");
  }
  if (result.processes.length === 0) {
    throw new Error("At least one process config is required.");
  }

  return result;
}

function buildRouteMap(processRoutes) {
  if (Array.isArray(processRoutes)) {
    const map = new Map();
    for (const routeRow of processRoutes) {
      map.set(routeRow.productCode, normalizeSteps(routeRow.steps));
    }
    return map;
  }

  const map = new Map();
  for (const [productCode, steps] of Object.entries(processRoutes)) {
    map.set(productCode, normalizeSteps(steps));
  }
  return map;
}

function normalizeSteps(steps) {
  const normalized = (Array.isArray(steps) ? steps : []).map((step, index) => ({
    stepIndex: index,
    processCode: step.processCode,
    dependencyType: (step.dependencyType || "FS").toUpperCase(),
  }));

  for (const step of normalized) {
    if (step.dependencyType !== "FS" && step.dependencyType !== "SS") {
      throw new Error(`Unsupported dependencyType: ${step.dependencyType}`);
    }
  }

  return normalized;
}

function buildTasks(orders, routeMap, strictRoute) {
  const sortedOrders = [...orders].sort(compareOrders);
  const tasks = new Map();
  const orderTaskGroups = [];

  for (const order of sortedOrders) {
    const orderNo = order.orderNo;
    const items = normalizeOrderItems(order);
    const orderGroup = {
      orderNo,
      frozen: !!order.frozen,
      items: [],
    };

    for (let itemIdx = 0; itemIdx < items.length; itemIdx += 1) {
      const item = items[itemIdx];
      const routeSteps = routeMap.get(item.productCode);
      if (!routeSteps || routeSteps.length === 0) {
        if (strictRoute) {
          throw new Error(`Route not found for product ${item.productCode}.`);
        }
        continue;
      }

      const stepTaskKeys = [];
      for (let stepIdx = 0; stepIdx < routeSteps.length; stepIdx += 1) {
        const step = routeSteps[stepIdx];
        const taskKey = `${orderNo}#${itemIdx}#${stepIdx}`;
        const predecessorTaskKey = stepIdx > 0 ? `${orderNo}#${itemIdx}#${stepIdx - 1}` : null;
        tasks.set(taskKey, {
          taskKey,
          orderNo,
          orderDueDate: order.dueDate,
          itemIndex: itemIdx,
          stepIndex: stepIdx,
          productCode: item.productCode,
          processCode: step.processCode,
          dependencyType: step.dependencyType,
          predecessorTaskKey,
          targetQty: toNumber(item.qty, 0),
          producedQty: 0,
          reasons: new Set(),
        });
        stepTaskKeys.push(taskKey);
      }

      orderGroup.items.push({
        itemIndex: itemIdx,
        productCode: item.productCode,
        stepTaskKeys,
      });
    }

    orderTaskGroups.push(orderGroup);
  }

  return { tasks, orderTaskGroups };
}

function normalizeOrderItems(order) {
  if (Array.isArray(order.items) && order.items.length > 0) {
    return order.items.map((item) => ({
      productCode: item.productCode,
      qty: toNumber(item.qty, 0),
    }));
  }

  if (order.productCode && toNumber(order.quantity, 0) > 0) {
    return [
      {
        productCode: order.productCode,
        qty: toNumber(order.quantity, 0),
      },
    ];
  }

  return [];
}

function compareOrders(a, b) {
  const urgentDiff = Number(!!b.urgent) - Number(!!a.urgent);
  if (urgentDiff !== 0) {
    return urgentDiff;
  }

  const dueA = a.dueDate || "9999-12-31";
  const dueB = b.dueDate || "9999-12-31";
  if (dueA !== dueB) {
    return dueA.localeCompare(dueB);
  }

  return String(a.orderNo || "").localeCompare(String(b.orderNo || ""));
}

function snapshotProduced(tasks) {
  const map = new Map();
  for (const [key, task] of tasks.entries()) {
    map.set(key, task.producedQty);
  }
  return map;
}

function calcAllowance(task, tasks, producedBeforeShift) {
  const remainingQty = roundQty(task.targetQty - task.producedQty);
  if (remainingQty <= EPS) {
    return 0;
  }

  if (!task.predecessorTaskKey) {
    return remainingQty;
  }

  const predecessorTask = tasks.get(task.predecessorTaskKey);
  if (!predecessorTask) {
    return 0;
  }

  if (task.dependencyType === "FS") {
    const predecessorBefore = toNumber(producedBeforeShift.get(task.predecessorTaskKey), 0);
    return Math.max(0, roundQty(predecessorBefore - task.producedQty));
  }

  const predecessorNow = predecessorTask.producedQty;
  return predecessorNow > EPS ? Math.max(0, roundQty(predecessorNow - task.producedQty)) : 0;
}

function buildMetrics(tasks, allocations, unscheduled) {
  let targetQty = 0;
  let producedQty = 0;
  for (const task of tasks.values()) {
    targetQty += task.targetQty;
    producedQty += task.producedQty;
  }

  return {
    taskCount: tasks.size,
    allocationCount: allocations.length,
    targetQty: roundQty(targetQty),
    scheduledQty: roundQty(producedQty),
    scheduleCompletionRate: targetQty > EPS ? roundQty((producedQty / targetQty) * 100) : 0,
    unscheduledTaskCount: unscheduled.length,
  };
}

function roundQty(value) {
  return Math.round(toNumber(value, 0) * 1000) / 1000;
}

function toPublicTask(task) {
  return {
    taskKey: task.taskKey,
    orderNo: task.orderNo,
    itemIndex: task.itemIndex,
    stepIndex: task.stepIndex,
    productCode: task.productCode,
    processCode: task.processCode,
    dependencyType: task.dependencyType,
    predecessorTaskKey: task.predecessorTaskKey,
    targetQty: task.targetQty,
    producedQty: task.producedQty,
  };
}

module.exports = {
  generateSchedule,
  normalizePlanningInput,
};
