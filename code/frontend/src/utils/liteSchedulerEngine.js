const DAY_MS = 24 * 60 * 60 * 1000;
const EPSILON = 1e-6;
export const WEEKEND_REST_MODE = Object.freeze({
  NONE: "NONE",
  SINGLE: "SINGLE",
  DOUBLE: "DOUBLE",
});
export const PLANNING_MODE = Object.freeze({
  QTY_CAPACITY: "QTY_CAPACITY",
  DURATION_MANUAL_FINISH: "DURATION_MANUAL_FINISH",
});
const DATE_WORK_MODE = Object.freeze({
  REST: "REST",
  WORK: "WORK",
});
const CN_STATUTORY_HOLIDAY_DATES_BY_YEAR = Object.freeze({
  2024: Object.freeze([
    "2024-01-01",
    "2024-02-10",
    "2024-02-11",
    "2024-02-12",
    "2024-02-13",
    "2024-02-14",
    "2024-02-15",
    "2024-02-16",
    "2024-02-17",
    "2024-04-04",
    "2024-04-05",
    "2024-04-06",
    "2024-05-01",
    "2024-05-02",
    "2024-05-03",
    "2024-05-04",
    "2024-05-05",
    "2024-06-08",
    "2024-06-09",
    "2024-06-10",
    "2024-09-15",
    "2024-09-16",
    "2024-09-17",
    "2024-10-01",
    "2024-10-02",
    "2024-10-03",
    "2024-10-04",
    "2024-10-05",
    "2024-10-06",
    "2024-10-07",
  ]),
  2025: Object.freeze([
    "2025-01-01",
    "2025-01-28",
    "2025-01-29",
    "2025-01-30",
    "2025-01-31",
    "2025-02-01",
    "2025-02-02",
    "2025-02-03",
    "2025-02-04",
    "2025-04-04",
    "2025-04-05",
    "2025-04-06",
    "2025-05-01",
    "2025-05-02",
    "2025-05-03",
    "2025-05-04",
    "2025-05-05",
    "2025-05-31",
    "2025-06-01",
    "2025-06-02",
    "2025-10-01",
    "2025-10-02",
    "2025-10-03",
    "2025-10-04",
    "2025-10-05",
    "2025-10-06",
    "2025-10-07",
    "2025-10-08",
  ]),
  2026: Object.freeze([
    "2026-01-01",
    "2026-01-02",
    "2026-01-03",
    "2026-02-15",
    "2026-02-16",
    "2026-02-17",
    "2026-02-18",
    "2026-02-19",
    "2026-02-20",
    "2026-02-21",
    "2026-02-22",
    "2026-02-23",
    "2026-04-04",
    "2026-04-05",
    "2026-04-06",
    "2026-05-01",
    "2026-05-02",
    "2026-05-03",
    "2026-05-04",
    "2026-05-05",
    "2026-06-19",
    "2026-06-20",
    "2026-06-21",
    "2026-09-25",
    "2026-09-26",
    "2026-09-27",
    "2026-10-01",
    "2026-10-02",
    "2026-10-03",
    "2026-10-04",
    "2026-10-05",
    "2026-10-06",
    "2026-10-07",
  ]),
  2027: Object.freeze([]),
  2028: Object.freeze([]),
});
const CN_STATUTORY_HOLIDAY_DATE_SET = new Set(
  Object.values(CN_STATUTORY_HOLIDAY_DATES_BY_YEAR).flat(),
);

function pad2(value) {
  return String(value).padStart(2, "0");
}

function toIsoDateFromUtc(date) {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
}

function parseIsoDate(value) {
  const text = String(value || "").trim();
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (
    !Number.isInteger(year) ||
    !Number.isInteger(month) ||
    !Number.isInteger(day)
  ) {
    return null;
  }
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }
  const ts = Date.UTC(year, month - 1, day);
  const date = new Date(ts);
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() + 1 !== month ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return { year, month, day, ts };
}

function dayNumber(dateText) {
  const parsed = parseIsoDate(dateText);
  return parsed ? Math.round(parsed.ts / DAY_MS) : null;
}

function clampNumber(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return fallback;
  }
  return n;
}

function round3(value) {
  return Math.round(value * 1000) / 1000;
}

function positiveOr(value, fallback = 0) {
  return Math.max(0, round3(clampNumber(value, fallback)));
}

function integerInRange(value, fallback, min, max) {
  const n = Math.round(clampNumber(value, fallback));
  return Math.min(max, Math.max(min, n));
}

function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function inferNextOrderSeq(orders) {
  let maxSeq = 0;
  (orders || []).forEach((order) => {
    const seq = Number(order?.orderSeq);
    if (Number.isFinite(seq) && seq > maxSeq) {
      maxSeq = seq;
    }
  });
  return Math.max(1, maxSeq + 1);
}

function normalizePriority(value) {
  if (String(value || "").toUpperCase() === "URGENT") {
    return "URGENT";
  }
  return "NORMAL";
}

function normalizeWeekendRestMode(value) {
  const modeText = String(value || "").toUpperCase();
  if (modeText === WEEKEND_REST_MODE.NONE) {
    return WEEKEND_REST_MODE.NONE;
  }
  if (modeText === WEEKEND_REST_MODE.SINGLE) {
    return WEEKEND_REST_MODE.SINGLE;
  }
  return WEEKEND_REST_MODE.DOUBLE;
}

