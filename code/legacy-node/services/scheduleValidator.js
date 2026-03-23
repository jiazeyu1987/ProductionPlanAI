const {
  buildShiftSlots,
  buildResourcePoolIndex,
  buildMaterialPoolIndex,
  getPoolValue,
  getMaterialValue,
  toNumber,
} = require("./planningModel");
const { normalizePlanningInput } = require("./schedulerEngine");

const EPS = 1e-9;

function validateSchedule(input, schedule) {
  const planningInput = normalizePlanningInput(input);
  const shifts = buildShiftSlots(planningInput);

  const workerPoolIndex = buildResourcePoolIndex(planningInput.workerPools, "availableWorkers", 0);
  const machinePoolIndex = buildResourcePoolIndex(planningInput.machinePools, "availableMachines", 0);
  const materialPoolIndex = buildMaterialPoolIndex(planningInput.materialAvailability);

  const violations = [];

  validateResourceUsage(schedule, workerPoolIndex, machinePoolIndex, materialPoolIndex, violations);
  validateDependencies(schedule, shifts, violations);
  validateFrozenOrders(planningInput.orders, schedule, violations);

  return {
    passed: violations.length === 0,
    violationCount: violations.length,
    violations,
  };
}

function validateResourceUsage(schedule, workerPoolIndex, machinePoolIndex, materialPoolIndex, violations) {
  const processUsage = new Map();
  const materialUsage = new Map();

  for (const allocation of schedule.allocations || []) {
    const processKey = `${allocation.shiftId}#${allocation.processCode}`;
    const usage = processUsage.get(processKey) || { workersUsed: 0, machinesUsed: 0 };
    usage.workersUsed += toNumber(allocation.workersUsed, 0);
    usage.machinesUsed += toNumber(allocation.machinesUsed, 0);
    processUsage.set(processKey, usage);

    const materialKey = `${allocation.shiftId}#${allocation.productCode}#${allocation.processCode}`;
    materialUsage.set(materialKey, toNumber(materialUsage.get(materialKey), 0) + toNumber(allocation.scheduledQty, 0));
  }

  for (const [key, usage] of processUsage.entries()) {
    const cut = key.lastIndexOf("#");
    const shiftId = key.slice(0, cut);
    const processCode = key.slice(cut + 1);
    const workersAvailable = getPoolValue(workerPoolIndex, shiftId, processCode);
    const machinesAvailable = getPoolValue(machinePoolIndex, shiftId, processCode);

    if (usage.workersUsed - workersAvailable > EPS) {
      violations.push({
        code: "WORKER_OVERUSED",
        shiftId,
        processCode,
        workersUsed: usage.workersUsed,
        workersAvailable,
      });
    }

    if (usage.machinesUsed - machinesAvailable > EPS) {
      violations.push({
        code: "MACHINE_OVERUSED",
        shiftId,
        processCode,
        machinesUsed: usage.machinesUsed,
        machinesAvailable,
      });
    }
  }

  for (const [key, consumedQty] of materialUsage.entries()) {
    const lastCut = key.lastIndexOf("#");
    const firstCut = key.slice(0, lastCut).lastIndexOf("#");
    const shiftId = key.slice(0, firstCut);
    const productCode = key.slice(firstCut + 1, lastCut);
    const processCode = key.slice(lastCut + 1);
    const availableQty = getMaterialValue(materialPoolIndex, shiftId, productCode, processCode);
    if (consumedQty - availableQty > EPS) {
      violations.push({
        code: "MATERIAL_OVERUSED",
        shiftId,
        productCode,
        processCode,
        consumedQty,
        availableQty,
      });
    }
  }
}

function validateDependencies(schedule, shifts, violations) {
  const tasks = new Map();
  for (const task of schedule.tasks || []) {
    tasks.set(task.taskKey, task);
  }

  const shiftIndex = new Map(shifts.map((shift, index) => [shift.shiftId, index]));
  const qtyByTaskByShift = new Map();

  for (const allocation of schedule.allocations || []) {
    if (!shiftIndex.has(allocation.shiftId)) {
      continue;
    }
    const taskKey = allocation.taskKey;
    const byShift = qtyByTaskByShift.get(taskKey) || new Map();
    const idx = shiftIndex.get(allocation.shiftId);
    byShift.set(idx, toNumber(byShift.get(idx), 0) + toNumber(allocation.scheduledQty, 0));
    qtyByTaskByShift.set(taskKey, byShift);
  }

  for (const task of tasks.values()) {
    if (!task.predecessorTaskKey) {
      continue;
    }

    const taskCurve = buildCumulativeCurve(qtyByTaskByShift.get(task.taskKey), shifts.length);
    const predecessorCurve = buildCumulativeCurve(qtyByTaskByShift.get(task.predecessorTaskKey), shifts.length);

    for (let i = 0; i < shifts.length; i += 1) {
      const currentCum = taskCurve[i];
      const dependencyBase = task.dependencyType === "FS" ? (i === 0 ? 0 : predecessorCurve[i - 1]) : predecessorCurve[i];
      if (currentCum - dependencyBase > EPS) {
        violations.push({
          code: "DEPENDENCY_VIOLATION",
          taskKey: task.taskKey,
          predecessorTaskKey: task.predecessorTaskKey,
          dependencyType: task.dependencyType,
          shiftId: shifts[i].shiftId,
          currentCum,
          dependencyBase,
        });
        break;
      }
    }
  }
}

function buildCumulativeCurve(byShift, totalShifts) {
  const curve = new Array(totalShifts).fill(0);
  if (!byShift) {
    return curve;
  }
  for (let i = 0; i < totalShifts; i += 1) {
    const prev = i === 0 ? 0 : curve[i - 1];
    curve[i] = prev + toNumber(byShift.get(i), 0);
  }
  return curve;
}

function validateFrozenOrders(orders, schedule, violations) {
  const frozenOrders = new Set(
    (orders || [])
      .filter((order) => !!order.frozen)
      .map((order) => order.orderNo)
  );

  for (const allocation of schedule.allocations || []) {
    if (frozenOrders.has(allocation.orderNo)) {
      violations.push({
        code: "FROZEN_ORDER_SCHEDULED",
        orderNo: allocation.orderNo,
        taskKey: allocation.taskKey,
      });
      break;
    }
  }
}

module.exports = {
  validateSchedule,
};
