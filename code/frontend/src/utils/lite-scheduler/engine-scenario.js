import {
  isoToday,
  normalizeDateWorkModeByDate,
  normalizeWeekendRestMode,
  parseIsoDate,
  WEEKEND_REST_MODE,
} from "./calendar";
import {
  EPSILON,
  PLANNING_MODE,
  clampNumber,
  makeId,
  positiveOr,
} from "./engine-core";
import { normalizeLine } from "./engine-scenario-line";
import { inferNextOrderSeq, normalizeOrder } from "./engine-scenario-order";
import {
  normalizeLock,
  normalizeManualFinishByLineOrder,
  sanitizeLinePlans,
} from "./engine-scenario-lock";

function normalizePlanningMode(value) {
  const modeText = String(value || "").toUpperCase();
  if (modeText === PLANNING_MODE.DURATION_MANUAL_FINISH) {
    return PLANNING_MODE.DURATION_MANUAL_FINISH;
  }
  return PLANNING_MODE.QTY_CAPACITY;
}

export function createDefaultLiteScenario(baseDate = isoToday()) {
  const start = parseIsoDate(baseDate) ? baseDate : isoToday();
  return {
    schemaVersion: 1,
    nextOrderSeq: 1,
    planningMode: PLANNING_MODE.QTY_CAPACITY,
    horizonStart: start,
    horizonDays: 30,
    skipStatutoryHolidays: false,
    weekendRestMode: WEEKEND_REST_MODE.DOUBLE,
    dateWorkModeByDate: {},
    manualFinishByLineOrder: {},
    lines: [
      {
        id: makeId("line"),
        name: "导管产线",
        baseCapacity: 300,
        capacityOverrides: {},
        enabled: true,
      },
    ],
    orders: [],
    locks: [],
    simulationLogs: [],
  };
}

export function normalizeLiteScenario(input) {
  const fallback = createDefaultLiteScenario();
  const planningMode = normalizePlanningMode(input?.planningMode);
  const horizonStart = parseIsoDate(input?.horizonStart)
    ? input.horizonStart
    : fallback.horizonStart;
  const horizonDays = Math.max(1, Math.round(clampNumber(input?.horizonDays, 30)));
  const skipStatutoryHolidays = input?.skipStatutoryHolidays === true;
  const weekendRestMode = normalizeWeekendRestMode(input?.weekendRestMode);
  const dateWorkModeByDate = normalizeDateWorkModeByDate(
    input?.dateWorkModeByDate,
  );

  const lines = Array.isArray(input?.lines)
    ? input.lines
        .map((line, index) => normalizeLine(line, index))
        .filter((line) => line.id)
    : [];
  const safeLines = lines.length > 0 ? lines : fallback.lines;
  const lineIdSet = new Set(safeLines.map((line) => line.id));

  const rawOrders = Array.isArray(input?.orders)
    ? input.orders.map((order, index) =>
        normalizeOrder(order, index, horizonStart),
      )
    : [];
  const orders = rawOrders.map((order) => {
    const { workloadDays, completedDays, lineWorkloads, linePlanDays, linePlanQuantities } = sanitizeLinePlans(order, lineIdSet);
    return {
      ...order,
      workloadDays,
      completedDays,
      lineWorkloads,
      linePlanDays,
      linePlanQuantities,
    };
  });
  const inferredNextOrderSeq = inferNextOrderSeq(orders);
  const nextOrderSeqRaw = Number(input?.nextOrderSeq);
  const nextOrderSeq = Number.isFinite(nextOrderSeqRaw)
    ? Math.max(1, Math.round(nextOrderSeqRaw))
    : inferredNextOrderSeq;

  const orderIdSet = new Set(orders.map((order) => order.id));
  const manualFinishByLineOrder = normalizeManualFinishByLineOrder(
    input?.manualFinishByLineOrder,
    orderIdSet,
    lineIdSet,
  );
  const locks = Array.isArray(input?.locks)
    ? input.locks
        .map((lock, index) => normalizeLock(lock, index, horizonStart))
        .filter(
          (lock) =>
            lock.workloadDays > EPSILON &&
            lock.orderId &&
            lock.lineId &&
            orderIdSet.has(lock.orderId) &&
            lineIdSet.has(lock.lineId),
        )
    : [];

  const simulationLogs = Array.isArray(input?.simulationLogs)
    ? input.simulationLogs
        .map((row) => ({
          date: parseIsoDate(row?.date) ? row.date : horizonStart,
          completedWorkload: positiveOr(row?.completedWorkload, 0),
          note: String(row?.note || "").trim(),
        }))
        .slice(0, 30)
    : [];

  return {
    schemaVersion: 1,
    nextOrderSeq,
    planningMode,
    horizonStart,
    horizonDays,
    skipStatutoryHolidays,
    weekendRestMode,
    dateWorkModeByDate,
    manualFinishByLineOrder,
    lines: safeLines,
    orders,
    locks,
    simulationLogs,
  };
}