function normalizePlanningMode(value) {
  const modeText = String(value || "").toUpperCase();
  if (modeText === PLANNING_MODE.DURATION_MANUAL_FINISH) {
    return PLANNING_MODE.DURATION_MANUAL_FINISH;
  }
  return PLANNING_MODE.QTY_CAPACITY;
}

export function makeLineOrderKey(lineId, orderId) {
  return `${String(lineId || "").trim()}|${String(orderId || "").trim()}`;
}

function parseLineOrderKey(text) {
  const parts = String(text || "").split("|");
  if (parts.length !== 2) {
    return null;
  }
  const lineId = String(parts[0] || "").trim();
  const orderId = String(parts[1] || "").trim();
  if (!lineId || !orderId) {
    return null;
  }
  return { lineId, orderId };
}

function normalizeDateWorkModeByDate(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([dateKey, modeRaw]) => {
    if (!parseIsoDate(dateKey)) {
      return;
    }
    const mode = String(modeRaw || "").toUpperCase();
    if (mode === DATE_WORK_MODE.REST || mode === DATE_WORK_MODE.WORK) {
      normalized[dateKey] = mode;
    }
  });
  return normalized;
}

function extractTrailingNumber(text) {
  const match = String(text || "").match(/(\d+)(?!.*\d)/);
  if (!match) {
    return null;
  }
  const n = Number(match[1]);
  return Number.isFinite(n) ? n : null;
}

function normalizeLine(line, index) {
  const id = String(line?.id || makeId("line"));
  const name = String(line?.name || "").trim() || `产线-${index + 1}`;
  const baseCapacity = positiveOr(line?.baseCapacity, 300);
  const enabled = line?.enabled !== false;
  const capacityOverrides = {};
  Object.entries(line?.capacityOverrides || {}).forEach(
    ([dateKey, capacityValue]) => {
      if (parseIsoDate(dateKey)) {
        capacityOverrides[dateKey] = positiveOr(capacityValue, baseCapacity);
      }
    },
  );
  return { id, name, baseCapacity, capacityOverrides, enabled };
}

function normalizeOrderLineWorkloads(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([lineIdRaw, daysRaw]) => {
    const lineId = String(lineIdRaw || "").trim();
    const days = positiveOr(daysRaw, 0);
    if (lineId && days > EPSILON) {
      normalized[lineId] = round3((normalized[lineId] || 0) + days);
    }
  });
  return normalized;
}

function normalizeOrderLinePlanDays(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([lineIdRaw, daysRaw]) => {
    const lineId = String(lineIdRaw || "").trim();
    const days = Math.max(0, Math.round(clampNumber(daysRaw, 0)));
    if (lineId && days > 0) {
      normalized[lineId] = days;
    }
  });
  return normalized;
}

function normalizeOrder(order, index, horizonStart) {
  const id = String(order?.id || makeId("order"));
  const orderNo =
    String(order?.orderNo || "").trim() || `SO-${pad2(index + 1)}`;
  const productName = String(
    order?.productName ?? order?.product_name ?? order?.product_name_cn ?? "",
  ).trim();
  const spec = String(order?.spec ?? order?.specModel ?? order?.spec_model ?? "")
    .trim();
  const batchNo = String(
    order?.batchNo ?? order?.batch_no ?? order?.production_batch_no ?? "",
  ).trim();
  const inferredSeq = extractTrailingNumber(orderNo) ?? index + 1;
  const orderSeqRaw = Number(order?.orderSeq);
  const orderSeq = Number.isFinite(orderSeqRaw)
    ? Math.max(1, Math.round(orderSeqRaw))
    : Math.max(1, inferredSeq);
  const lineWorkloads = normalizeOrderLineWorkloads(order?.lineWorkloads);
  const linePlanDays = normalizeOrderLinePlanDays(
    order?.linePlanDays ?? order?.line_plan_days,
  );
  const lineWorkloadTotal = round3(
    Object.values(lineWorkloads).reduce((sum, value) => sum + value, 0),
  );
  const linePlanDayMax = round3(
    Object.values(linePlanDays).reduce(
      (maxValue, value) => Math.max(maxValue, value),
      0,
    ),
  );
  const baseWorkloadDays = positiveOr(
    order?.workloadDays,
    lineWorkloadTotal > EPSILON
      ? lineWorkloadTotal
      : linePlanDayMax > EPSILON
        ? linePlanDayMax
        : 1,
  );
  const workloadDays = Math.max(
    baseWorkloadDays,
    lineWorkloadTotal,
    linePlanDayMax,
  );
  const completedDays = Math.min(
    workloadDays,
    positiveOr(order?.completedDays, 0),
  );
  const dueDate = parseIsoDate(order?.dueDate)
    ? order.dueDate
    : addDays(horizonStart, 7);
  const releaseDate = parseIsoDate(order?.releaseDate)
    ? order.releaseDate
    : horizonStart;
  return {
    id,
    orderNo,
    productName,
    spec,
    batchNo,
    workloadDays,
    completedDays,
    dueDate,
    releaseDate,
    priority: "NORMAL",
    orderSeq,
    lineWorkloads,
    linePlanDays,
  };
}

