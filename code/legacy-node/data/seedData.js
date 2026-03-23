function buildSeedData() {
  const startDate = "2026-03-22";

  return {
    startDate,
    horizonDays: 4,
    shiftsPerDay: 2,
    shiftHours: 12,
    strictRoute: true,
    processes: [
      { processCode: "PROC_TUBE", capacityPerShift: 1000, requiredWorkers: 2, requiredMachines: 1 },
      { processCode: "PROC_ASSEMBLY", capacityPerShift: 900, requiredWorkers: 2, requiredMachines: 1 },
      { processCode: "PROC_BALLOON", capacityPerShift: 850, requiredWorkers: 2, requiredMachines: 1 },
      { processCode: "PROC_STENT", capacityPerShift: 700, requiredWorkers: 3, requiredMachines: 1 },
      { processCode: "PROC_STERILE", capacityPerShift: 1200, requiredWorkers: 1, requiredMachines: 1 },
    ],
    processRoutes: {
      PROD_CATH: [
        { processCode: "PROC_TUBE", dependencyType: "FS" },
        { processCode: "PROC_ASSEMBLY", dependencyType: "FS" },
        { processCode: "PROC_STERILE", dependencyType: "FS" },
      ],
      PROD_BALLOON: [
        { processCode: "PROC_BALLOON", dependencyType: "FS" },
        { processCode: "PROC_ASSEMBLY", dependencyType: "FS" },
        { processCode: "PROC_STERILE", dependencyType: "FS" },
      ],
      PROD_STENT: [
        { processCode: "PROC_STENT", dependencyType: "FS" },
        { processCode: "PROC_STERILE", dependencyType: "FS" },
      ],
    },
    shiftCalendar: buildShiftCalendar(startDate, 4, 2),
    workerPools: buildWorkerPools(startDate, 4, 2),
    machinePools: buildMachinePools(startDate, 4, 2),
    materialAvailability: buildMaterialAvailability(startDate, 4, 2),
    orders: [
      {
        orderNo: "MO-CATH-001",
        orderType: "production",
        dueDate: "2026-03-24",
        urgent: true,
        frozen: false,
        lockFlag: false,
        status: "OPEN",
        items: [{ productCode: "PROD_CATH", qty: 300 }],
      },
      {
        orderNo: "MO-BALLOON-001",
        orderType: "production",
        dueDate: "2026-03-25",
        urgent: false,
        frozen: false,
        lockFlag: false,
        status: "OPEN",
        items: [{ productCode: "PROD_BALLOON", qty: 1000 }],
      },
      {
        orderNo: "MO-STENT-001",
        orderType: "production",
        dueDate: "2026-03-26",
        urgent: false,
        frozen: false,
        lockFlag: false,
        status: "OPEN",
        items: [{ productCode: "PROD_STENT", qty: 2000 }],
      },
    ],
  };
}

function buildShiftCalendar(startDate, horizonDays, shiftsPerDay) {
  const rows = [];
  const shiftCodes = ["D", "N"];
  for (let i = 0; i < horizonDays; i += 1) {
    const date = addDays(startDate, i);
    for (let j = 0; j < shiftsPerDay; j += 1) {
      rows.push({
        date,
        shiftCode: shiftCodes[j],
        open: true,
      });
    }
  }
  return rows;
}

function buildWorkerPools(startDate, horizonDays, shiftsPerDay) {
  const rows = [];
  const shiftCodes = ["D", "N"];
  const counts = {
    PROC_TUBE: 8,
    PROC_ASSEMBLY: 10,
    PROC_BALLOON: 8,
    PROC_STENT: 9,
    PROC_STERILE: 6,
  };
  for (let i = 0; i < horizonDays; i += 1) {
    const date = addDays(startDate, i);
    for (let j = 0; j < shiftsPerDay; j += 1) {
      for (const [processCode, availableWorkers] of Object.entries(counts)) {
        rows.push({ date, shiftCode: shiftCodes[j], processCode, availableWorkers });
      }
    }
  }
  return rows;
}

function buildMachinePools(startDate, horizonDays, shiftsPerDay) {
  const rows = [];
  const shiftCodes = ["D", "N"];
  const counts = {
    PROC_TUBE: 3,
    PROC_ASSEMBLY: 3,
    PROC_BALLOON: 2,
    PROC_STENT: 2,
    PROC_STERILE: 2,
  };
  for (let i = 0; i < horizonDays; i += 1) {
    const date = addDays(startDate, i);
    for (let j = 0; j < shiftsPerDay; j += 1) {
      for (const [processCode, availableMachines] of Object.entries(counts)) {
        rows.push({ date, shiftCode: shiftCodes[j], processCode, availableMachines });
      }
    }
  }
  return rows;
}

function buildMaterialAvailability(startDate, horizonDays, shiftsPerDay) {
  const rows = [];
  const shiftCodes = ["D", "N"];
  const productByProcess = {
    PROD_CATH: ["PROC_TUBE", "PROC_ASSEMBLY", "PROC_STERILE"],
    PROD_BALLOON: ["PROC_BALLOON", "PROC_ASSEMBLY", "PROC_STERILE"],
    PROD_STENT: ["PROC_STENT", "PROC_STERILE"],
  };

  for (let i = 0; i < horizonDays; i += 1) {
    const date = addDays(startDate, i);
    for (let j = 0; j < shiftsPerDay; j += 1) {
      for (const [productCode, processCodes] of Object.entries(productByProcess)) {
        for (const processCode of processCodes) {
          rows.push({
            date,
            shiftCode: shiftCodes[j],
            productCode,
            processCode,
            availableQty: 5000,
          });
        }
      }
    }
  }

  return rows;
}

function addDays(dateString, offsetDays) {
  const [year, month, day] = dateString.split("-").map((item) => Number(item));
  const date = new Date(Date.UTC(year, month - 1, day + offsetDays));
  return date.toISOString().slice(0, 10);
}

module.exports = {
  buildSeedData,
};
