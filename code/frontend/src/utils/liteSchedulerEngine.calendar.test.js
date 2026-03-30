import { describe, expect, it } from "vitest";
import {
  advanceLiteScenarioOneDay,
  buildDateRange,
  normalizeLiteScenario,
  WEEKEND_REST_MODE
} from "./liteSchedulerEngine";

describe("liteSchedulerEngine", () => {
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
    expect(dates).toEqual(["2026-03-27", "2026-03-28", "2026-03-29", "2026-03-30"]);
  });

  it("skips only sunday when weekend rest mode is SINGLE", () => {
    const dates = buildDateRange("2026-03-27", 4, true, WEEKEND_REST_MODE.SINGLE);
    expect(dates).toEqual(["2026-03-27", "2026-03-28", "2026-03-30", "2026-03-31"]);
  });

  it("supports manual work override on rest dates", () => {
    const dates = buildDateRange("2026-03-27", 4, true, WEEKEND_REST_MODE.DOUBLE, {
      "2026-03-29": "WORK"
    });
    expect(dates).toEqual(["2026-03-27", "2026-03-29", "2026-03-30", "2026-03-31"]);
  });

  it("supports manual rest override even when statutory skip is disabled", () => {
    const dates = buildDateRange("2026-03-27", 4, false, WEEKEND_REST_MODE.NONE, {
      "2026-03-28": "REST"
    });
    expect(dates).toEqual(["2026-03-27", "2026-03-29", "2026-03-30", "2026-03-31"]);
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
          priority: "NORMAL"
        }
      ],
      locks: []
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
          priority: "NORMAL"
        }
      ],
      locks: []
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
          priority: "NORMAL"
        }
      ],
      locks: []
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
          priority: "NORMAL"
        }
      ],
      locks: []
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
        "2026-03-29": "WORK"
      },
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [],
      locks: []
    });
    const manualWorkResult = advanceLiteScenarioOneDay(manualWorkScenario);
    expect(manualWorkResult.nextScenario.horizonStart).toBe("2026-03-29");

    const manualRestScenario = normalizeLiteScenario({
      horizonStart: "2026-03-27",
      horizonDays: 7,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      dateWorkModeByDate: {
        "2026-03-28": "REST"
      },
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [],
      locks: []
    });
    const manualRestResult = advanceLiteScenarioOneDay(manualRestScenario);
    expect(manualRestResult.nextScenario.horizonStart).toBe("2026-03-29");
  });
});