function normalizeLock(lock, index, fallbackStart) {
  const id = String(lock?.id || makeId("lock"));
  const orderId = String(lock?.orderId || "").trim();
  const lineId = String(lock?.lineId || "").trim();
  const startDateRaw = parseIsoDate(lock?.startDate)
    ? lock.startDate
    : fallbackStart;
  const endDateRaw = parseIsoDate(lock?.endDate) ? lock.endDate : startDateRaw;
  const startDate =
    compareDate(startDateRaw, endDateRaw) <= 0 ? startDateRaw : endDateRaw;
  const endDate =
    compareDate(startDateRaw, endDateRaw) <= 0 ? endDateRaw : startDateRaw;
  const workloadDays = positiveOr(lock?.workloadDays, 0);
  return {
    id,
    orderId,
    lineId,
    startDate,
    endDate,
    workloadDays,
    seq: Number(lock?.seq ?? index),
  };
}

function resolveLineCapacity(line, date) {
  if (Object.prototype.hasOwnProperty.call(line.capacityOverrides, date)) {
    return positiveOr(line.capacityOverrides[date], line.baseCapacity);
  }
  return positiveOr(line.baseCapacity, 0);
}

function orderSortForReplan(a, b, remainingByOrder) {
  const seqCmp = (Number(a.orderSeq) || 0) - (Number(b.orderSeq) || 0);
  if (seqCmp !== 0) {
    return seqCmp;
  }
  return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
}

function makeAllocation(orderId, lineId, date, workloadDays, source, lockId) {
  return {
    id: makeId("alloc"),
    orderId,
    lineId,
    date,
    workloadDays: round3(workloadDays),
    source,
    lockId: lockId || null,
  };
}

export function isoToday() {
  return toIsoDateFromUtc(new Date());
}

export function addDays(dateText, offset) {
  const parsed = parseIsoDate(dateText);
  if (!parsed) {
    return dateText;
  }
  return toIsoDateFromUtc(new Date(parsed.ts + Number(offset || 0) * DAY_MS));
}

export function compareDate(a, b) {
  if (a === b) {
    return 0;
  }
  const aDay = dayNumber(a);
  const bDay = dayNumber(b);
  if (aDay === null && bDay === null) {
    return String(a || "").localeCompare(String(b || ""));
  }
  if (aDay === null) {
    return -1;
  }
  if (bDay === null) {
    return 1;
  }
  return aDay - bDay;
}

export function diffDays(laterDate, earlierDate) {
  const a = dayNumber(laterDate);
  const b = dayNumber(earlierDate);
  if (a === null || b === null) {
    return 0;
  }
  return a - b;
}

export function supportedCnHolidayYears() {
  return Object.keys(CN_STATUTORY_HOLIDAY_DATES_BY_YEAR)
    .map((year) => Number(year))
    .sort((a, b) => a - b);
}

export function isCnStatutoryHoliday(dateText) {
  const parsed = parseIsoDate(dateText);
  if (!parsed) {
    return false;
  }
  const normalized = `${parsed.year}-${pad2(parsed.month)}-${pad2(parsed.day)}`;
  return CN_STATUTORY_HOLIDAY_DATE_SET.has(normalized);
}

function isWeekendDate(dateText) {
  const parsed = parseIsoDate(dateText);
  if (!parsed) {
    return false;
  }
  const weekday = new Date(parsed.ts).getUTCDay();
  return weekday === 0 || weekday === 6;
}

function isSingleRestDay(dateText) {
  const parsed = parseIsoDate(dateText);
  if (!parsed) {
    return false;
  }
  return new Date(parsed.ts).getUTCDay() === 0;
}

function isSkippedPlanningDate(
  dateText,
  skipStatutoryHolidays,
  weekendRestMode = WEEKEND_REST_MODE.DOUBLE,
  dateWorkModeByDate = {},
) {
  const dayMode = dateWorkModeByDate?.[dateText];
  if (dayMode === DATE_WORK_MODE.WORK) {
    return false;
  }
  if (dayMode === DATE_WORK_MODE.REST) {
    return true;
  }

  if (isCnStatutoryHoliday(dateText)) {
    return skipStatutoryHolidays;
  }
  if (!skipStatutoryHolidays) {
    return false;
  }
  if (weekendRestMode === WEEKEND_REST_MODE.NONE) {
    return false;
  }
  if (weekendRestMode === WEEKEND_REST_MODE.SINGLE) {
    return isSingleRestDay(dateText);
  }
  return isWeekendDate(dateText);
}

function nextBusinessDate(
  dateText,
  skipStatutoryHolidays,
  weekendRestMode = WEEKEND_REST_MODE.DOUBLE,
  dateWorkModeByDate = {},
) {
  let nextDate = addDays(dateText, 1);

  let guard = 0;
  while (
    isSkippedPlanningDate(
      nextDate,
      skipStatutoryHolidays,
      weekendRestMode,
      dateWorkModeByDate,
    ) &&
    guard < 370
  ) {
    const candidate = addDays(nextDate, 1);
    if (candidate === nextDate) {
      break;
    }
    nextDate = candidate;
    guard += 1;
  }
  return nextDate;
}

