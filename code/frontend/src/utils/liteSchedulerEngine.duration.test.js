import { describe, expect, it } from "vitest";
import {
  advanceLiteScenarioOneDay,
  buildLiteSchedule,
  makeLineOrderKey,
  normalizeLiteScenario,
  PLANNING_MODE,
  WEEKEND_REST_MODE
} from "./liteSchedulerEngine";

describe("liteSchedulerEngine", () => {
  it("duration mode schedules by line plan days and ignores base capacity", () => {
    const scenario = normalizeLiteScenario({
      planningMode: PLANNING_MODE.DURATION_MANUAL_FINISH,
      horizonStart: "2026-03-25",
      horizonDays: 12,
      skipStatutoryHolidays: false,
      weekendRestMode: WEEKEND_REST_MODE.NONE,
      lines: [
        { id: "L1", name: "瀵肩", baseCapacity: 500 },
        { id: "L2", name: "瀵间笣", baseCapacity: 2000 }
      ],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 6, L2: 7 }
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2, L2: 2 }
        }
      ]
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
        { id: "L1", name: "瀵肩", baseCapacity: 300 },
        { id: "L2", name: "瀵间笣", baseCapacity: 300 }
      ],
      orders: [
        {
          id: "O1",
          orderNo: "PO-0001",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 7, L2: 6 }
        }
      ]
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
      lines: [{ id: "L1", name: "瀵肩", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2 }
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 2 }
        }
      ]
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
        [makeLineOrderKey("L1", "O1")]: "2026-03-26"
      }
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
      lines: [{ id: "L1", name: "瀵肩", baseCapacity: 1 }],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 3,
          completedDays: 1,
          releaseDate: "2026-03-25",
          linePlanDays: { L1: 3 }
        }
      ]
    });

    const result = advanceLiteScenarioOneDay(scenario);
    expect(result.nextScenario.horizonStart).toBe("2026-03-28");
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
  });
});

