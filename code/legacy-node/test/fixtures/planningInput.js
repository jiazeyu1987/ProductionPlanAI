function buildMinimalInput(overrides = {}) {
  const base = {
    startDate: "2026-03-22",
    horizonDays: 1,
    shiftsPerDay: 1,
    shiftHours: 12,
    processes: [
      {
        processCode: "P1",
        capacityPerShift: 1000,
        requiredWorkers: 2,
        requiredMachines: 1,
      },
    ],
    processRoutes: {
      PROD_A: [{ processCode: "P1", dependencyType: "FS" }],
    },
    shiftCalendar: [{ date: "2026-03-22", shiftCode: "D", open: true }],
    workerPools: [{ date: "2026-03-22", shiftCode: "D", processCode: "P1", availableWorkers: 2 }],
    machinePools: [{ date: "2026-03-22", shiftCode: "D", processCode: "P1", availableMachines: 1 }],
    materialAvailability: [
      {
        date: "2026-03-22",
        shiftCode: "D",
        productCode: "PROD_A",
        processCode: "P1",
        availableQty: 1000,
      },
    ],
    orders: [
      {
        orderNo: "MO-001",
        dueDate: "2026-03-23",
        urgent: false,
        frozen: false,
        items: [{ productCode: "PROD_A", qty: 1000 }],
      },
    ],
  };

  return deepMerge(base, overrides);
}

function buildDependencyInput() {
  return buildMinimalInput({
    horizonDays: 2,
    processRoutes: {
      PROD_A: [
        { processCode: "P1", dependencyType: "FS" },
        { processCode: "P2", dependencyType: "FS" },
      ],
    },
    processes: [
      { processCode: "P1", capacityPerShift: 500, requiredWorkers: 1, requiredMachines: 1 },
      { processCode: "P2", capacityPerShift: 500, requiredWorkers: 1, requiredMachines: 1 },
    ],
    workerPools: [
      { date: "2026-03-22", shiftCode: "D", processCode: "P1", availableWorkers: 2 },
      { date: "2026-03-22", shiftCode: "D", processCode: "P2", availableWorkers: 2 },
      { date: "2026-03-23", shiftCode: "D", processCode: "P1", availableWorkers: 2 },
      { date: "2026-03-23", shiftCode: "D", processCode: "P2", availableWorkers: 2 },
    ],
    machinePools: [
      { date: "2026-03-22", shiftCode: "D", processCode: "P1", availableMachines: 1 },
      { date: "2026-03-22", shiftCode: "D", processCode: "P2", availableMachines: 1 },
      { date: "2026-03-23", shiftCode: "D", processCode: "P1", availableMachines: 1 },
      { date: "2026-03-23", shiftCode: "D", processCode: "P2", availableMachines: 1 },
    ],
    materialAvailability: [
      { date: "2026-03-22", shiftCode: "D", productCode: "PROD_A", processCode: "P1", availableQty: 500 },
      { date: "2026-03-22", shiftCode: "D", productCode: "PROD_A", processCode: "P2", availableQty: 500 },
      { date: "2026-03-23", shiftCode: "D", productCode: "PROD_A", processCode: "P1", availableQty: 500 },
      { date: "2026-03-23", shiftCode: "D", productCode: "PROD_A", processCode: "P2", availableQty: 500 },
    ],
    shiftCalendar: [
      { date: "2026-03-22", shiftCode: "D", open: true },
      { date: "2026-03-23", shiftCode: "D", open: true },
    ],
    orders: [
      {
        orderNo: "MO-DEP-001",
        dueDate: "2026-03-24",
        urgent: true,
        frozen: false,
        items: [{ productCode: "PROD_A", qty: 500 }],
      },
    ],
  });
}

function deepMerge(base, patch) {
  if (!patch || typeof patch !== "object") {
    return base;
  }

  if (Array.isArray(base) || Array.isArray(patch)) {
    return Array.isArray(patch) ? patch : base;
  }

  const merged = { ...base };
  for (const [key, value] of Object.entries(patch)) {
    if (value && typeof value === "object" && !Array.isArray(value) && merged[key] && typeof merged[key] === "object") {
      merged[key] = deepMerge(merged[key], value);
    } else {
      merged[key] = value;
    }
  }
  return merged;
}

module.exports = {
  buildMinimalInput,
  buildDependencyInput,
};