export function buildDateRange(
  startDate,
  horizonDays,
  skipStatutoryHolidays = false,
  weekendRestMode = WEEKEND_REST_MODE.DOUBLE,
  dateWorkModeByDate = {},
) {
  const safeDays = Math.max(1, Math.round(clampNumber(horizonDays, 30)));
  const safeWeekendMode = normalizeWeekendRestMode(weekendRestMode);
  const safeDateWorkModeByDate =
    normalizeDateWorkModeByDate(dateWorkModeByDate);
  const parsedStart = parseIsoDate(startDate);
  const hasDateOverride = Object.keys(safeDateWorkModeByDate).length > 0;
  if (!parsedStart) {
    return Array.from({ length: safeDays }, (_, idx) =>
      addDays(startDate, idx),
    );
  }
  if (!skipStatutoryHolidays && !hasDateOverride) {
    return Array.from({ length: safeDays }, (_, idx) =>
      addDays(startDate, idx),
    );
  }

  const dates = [];
  let cursor = `${parsedStart.year}-${pad2(parsedStart.month)}-${pad2(parsedStart.day)}`;
  let guard = 0;
  while (dates.length < safeDays && guard < safeDays * 400) {
    if (
      !isSkippedPlanningDate(
        cursor,
        skipStatutoryHolidays,
        safeWeekendMode,
        safeDateWorkModeByDate,
      )
    ) {
      dates.push(cursor);
    }
    const next = addDays(cursor, 1);
    if (next === cursor) {
      break;
    }
    cursor = next;
    guard += 1;
  }

  if (dates.length >= safeDays) {
    return dates;
  }
  return Array.from({ length: safeDays }, (_, idx) => addDays(startDate, idx));
}

