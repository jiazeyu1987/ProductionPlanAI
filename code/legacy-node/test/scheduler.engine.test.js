const test = require("node:test");
const assert = require("node:assert/strict");

const { generateSchedule } = require("../src/services/schedulerEngine");
const { validateSchedule } = require("../src/services/scheduleValidator");
const { buildMinimalInput, buildDependencyInput } = require("./fixtures/planningInput");

test("material shortage blocks executable quantity", () => {
  const input = buildMinimalInput({
    materialAvailability: [
      {
        date: "2026-03-22",
        shiftCode: "D",
        productCode: "PROD_A",
        processCode: "P1",
        availableQty: 800,
      },
    ],
  });

  const schedule = generateSchedule(input, { versionNo: "V001" });
  const allocated = sumQty(schedule.allocations);

  assert.equal(allocated, 800);
  assert.equal(schedule.unscheduled.length, 1);
  assert.equal(schedule.unscheduled[0].remainingQty, 200);
  assert.ok(schedule.unscheduled[0].reasons.includes("MATERIAL_SHORTAGE"));

  const validation = validateSchedule(input, schedule);
  assert.equal(validation.passed, true);
});

test("machine constraint limits total quantity in one shift", () => {
  const input = buildMinimalInput({
    workerPools: [
      { date: "2026-03-22", shiftCode: "D", processCode: "P1", availableWorkers: 20 },
    ],
    machinePools: [
      { date: "2026-03-22", shiftCode: "D", processCode: "P1", availableMachines: 1 },
    ],
    orders: [
      {
        orderNo: "MO-A",
        dueDate: "2026-03-23",
        urgent: true,
        frozen: false,
        items: [{ productCode: "PROD_A", qty: 1000 }],
      },
      {
        orderNo: "MO-B",
        dueDate: "2026-03-24",
        urgent: false,
        frozen: false,
        items: [{ productCode: "PROD_A", qty: 1000 }],
      },
    ],
    materialAvailability: [
      { date: "2026-03-22", shiftCode: "D", productCode: "PROD_A", processCode: "P1", availableQty: 9999 },
    ],
  });

  const schedule = generateSchedule(input, { versionNo: "V002" });

  const qtyByOrder = new Map();
  for (const row of schedule.allocations) {
    qtyByOrder.set(row.orderNo, (qtyByOrder.get(row.orderNo) || 0) + row.scheduledQty);
  }

  assert.equal(sumQty(schedule.allocations), 1000);
  assert.equal(qtyByOrder.get("MO-A"), 1000);
  assert.equal(qtyByOrder.get("MO-B") || 0, 0);

  const validation = validateSchedule(input, schedule);
  assert.equal(validation.passed, true);
});

test("frozen orders are excluded from scheduling", () => {
  const input = buildMinimalInput({
    orders: [
      {
        orderNo: "MO-FROZEN",
        dueDate: "2026-03-23",
        urgent: true,
        frozen: true,
        items: [{ productCode: "PROD_A", qty: 500 }],
      },
    ],
  });

  const schedule = generateSchedule(input, { versionNo: "V003" });
  assert.equal(schedule.allocations.length, 0);

  const validation = validateSchedule(input, schedule);
  assert.equal(validation.passed, true);
});

test("FS dependency forces second process to wait next shift", () => {
  const input = buildDependencyInput();
  const schedule = generateSchedule(input, { versionNo: "V004" });

  const p1ShiftIds = schedule.allocations.filter((row) => row.processCode === "P1").map((row) => row.shiftId);
  const p2ShiftIds = schedule.allocations.filter((row) => row.processCode === "P2").map((row) => row.shiftId);

  assert.deepEqual(p1ShiftIds, ["2026-03-22#D"]);
  assert.deepEqual(p2ShiftIds, ["2026-03-23#D"]);

  const validation = validateSchedule(input, schedule);
  assert.equal(validation.passed, true);
});

test("same input should produce deterministic schedule", () => {
  const input = buildMinimalInput();
  const a = generateSchedule(input, { versionNo: "V005" });
  const b = generateSchedule(input, { versionNo: "V006" });

  assert.deepEqual(stripMeta(a), stripMeta(b));
});

function sumQty(rows) {
  return rows.reduce((sum, row) => sum + row.scheduledQty, 0);
}

function stripMeta(schedule) {
  return {
    shiftHours: schedule.shiftHours,
    shiftsPerDay: schedule.shiftsPerDay,
    shifts: schedule.shifts,
    tasks: schedule.tasks,
    allocations: schedule.allocations,
    unscheduled: schedule.unscheduled,
    metrics: schedule.metrics,
    metadata: schedule.metadata,
  };
}
