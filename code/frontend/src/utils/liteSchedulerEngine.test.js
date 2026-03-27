import { describe, expect, it } from "vitest";
import {
  advanceLiteScenarioOneDay,
  buildDateRange,
  buildLiteSchedule,
  makeLineOrderKey,
  normalizeLiteScenario,
  PLANNING_MODE,
  WEEKEND_REST_MODE,
} from "./liteSchedulerEngine";

describe("liteSchedulerEngine", () => {
  it("respects lock segments and line daily capacity", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 3,
      lines: [
        { id: "L1", name: "Line-1", baseCapacity: 1 },
        { id: "L2", name: "Line-2", baseCapacity: 1 },
      ],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 3,
          completedDays: 0,
          dueDate: "2026-03-26",
          releaseDate: "2026-03-25",
          priority: "NORMAL",
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-03-27",
          releaseDate: "2026-03-25",
          priority: "NORMAL",
        },
      ],
      locks: [
        {
          id: "LOCK-1",
          orderId: "O2",
          lineId: "L1",
          startDate: "2026-03-25",
          endDate: "2026-03-25",
          workloadDays: 1,
        },
      ],
    });

    const plan = buildLiteSchedule(scenario);
    const locked = plan.allocations.find((item) => item.lockId === "LOCK-1");

    expect(locked).toBeTruthy();
    expect(locked?.orderId).toBe("O2");
    expect(locked?.lineId).toBe("L1");
    expect(locked?.date).toBe("2026-03-25");
    expect(locked?.source).toBe("LOCKED");

    plan.lineRows.forEach((line) => {
      plan.dates.forEach((date) => {
        const cell = line.daily[date];
        expect(cell.assigned).toBeLessThanOrEqual(cell.capacity + 1e-9);
      });
    });
  });

  it("advancing one day updates completed workload and lock residual", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 4,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 5,
          completedDays: 0,
          dueDate: "2026-03-30",
          releaseDate: "2026-03-25",
          priority: "NORMAL",
        },
      ],
      locks: [
        {
          id: "LOCK-1",
          orderId: "O1",
          lineId: "L1",
          startDate: "2026-03-25",
          endDate: "2026-03-27",
          workloadDays: 3,
        },
      ],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");
    const nextLock = result.nextScenario.locks.find(
      (row) => row.id === "LOCK-1",
    );

    expect(result.nextScenario.horizonStart).toBe("2026-03-26");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
    expect(nextLock?.startDate).toBe("2026-03-26");
    expect(nextLock?.workloadDays).toBeCloseTo(2, 6);
  });

  it("prioritizes earlier order sequence under constrained capacity", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 2,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "SECOND",
          orderNo: "PO-0002",
          orderSeq: 2,
          workloadDays: 1,
          completedDays: 0,
          dueDate: "2026-03-25",
          releaseDate: "2026-03-25",
          priority: "URGENT",
        },
        {
          id: "FIRST",
          orderNo: "PO-0001",
          orderSeq: 1,
          workloadDays: 1,
          completedDays: 0,
          dueDate: "2026-03-26",
          releaseDate: "2026-03-25",
          priority: "NORMAL",
        },
      ],
      locks: [],
    });

    const plan = buildLiteSchedule(scenario);
    const dayOneAllocation = plan.allocations.find(
      (item) => item.date === "2026-03-25",
    );
    expect(dayOneAllocation?.orderId).toBe("FIRST");
  });

  it("honors line-specific workload assignments from order input", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 2,
      lines: [
        { id: "L1", name: "Line-1", baseCapacity: 1 },
        { id: "L2", name: "Line-2", baseCapacity: 1 },
      ],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-LINE",
          workloadDays: 1,
          completedDays: 0,
          dueDate: "2026-03-26",
          releaseDate: "2026-03-25",
          priority: "NORMAL",
          lineWorkloads: {
            L2: 1,
          },
        },
      ],
      locks: [],
    });

    const plan = buildLiteSchedule(scenario);
    const dayOne = plan.allocations.find((item) => item.date === "2026-03-25");
    expect(dayOne?.orderId).toBe("O1");
    expect(dayOne?.lineId).toBe("L2");
  });

  it("normalizes product/spec/batch fields for lite orders", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 2,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-META",
          workloadDays: 1,
          completedDays: 0,
          product_name: "Product-X",
          spec_model: "10ml",
          production_batch_no: "BATCH-01",
        },
      ],
      locks: [],
    });

    expect(scenario.orders[0]).toMatchObject({
      productName: "Product-X",
      spec: "10ml",
      batchNo: "BATCH-01",
    });

    const plan = buildLiteSchedule(scenario);
    expect(plan.orderRows[0]).toMatchObject({
      productName: "Product-X",
      spec: "10ml",
      batchNo: "BATCH-01",
    });
  });

  it("duration mode schedules by line plan days and ignores base capacity", () => {
    const scenario = normalizeLiteScenario({
      planningMode: PLANNING_MODE.DURATION_MANUAL_FINISH,
      horizonStart: "2026-03-25",
      horizonDays: 12,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [
        { id: "L1", name: "导管", baseCapacity: 500 },
        { id: "L2", name: "导丝", baseCapacity: 2000 },
      ],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 6, L2: 7 },
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2, L2: 2 },
        },
      ],
    });

    const plan = buildLiteSchedule(scenario);
    const line1 = plan.lineRows.find((row) => row.lineId === "L1");
    const line2 = plan.lineRows.find((row) => row.lineId === "L2");
    expect(line1).toBeTruthy();
    expect(line2).toBeTruthy();

    const line1Seq = plan.dates
      .map((date) => line1.daily[date].items[0]?.orderId || null)
      .filter(Boolean);
    const line2Seq = plan.dates
      .map((date) => line2.daily[date].items[0]?.orderId || null)
      .filter(Boolean);

    expect(line1Seq.slice(0, 6)).toEqual(Array(6).fill("O1"));
    expect(line1Seq[6]).toBe("O2");
    expect(line2Seq.slice(0, 7)).toEqual(Array(7).fill("O1"));
    expect(line2Seq[7]).toBe("O2");
  });

  it("duration mode order planned days uses max across lines", () => {
    const scenario = normalizeLiteScenario({
      planningMode: PLANNING_MODE.DURATION_MANUAL_FINISH,
      horizonStart: "2026-03-25",
      horizonDays: 12,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [
        { id: "L1", name: "导管", baseCapacity: 300 },
        { id: "L2", name: "导丝", baseCapacity: 300 },
      ],
      orders: [
        {
          id: "O1",
          orderNo: "PO-0001",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 7, L2: 6 },
        },
      ],
    });

    const plan = buildLiteSchedule(scenario);
    const order1 = plan.orderRows.find((row) => row.id === "O1");
    expect(order1).toBeTruthy();
    expect(order1?.workloadDays).toBe(7);
  });

  it("duration mode can backfill manual finish and pull subsequent orders earlier", () => {
    const baseScenario = normalizeLiteScenario({
      planningMode: PLANNING_MODE.DURATION_MANUAL_FINISH,
      horizonStart: "2026-03-27",
      horizonDays: 5,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [{ id: "L1", name: "导管", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2 },
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2 },
        },
      ],
    });

    const noFinishPlan = buildLiteSchedule(baseScenario);
    const lineNoFinish = noFinishPlan.lineRows.find((row) => row.lineId === "L1");
    expect(lineNoFinish.daily["2026-03-27"].items[0]?.orderId).toBe("O1");
    expect(lineNoFinish.daily["2026-03-28"].items[0]?.orderId).toBe("O2");
    const noFinishOrder1 = noFinishPlan.orderRows.find((row) => row.id === "O1");
    expect(noFinishOrder1?.finishStatus).toBe("未报结束");

    const withManualFinish = normalizeLiteScenario({
      ...baseScenario,
      manualFinishByLineOrder: {
        [makeLineOrderKey("L1", "O1")]: "2026-03-26",
      },
    });
    const finishedPlan = buildLiteSchedule(withManualFinish);
    const lineFinished = finishedPlan.lineRows.find((row) => row.lineId === "L1");
    expect(lineFinished.daily["2026-03-27"].items[0]?.orderId).toBe("O2");
    const finishedOrder1 = finishedPlan.orderRows.find((row) => row.id === "O1");
    expect(finishedOrder1?.finishStatus).toBe("已结束");
    expect(finishedOrder1?.actualFinishDate).toBe("2026-03-26");
  });

  it("advancing one day in duration mode only moves the planning date", () => {
    const scenario = normalizeLiteScenario({
      planningMode: PLANNING_MODE.DURATION_MANUAL_FINISH,
      horizonStart: "2026-03-27",
      horizonDays: 5,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [{ id: "L1", name: "导管", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 3,
          completedDays: 1,
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 3 },
        },
      ],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    expect(result.nextScenario.horizonStart).toBe("2026-03-28");
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
  });

  it("does not cap horizon days at 120", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 150,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [],
      locks: [],
    });

    const plan = buildLiteSchedule(scenario);
    expect(plan.dates).toHaveLength(150);
    expect(plan.summary.horizonEnd).toBe("2026-08-21");
  });

  it("skips statutory holiday dates in date range when enabled", () => {
    const dates = buildDateRange("2026-10-01", 3, true);
    expect(dates).toEqual(["2026-10-08", "2026-10-09", "2026-10-12"]);
  });

  it("skips weekend dates in date range when enabled", () => {
    const dates = buildDateRange("2026-03-27", 3, true);
    expect(dates).toEqual(["2026-03-27", "2026-03-30", "2026-03-31"]);
  });

  it("keeps weekends when weekend rest mode is NONE", () => {
    const dates = buildDateRange("2026-03-27", 4, true, WEEKEND_REST_MODE.NONE);
    expect(dates).toEqual([
      "2026-03-27",
      "2026-03-28",
      "2026-03-29",
      "2026-03-30",
    ]);
  });

  it("skips only sunday when weekend rest mode is SINGLE", () => {
    const dates = buildDateRange(
      "2026-03-27",
      4,
      true,
      WEEKEND_REST_MODE.SINGLE,
    );
    expect(dates).toEqual([
      "2026-03-27",
      "2026-03-28",
      "2026-03-30",
      "2026-03-31",
    ]);
  });

  it("supports manual work override on rest dates", () => {
    const dates = buildDateRange(
      "2026-03-27",
      4,
      true,
      WEEKEND_REST_MODE.DOUBLE,
      {
        "2026-03-29": "WORK",
      },
    );
    expect(dates).toEqual([
      "2026-03-27",
      "2026-03-29",
      "2026-03-30",
      "2026-03-31",
    ]);
  });

  it("supports manual rest override even when statutory skip is disabled", () => {
    const dates = buildDateRange(
      "2026-03-27",
      4,
      false,
      WEEKEND_REST_MODE.NONE,
      {
        "2026-03-28": "REST",
      },
    );
    expect(dates).toEqual([
      "2026-03-27",
      "2026-03-29",
      "2026-03-30",
      "2026-03-31",
    ]);
  });

  it("advancing one day jumps across statutory holiday spans", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-09-30",
      horizonDays: 7,
      skipStatutoryHolidays: true,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-10-12",
          releaseDate: "2026-09-30",
          priority: "NORMAL",
        },
      ],
      locks: [],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");

    expect(result.nextScenario.horizonStart).toBe("2026-10-08");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
  });

  it("advancing one day jumps across weekend when enabled", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-27",
      horizonDays: 7,
      skipStatutoryHolidays: true,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-04-03",
          releaseDate: "2026-03-27",
          priority: "NORMAL",
        },
      ],
      locks: [],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");

    expect(result.nextScenario.horizonStart).toBe("2026-03-30");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
  });

  it("advancing one day does not skip saturday under NONE mode", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-27",
      horizonDays: 7,
      skipStatutoryHolidays: true,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-04-03",
          releaseDate: "2026-03-27",
          priority: "NORMAL",
        },
      ],
      locks: [],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    expect(result.nextScenario.horizonStart).toBe("2026-03-28");
  });

  it("advancing one day skips sunday under SINGLE mode", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-28",
      horizonDays: 7,
      skipStatutoryHolidays: true,
      weekendRestMode: WEEKEND_REST_MODE.SINGLE,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-04-03",
          releaseDate: "2026-03-28",
          priority: "NORMAL",
        },
      ],
      locks: [],
    });

    const result = advanceLiteScenarioOneDay(scenario);
    expect(result.nextScenario.horizonStart).toBe("2026-03-30");
  });

  it("advancing one day respects manual work and manual rest overrides", () => {
    const manualWorkScenario = normalizeLiteScenario({
      horizonStart: "2026-03-28",
      horizonDays: 7,
      skipStatutoryHolidays: true,
      weekendRestMode: WEEKEND_REST_MODE.DOUBLE,
      dateWorkModeByDate: {
        "2026-03-29": "WORK",
      },
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [],
      locks: [],
    });
    const manualWorkResult = advanceLiteScenarioOneDay(manualWorkScenario);
    expect(manualWorkResult.nextScenario.horizonStart).toBe("2026-03-29");

    const manualRestScenario = normalizeLiteScenario({
      horizonStart: "2026-03-27",
      horizonDays: 7,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      dateWorkModeByDate: {
        "2026-03-28": "REST",
      },
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [],
      locks: [],
    });
    const manualRestResult = advanceLiteScenarioOneDay(manualRestScenario);
    expect(manualRestResult.nextScenario.horizonStart).toBe("2026-03-29");
  });
});
