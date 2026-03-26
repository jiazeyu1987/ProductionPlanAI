import { describe, expect, it } from "vitest";
import {
  advanceLiteScenarioOneDay,
  buildDateRange,
  buildLiteSchedule,
  normalizeLiteScenario,
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