function normalizeManualFinishByLineOrder(inputValue, orderIdSet, lineIdSet) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([keyRaw, dateRaw]) => {
    const parsedKey = parseLineOrderKey(keyRaw);
    if (!parsedKey) {
      return;
    }
    if (!lineIdSet.has(parsedKey.lineId) || !orderIdSet.has(parsedKey.orderId)) {
      return;
    }
    if (!parseIsoDate(dateRaw)) {
      return;
    }
    normalized[makeLineOrderKey(parsedKey.lineId, parsedKey.orderId)] = dateRaw;
  });
  return normalized;
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
  const horizonDays = Math.max(
    1,
    Math.round(clampNumber(input?.horizonDays, 30)),
  );
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
    const nextLineWorkloads = {};
    Object.entries(order.lineWorkloads || {}).forEach(([lineId, value]) => {
      if (lineIdSet.has(lineId) && value > EPSILON) {
        nextLineWorkloads[lineId] = value;
      }
    });
    const nextLinePlanDays = {};
    Object.entries(order.linePlanDays || {}).forEach(([lineId, value]) => {
      const days = Math.max(0, Math.round(clampNumber(value, 0)));
      if (lineIdSet.has(lineId) && days > 0) {
        nextLinePlanDays[lineId] = days;
      }
    });
    const lineTotal = round3(
      Object.values(nextLineWorkloads).reduce((sum, value) => sum + value, 0),
    );
    const linePlanTotal = round3(
      Object.values(nextLinePlanDays).reduce((sum, value) => sum + value, 0),
    );
    const workloadDays = Math.max(order.workloadDays, lineTotal, linePlanTotal);
    return {
      ...order,
      workloadDays,
      completedDays: Math.min(workloadDays, order.completedDays),
      lineWorkloads: nextLineWorkloads,
      linePlanDays: nextLinePlanDays,
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

function alignToBusinessDate(dateText, scenario) {
  let cursor = parseIsoDate(dateText) ? dateText : scenario.horizonStart;
  let guard = 0;
  while (
    isSkippedPlanningDate(
      cursor,
      scenario.skipStatutoryHolidays,
      scenario.weekendRestMode,
      scenario.dateWorkModeByDate,
    ) &&
    guard < 400
  ) {
    const next = addDays(cursor, 1);
    if (next === cursor) {
      break;
    }
    cursor = next;
    guard += 1;
  }
  return cursor;
}

function moveBusinessDays(startDate, offset, scenario) {
  const steps = Math.max(0, Math.round(clampNumber(offset, 0)));
  let cursor = startDate;
  for (let idx = 0; idx < steps; idx += 1) {
    cursor = nextBusinessDate(
      cursor,
      scenario.skipStatutoryHolidays,
      scenario.weekendRestMode,
      scenario.dateWorkModeByDate,
    );
  }
  return cursor;
}

function countBusinessDaysInclusive(startDate, endDate, scenario) {
  if (compareDate(endDate, startDate) < 0) {
    return 0;
  }
  let days = 1;
  let cursor = startDate;
  let guard = 0;
  while (compareDate(cursor, endDate) < 0 && guard < 5000) {
    const next = nextBusinessDate(
      cursor,
      scenario.skipStatutoryHolidays,
      scenario.weekendRestMode,
      scenario.dateWorkModeByDate,
    );
    if (next === cursor) {
      break;
    }
    cursor = next;
    if (compareDate(cursor, endDate) <= 0) {
      days += 1;
    }
    guard += 1;
  }
  return days;
}

function buildLiteScheduleByDurationManual(scenario) {
  const dates = buildDateRange(
    scenario.horizonStart,
    scenario.horizonDays,
    scenario.skipStatutoryHolidays,
    scenario.weekendRestMode,
    scenario.dateWorkModeByDate,
  );
  const lines = scenario.lines.filter((line) => line.enabled !== false);
  const orders = scenario.orders
    .slice()
    .sort((a, b) => orderSortForReplan(a, b, {}));
  const warnings = [];
  const allocations = [];
  const orderSegments = {};
  const todayAnchor = alignToBusinessDate(scenario.horizonStart, scenario);

  if (scenario.locks.length > 0) {
    warnings.push("按天数模式下已忽略手动锁定片段。");
  }

  lines.forEach((line) => {
    let cursor = null;
    orders.forEach((order) => {
      const plannedDays = Math.max(
        0,
        Math.round(clampNumber(order.linePlanDays?.[line.id], 0)),
      );
      if (plannedDays <= 0) {
        return;
      }
      const releaseStart = alignToBusinessDate(order.releaseDate, scenario);
      if (!cursor) {
        cursor = releaseStart;
      }
      const startDate =
        compareDate(cursor, releaseStart) >= 0 ? cursor : releaseStart;
      const plannedEndDate = moveBusinessDays(startDate, plannedDays - 1, scenario);
      const key = makeLineOrderKey(line.id, order.id);
      const rawManualFinish = scenario.manualFinishByLineOrder?.[key];
      const manualFinishDate = parseIsoDate(rawManualFinish)
        ? rawManualFinish
        : null;

      let actualEndDate = plannedEndDate;
      let finishSource = "PLANNED";
      if (manualFinishDate) {
        actualEndDate =
          compareDate(manualFinishDate, startDate) < 0
            ? startDate
            : manualFinishDate;
        finishSource = "MANUAL";
      } else if (compareDate(todayAnchor, plannedEndDate) > 0) {
        actualEndDate = todayAnchor;
        finishSource = "EXTENDED";
      }

      const segment = {
        lineId: line.id,
        lineName: line.name,
        orderId: order.id,
        startDate,
        plannedEndDate,
        actualEndDate,
        plannedDays,
        manualFinishDate,
        finishSource,
      };
      if (!orderSegments[order.id]) {
        orderSegments[order.id] = [];
      }
      orderSegments[order.id].push(segment);

      dates.forEach((date) => {
        if (compareDate(date, startDate) < 0 || compareDate(date, actualEndDate) > 0) {
          return;
        }
        allocations.push({
          id: makeId("alloc"),
          orderId: order.id,
          lineId: line.id,
          date,
          workloadDays: 1,
          source: "DURATION",
          lockId: null,
          segmentStartDate: startDate,
          plannedEndDate,
          actualEndDate,
          finishSource,
          manualFinishDate,
        });
      });

      cursor = nextBusinessDate(
        actualEndDate,
        scenario.skipStatutoryHolidays,
        scenario.weekendRestMode,
        scenario.dateWorkModeByDate,
      );
    });
  });

  orders.forEach((order) => {
    const plannedCount = Object.values(order.linePlanDays || {}).reduce(
      (sum, value) => sum + Math.max(0, Math.round(clampNumber(value, 0))),
      0,
    );
    if (plannedCount <= 0) {
      warnings.push(`订单 ${order.orderNo} 未设置产线计划天数。`);
    }
  });

  const allocationMap = {};
  const orderAllocByDate = {};
  allocations.forEach((item) => {
    const key = `${item.lineId}|${item.date}`;
    if (!allocationMap[key]) {
      allocationMap[key] = [];
    }
    allocationMap[key].push(item);

    const dateMap = (orderAllocByDate[item.orderId] =
      orderAllocByDate[item.orderId] || {});
    dateMap[item.date] = round3((dateMap[item.date] || 0) + item.workloadDays);
  });

  const lineRows = lines.map((line) => {
    let assignedTotal = 0;
    let capacityTotal = 0;
    const daily = {};
    dates.forEach((date) => {
      const items = allocationMap[`${line.id}|${date}`] || [];
      const assigned = round3(
        items.reduce((sum, item) => sum + item.workloadDays, 0),
      );
      const cap = 1;
      assignedTotal = round3(assignedTotal + assigned);
      capacityTotal = round3(capacityTotal + cap);
      daily[date] = {
        capacity: cap,
        assigned,
        utilization: cap > EPSILON ? assigned / cap : 0,
        items,
      };
    });
    return {
      lineId: line.id,
      lineName: line.name,
      assignedTotal,
      capacityTotal,
      utilization: capacityTotal > EPSILON ? assignedTotal / capacityTotal : 0,
      daily,
    };
  });

  const orderRows = orders.map((order) => {
    const segments = orderSegments[order.id] || [];
    const plannedDays = round3(
      Object.values(order.linePlanDays || {}).reduce(
        (maxValue, value) =>
          Math.max(maxValue, Math.max(0, Math.round(clampNumber(value, 0)))),
        0,
      ),
    );
    const dateMap = orderAllocByDate[order.id] || {};
    const scheduledDays = round3(
      Object.values(dateMap).reduce((sum, value) => sum + value, 0),
    );

    let completedDays = 0;
    let completionDate = null;
    let hasExtendedSegment = false;
    let manualFinishedCount = 0;
    segments.forEach((segment) => {
      const endForCompleted =
        compareDate(segment.actualEndDate, todayAnchor) <= 0
          ? segment.actualEndDate
          : todayAnchor;
      const segmentCompletedDays = countBusinessDaysInclusive(
        segment.startDate,
        endForCompleted,
        scenario,
      );
      completedDays = round3(Math.max(completedDays, segmentCompletedDays));
      if (segment.finishSource === "EXTENDED") {
        hasExtendedSegment = true;
      } else if (
        !completionDate ||
        compareDate(segment.actualEndDate, completionDate) > 0
      ) {
        completionDate = segment.actualEndDate;
      }
      if (segment.manualFinishDate) {
        manualFinishedCount += 1;
      }
    });
    if (hasExtendedSegment) {
      completionDate = null;
    }

    const safeCompleted = Math.min(plannedDays, completedDays);
    const remainingDays = hasExtendedSegment
      ? round3(Math.max(1, plannedDays - safeCompleted))
      : round3(Math.max(0, plannedDays - safeCompleted));
    const allManualFinished =
      segments.length > 0 && manualFinishedCount === segments.length;
    const hasManualFinished = manualFinishedCount > 0;
    const finishStatus =
      segments.length === 0
        ? "未配置"
        : allManualFinished
          ? "已结束"
          : hasManualFinished
            ? "部分报结束"
            : "未报结束";
    const actualFinishDate = allManualFinished ? completionDate : null;
    const reasonParts = [];
    if (segments.length === 0) {
      reasonParts.push("未配置按天数产线计划");
    } else if (hasExtendedSegment) {
      reasonParts.push("存在未报结束产线，后续订单已顺延");
    } else {
      reasonParts.push("按计划天数排产，可手动报结束");
    }

    return {
      ...order,
      lineWorkloads: order.linePlanDays || {},
      workloadDays: plannedDays,
      completedDays: safeCompleted,
      scheduledDays,
      autoScheduledDays: scheduledDays,
      lockedScheduledDays: 0,
      remainingDays,
      completionDate,
      actualFinishDate,
      finishStatus,
      delayDays: completionDate
        ? Math.max(0, diffDays(completionDate, order.dueDate))
        : hasExtendedSegment
          ? Math.max(0, diffDays(todayAnchor, order.dueDate))
          : null,
      reason: `${reasonParts.join("，")}。`,
    };
  });

  const totalCapacity = round3(
    lineRows.reduce((sum, line) => sum + line.capacityTotal, 0),
  );
  const totalAssigned = round3(
    lineRows.reduce((sum, line) => sum + line.assignedTotal, 0),
  );
  const delayedOrders = orderRows.filter(
    (row) => Number(row.delayDays) > 0,
  ).length;
  const totalRemaining = round3(
    orderRows.reduce((sum, row) => sum + Math.max(0, row.remainingDays || 0), 0),
  );
  const bottleneck = lineRows
    .slice()
    .sort((a, b) => b.utilization - a.utilization)[0];

  return {
    scenario,
    dates,
    allocations,
    warnings,
    lineRows,
    orderRows,
    summary: {
      horizonStart: scenario.horizonStart,
      horizonEnd: dates[dates.length - 1],
      totalOrders: orders.length,
      totalCapacity,
      totalAssigned,
      utilization: totalCapacity > EPSILON ? totalAssigned / totalCapacity : 0,
      delayedOrders,
      totalRemaining,
      bottleneckLineId: bottleneck?.lineId || null,
      bottleneckLineName: bottleneck?.lineName || null,
    },
  };
}

export function buildLiteSchedule(inputScenario) {
  const scenario = normalizeLiteScenario(inputScenario);
  if (scenario.planningMode === PLANNING_MODE.DURATION_MANUAL_FINISH) {
    return buildLiteScheduleByDurationManual(scenario);
  }
  const dates = buildDateRange(
    scenario.horizonStart,
    scenario.horizonDays,
    scenario.skipStatutoryHolidays,
    scenario.weekendRestMode,
    scenario.dateWorkModeByDate,
  );
  const lines = scenario.lines.filter((line) => line.enabled !== false);
  const orders = scenario.orders;
  const lineSet = new Set(lines.map((line) => line.id));

  const warnings = [];
  const allocations = [];
  const lineCapByDate = {};
  const lineRemainingCap = {};
  const orderRemaining = {};
  const orderLineRemaining = {};

  lines.forEach((line) => {
    lineCapByDate[line.id] = {};
    lineRemainingCap[line.id] = {};
    dates.forEach((date) => {
      const cap = resolveLineCapacity(line, date);
      lineCapByDate[line.id][date] = cap;
      lineRemainingCap[line.id][date] = cap;
    });
  });

  orders.forEach((order) => {
    const remaining = Math.max(
      0,
      round3(order.workloadDays - order.completedDays),
    );
    orderRemaining[order.id] = remaining;

    const requested = {};
    Object.entries(order.lineWorkloads || {}).forEach(([lineId, qty]) => {
      if (!lineSet.has(lineId)) {
        warnings.push(
          `订单 ${order.orderNo} 指定的产线 ${lineId} 不存在，已忽略。`,
        );
        return;
      }
      if (qty > EPSILON) {
        requested[lineId] = qty;
      }
    });

    const requestedTotal = round3(
      Object.values(requested).reduce((sum, value) => sum + value, 0),
    );
    const ratio =
      requestedTotal > EPSILON && remaining > EPSILON
        ? Math.min(1, remaining / requestedTotal)
        : 0;

    const normalized = {};
    Object.entries(requested).forEach(([lineId, qty]) => {
      const value = round3(qty * ratio);
      if (value > EPSILON) {
        normalized[lineId] = value;
      }
    });
    orderLineRemaining[order.id] = normalized;
  });

  function reduceLineRequirement(orderId, lineId, qty) {
    const safeQty = positiveOr(qty, 0);
    if (safeQty <= EPSILON) {
      return;
    }
    const map = orderLineRemaining[orderId];
    if (!map || !Object.prototype.hasOwnProperty.call(map, lineId)) {
      return;
    }
    map[lineId] = round3((map[lineId] || 0) - safeQty);
    if (map[lineId] <= EPSILON) {
      delete map[lineId];
    }
  }

  function allocateOne(orderId, lineId, date, wanted, source, lockId = null) {
    const remainOrder = orderRemaining[orderId] ?? 0;
    const remainCap = lineRemainingCap[lineId]?.[date] ?? 0;
    const qty = Math.min(positiveOr(wanted, 0), remainOrder, remainCap);
    if (qty <= EPSILON) {
      return 0;
    }
    const safeQty = round3(qty);
    orderRemaining[orderId] = round3((orderRemaining[orderId] || 0) - safeQty);
    lineRemainingCap[lineId][date] = round3(
      (lineRemainingCap[lineId][date] || 0) - safeQty,
    );
    allocations.push(
      makeAllocation(orderId, lineId, date, safeQty, source, lockId),
    );
    return safeQty;
  }

  const sortedLocks = scenario.locks.slice().sort((a, b) => {
    const startCmp = compareDate(a.startDate, b.startDate);
    if (startCmp !== 0) {
      return startCmp;
    }
    return (a.seq || 0) - (b.seq || 0);
  });

  sortedLocks.forEach((lock) => {
    if (!Object.prototype.hasOwnProperty.call(orderRemaining, lock.orderId)) {
      warnings.push(`锁定片段 ${lock.id} 对应订单不存在，已跳过。`);
      return;
    }
    if (!lineRemainingCap[lock.lineId]) {
      warnings.push(`锁定片段 ${lock.id} 对应产线不存在，已跳过。`);
      return;
    }

    const lockDates = dates.filter(
      (date) =>
        compareDate(date, lock.startDate) >= 0 &&
        compareDate(date, lock.endDate) <= 0,
    );
    if (lockDates.length === 0) {
      warnings.push(`锁定片段 ${lock.id} 不在当前周期内，已跳过。`);
      return;
    }

    let left = Math.min(lock.workloadDays, orderRemaining[lock.orderId] || 0);
    lockDates.forEach((date) => {
      if (left <= EPSILON) {
        return;
      }
      const done = allocateOne(
        lock.orderId,
        lock.lineId,
        date,
        left,
        "LOCKED",
        lock.id,
      );
      reduceLineRequirement(lock.orderId, lock.lineId, done);
      left = round3(left - done);
    });

    // 用户在 lite 模式下不需要“未落地锁定片段”提醒，这里静默处理剩余量。
  });

  dates.forEach((date) => {
    const activeLineIds = lines
      .map((line) => line.id)
      .filter((lineId) => (lineRemainingCap[lineId]?.[date] || 0) > EPSILON);
    if (activeLineIds.length === 0) {
      return;
    }

    const candidates = orders
      .filter((order) => {
        const remains = orderRemaining[order.id] || 0;
        return remains > EPSILON && compareDate(date, order.releaseDate) >= 0;
      })
      .sort((a, b) => orderSortForReplan(a, b, orderRemaining));

    if (candidates.length === 0) {
      return;
    }

    candidates.forEach((order) => {
      const lineReq = orderLineRemaining[order.id] || {};
      Object.entries(lineReq)
        .filter(([, qty]) => qty > EPSILON)
        .sort((a, b) => b[1] - a[1])
        .forEach(([lineId, qty]) => {
          if (!lineRemainingCap[lineId]) {
            return;
          }
          const done = allocateOne(order.id, lineId, date, qty, "AUTO_LINE");
          reduceLineRequirement(order.id, lineId, done);
        });
    });

    candidates.forEach((order) => {
      let left = orderRemaining[order.id] || 0;
      if (left <= EPSILON) {
        return;
      }
      const dynamicLines = activeLineIds.slice().sort((a, b) => {
        return (
          (lineRemainingCap[b]?.[date] || 0) -
          (lineRemainingCap[a]?.[date] || 0)
        );
      });
      dynamicLines.forEach((lineId) => {
        if (left <= EPSILON) {
          return;
        }
        const done = allocateOne(order.id, lineId, date, left, "AUTO");
        left = round3(left - done);
      });
    });
  });

  const allocationMap = {};
  const orderAllocByDate = {};
  const orderAutoTotals = {};
  const orderLockedTotals = {};

  allocations.forEach((item) => {
    const lineDateKey = `${item.lineId}|${item.date}`;
    if (!allocationMap[lineDateKey]) {
      allocationMap[lineDateKey] = [];
    }
    allocationMap[lineDateKey].push(item);

    const dateMap = (orderAllocByDate[item.orderId] =
      orderAllocByDate[item.orderId] || {});
    dateMap[item.date] = round3((dateMap[item.date] || 0) + item.workloadDays);

    if (item.source === "LOCKED") {
      orderLockedTotals[item.orderId] = round3(
        (orderLockedTotals[item.orderId] || 0) + item.workloadDays,
      );
    } else {
      orderAutoTotals[item.orderId] = round3(
        (orderAutoTotals[item.orderId] || 0) + item.workloadDays,
      );
    }
  });

  Object.values(allocationMap).forEach((items) => {
    items.sort((a, b) => {
      const orderA = orders.find((row) => row.id === a.orderId);
      const orderB = orders.find((row) => row.id === b.orderId);
      if (orderA && orderB) {
        return orderSortForReplan(orderA, orderB, orderRemaining);
      }
      return String(a.orderId).localeCompare(String(b.orderId));
    });
  });

  const nominalDailyCapacity =
    dates.reduce((sum, date) => {
      return (
        sum +
        lines.reduce((lineSum, line) => {
          return lineSum + (lineCapByDate[line.id]?.[date] || 0);
        }, 0)
      );
    }, 0) / Math.max(dates.length, 1);

  const orderRows = orders.map((order) => {
    const remaining = orderRemaining[order.id] || 0;
    const dateMap = orderAllocByDate[order.id] || {};

    let completed = order.completedDays;
    let completionDate =
      completed >= order.workloadDays
        ? addDays(scenario.horizonStart, -1)
        : null;

    dates.forEach((date) => {
      if (completionDate) {
        return;
      }
      completed = round3(completed + (dateMap[date] || 0));
      if (completed + EPSILON >= order.workloadDays) {
        completionDate = date;
      }
    });

    if (
      !completionDate &&
      remaining > EPSILON &&
      nominalDailyCapacity > EPSILON
    ) {
      completionDate = addDays(
        dates[dates.length - 1],
        Math.ceil(remaining / nominalDailyCapacity),
      );
    }

    const hasLinePreference =
      Object.values(order.lineWorkloads || {}).reduce(
        (sum, value) => sum + value,
        0,
      ) > EPSILON;
    const reasonParts = [];
    if (hasLinePreference) {
      reasonParts.push("先满足指定产线工作量");
    }
    if ((orderLockedTotals[order.id] || 0) > EPSILON) {
      reasonParts.push("手动锁定优先");
    }
    reasonParts.push("其余按订单顺序向后排产");

    return {
      ...order,
      scheduledDays: round3(
        order.workloadDays - remaining - order.completedDays,
      ),
      autoScheduledDays: round3(orderAutoTotals[order.id] || 0),
      lockedScheduledDays: round3(orderLockedTotals[order.id] || 0),
      remainingDays: round3(remaining),
      completionDate,
      delayDays: completionDate
        ? Math.max(0, diffDays(completionDate, order.dueDate))
        : null,
      reason: `${reasonParts.join("，")}。`,
    };
  });

  const lineRows = lines.map((line) => {
    let assignedTotal = 0;
    let capacityTotal = 0;
    const daily = {};
    dates.forEach((date) => {
      const cap = lineCapByDate[line.id]?.[date] || 0;
      const items = allocationMap[`${line.id}|${date}`] || [];
      const assigned = round3(
        items.reduce((sum, item) => sum + item.workloadDays, 0),
      );
      assignedTotal = round3(assignedTotal + assigned);
      capacityTotal = round3(capacityTotal + cap);
      daily[date] = {
        capacity: cap,
        assigned,
        utilization: cap > EPSILON ? assigned / cap : 0,
        items,
      };
    });
    return {
      lineId: line.id,
      lineName: line.name,
      assignedTotal,
      capacityTotal,
      utilization: capacityTotal > EPSILON ? assignedTotal / capacityTotal : 0,
      daily,
    };
  });

  const totalCapacity = round3(
    lines.reduce((sum, line) => {
      return (
        sum +
        dates.reduce((lineSum, date) => {
          return lineSum + (lineCapByDate[line.id]?.[date] || 0);
        }, 0)
      );
    }, 0),
  );
  const totalAssigned = round3(
    allocations.reduce((sum, item) => sum + item.workloadDays, 0),
  );
  const delayedOrders = orderRows.filter(
    (row) => Number(row.delayDays) > 0,
  ).length;
  const totalRemaining = round3(
    Object.values(orderRemaining).reduce((sum, value) => sum + value, 0),
  );
  const bottleneck = lineRows
    .slice()
    .sort((a, b) => b.utilization - a.utilization)[0];

  return {
    scenario,
    dates,
    allocations,
    warnings,
    lineRows,
    orderRows,
    summary: {
      horizonStart: scenario.horizonStart,
      horizonEnd: dates[dates.length - 1],
      totalOrders: orders.length,
      totalCapacity,
      totalAssigned,
      utilization: totalCapacity > EPSILON ? totalAssigned / totalCapacity : 0,
      delayedOrders,
      totalRemaining,
      bottleneckLineId: bottleneck?.lineId || null,
      bottleneckLineName: bottleneck?.lineName || null,
    },
  };
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
