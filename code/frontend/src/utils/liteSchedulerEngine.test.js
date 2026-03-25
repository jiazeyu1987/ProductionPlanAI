import { describe, expect, it } from "vitest";
import {
  advanceLiteScenarioOneDay,
  buildLiteSchedule,
  normalizeLiteScenario
} from "./liteSchedulerEngine";

describe("liteSchedulerEngine", () => {
  it("respects lock segments and line daily capacity", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 3,
      lines: [
        { id: "L1", name: "Line-1", baseCapacity: 1 },
        { id: "L2", name: "Line-2", baseCapacity: 1 }
      ],
      orders: [
        {
          id: "O1",
          orderNo: "ORD-1",
          workloadDays: 3,
          completedDays: 0,
          dueDate: "2026-03-26",
          releaseDate: "2026-03-25",
          priority: "NORMAL"
        },
        {
          id: "O2",
          orderNo: "ORD-2",
          workloadDays: 2,
          completedDays: 0,
          dueDate: "2026-03-27",
          releaseDate: "2026-03-25",
          priority: "NORMAL"
        }
      ],
      locks: [
        {
          id: "LOCK-1",
          orderId: "O2",
          lineId: "L1",
          startDate: "2026-03-25",
          endDate: "2026-03-25",
          workloadDays: 1
        }
      ]
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
          priority: "NORMAL"
        }
      ],
      locks: [
        {
          id: "LOCK-1",
          orderId: "O1",
          lineId: "L1",
          startDate: "2026-03-25",
          endDate: "2026-03-27",
          workloadDays: 3
        }
      ]
    });

    const result = advanceLiteScenarioOneDay(scenario);
    const nextOrder = result.nextScenario.orders.find((row) => row.id === "O1");
    const nextLock = result.nextScenario.locks.find((row) => row.id === "LOCK-1");

    expect(result.nextScenario.horizonStart).toBe("2026-03-26");
    expect(nextOrder?.completedDays).toBeCloseTo(1, 6);
    expect(nextLock?.startDate).toBe("2026-03-26");
    expect(nextLock?.workloadDays).toBeCloseTo(2, 6);
  });

  it("prioritizes earlier due date under constrained capacity", () => {
    const scenario = normalizeLiteScenario({
      horizonStart: "2026-03-25",
      horizonDays: 2,
      lines: [{ id: "L1", name: "Line-1", baseCapacity: 1 }],
      orders: [
        {
          id: "EARLY",
          orderNo: "EARLY",
          workloadDays: 1,
          completedDays: 0,
          dueDate: "2026-03-25",
          releaseDate: "2026-03-25",
          priority: "NORMAL"
        },
        {
          id: "LATE",
          orderNo: "LATE",
          workloadDays: 1,
          completedDays: 0,
          dueDate: "2026-03-26",
          releaseDate: "2026-03-25",
          priority: "NORMAL"
        }
      ],
      locks: []
    });

    const plan = buildLiteSchedule(scenario);
    const dayOneAllocation = plan.allocations.find((item) => item.date === "2026-03-25");
    expect(dayOneAllocation?.orderId).toBe("EARLY");
  });
});
