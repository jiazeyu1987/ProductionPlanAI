import { compareDate, nextBusinessDate } from "./calendar";
import {
  EPSILON,
  PLANNING_MODE,
  clampNumber,
  makeId,
  makeLineOrderKey,
  positiveOr,
  round3,
} from "./engine-core";
import { normalizeLiteScenario } from "./engine-scenario";
import { buildLiteScheduleByDurationManual } from "./schedule-duration";
import { buildLiteScheduleByQuantityCapacity } from "./schedule-quantity";

export function buildLiteSchedule(inputScenario) {
  const scenario = normalizeLiteScenario(inputScenario);
  const schedulingHelpers = {
    EPSILON,
    clampNumber,
    round3,
    positiveOr,
    makeId,
    makeLineOrderKey,
  };
  if (scenario.planningMode === PLANNING_MODE.DURATION_MANUAL_FINISH) {
    return buildLiteScheduleByDurationManual(scenario, schedulingHelpers);
  }
  return buildLiteScheduleByQuantityCapacity(scenario, schedulingHelpers);
}

export function advanceLiteScenarioOneDay(inputScenario) {
  const scenario = normalizeLiteScenario(inputScenario);
  const plan = buildLiteSchedule(scenario);
  const today = scenario.horizonStart;

  if (scenario.planningMode === PLANNING_MODE.DURATION_MANUAL_FINISH) {
    const completedToday = round3(
      plan.allocations
        .filter((item) => item.date === today)
        .reduce((sum, item) => sum + item.workloadDays, 0),
    );
    const newStart = nextBusinessDate(
      today,
      scenario.skipStatutoryHolidays,
      scenario.weekendRestMode,
      scenario.dateWorkModeByDate,
    );
    const nextScenario = normalizeLiteScenario({
      ...scenario,
      horizonStart: newStart,
      simulationLogs: [
        {
          date: today,
          completedWorkload: completedToday,
          note: "按天数模式推进 1 天",
        },
        ...scenario.simulationLogs,
      ].slice(0, 30),
    });
    const nextPlan = buildLiteSchedule(nextScenario);
    return {
      nextScenario,
      daySummary: {
        date: today,
        completedWorkload: completedToday,
        remainingWorkload: nextPlan.summary.totalRemaining,
        delayedOrders: nextPlan.summary.delayedOrders,
        message: `已推进至 ${newStart}，当日完成 ${completedToday} 天工作量。`,
      },
    };
  }

  const completedByOrder = {};
  const consumedByLock = {};
  plan.allocations
    .filter((item) => item.date === today)
    .forEach((item) => {
      completedByOrder[item.orderId] = round3(
        (completedByOrder[item.orderId] || 0) + item.workloadDays,
      );
      if (item.lockId) {
        consumedByLock[item.lockId] = round3(
          (consumedByLock[item.lockId] || 0) + item.workloadDays,
        );
      }
    });

  const newStart = nextBusinessDate(
    today,
    scenario.skipStatutoryHolidays,
    scenario.weekendRestMode,
    scenario.dateWorkModeByDate,
  );
  const nextOrders = scenario.orders.map((order) => {
    const completed = round3(
      order.completedDays + (completedByOrder[order.id] || 0),
    );
    return {
      ...order,
      completedDays: Math.min(order.workloadDays, completed),
    };
  });
  const nextLocks = scenario.locks
    .map((lock) => {
      const consumed = consumedByLock[lock.id] || 0;
      const left = round3(lock.workloadDays - consumed);
      if (left <= EPSILON) {
        return null;
      }
      const startDate =
        compareDate(lock.startDate, newStart) < 0 ? newStart : lock.startDate;
      if (compareDate(startDate, lock.endDate) > 0) {
        return null;
      }
      return {
        ...lock,
        startDate,
        workloadDays: left,
      };
    })
    .filter(Boolean);

  const completedToday = round3(
    Object.values(completedByOrder).reduce((sum, value) => sum + value, 0),
  );
  const nextScenario = normalizeLiteScenario({
    ...scenario,
    horizonStart: newStart,
    orders: nextOrders,
    locks: nextLocks,
    simulationLogs: [
      {
        date: today,
        completedWorkload: completedToday,
        note: "按当前方案推进 1 天",
      },
      ...scenario.simulationLogs,
    ].slice(0, 30),
  });
  const nextPlan = buildLiteSchedule(nextScenario);
  return {
    nextScenario,
    daySummary: {
      date: today,
      completedWorkload: completedToday,
      remainingWorkload: nextPlan.summary.totalRemaining,
      delayedOrders: nextPlan.summary.delayedOrders,
      message: `已推进至 ${newStart}，当日完成 ${completedToday} 天工作量。`,
    },
  };
}

