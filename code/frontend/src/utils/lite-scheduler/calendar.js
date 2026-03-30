const DAY_MS = 24 * 60 * 60 * 1000;

export const WEEKEND_REST_MODE = Object.freeze({
  NONE: "NONE",
  SINGLE: "SINGLE",
  DOUBLE: "DOUBLE",
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

export function parseIsoDate(value) {
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

export function normalizeWeekendRestMode(value) {
  const modeText = String(value || "").toUpperCase();
  if (modeText === WEEKEND_REST_MODE.NONE) {
    return WEEKEND_REST_MODE.NONE;
  }
  if (modeText === WEEKEND_REST_MODE.SINGLE) {
    return WEEKEND_REST_MODE.SINGLE;
  }
  return WEEKEND_REST_MODE.DOUBLE;
}

export function normalizeDateWorkModeByDate(inputValue) {
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

export function isSkippedPlanningDate(
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

export function nextBusinessDate(
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
  const safeDateWorkModeByDate = normalizeDateWorkModeByDate(dateWorkModeByDate);
  const parsedStart = parseIsoDate(startDate);
  const hasDateOverride = Object.keys(safeDateWorkModeByDate).length > 0;
  if (!parsedStart) {
    return Array.from({ length: safeDays }, (_, idx) => addDays(startDate, idx));
  }
  if (!skipStatutoryHolidays && !hasDateOverride) {
    return Array.from({ length: safeDays }, (_, idx) => addDays(startDate, idx));
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
