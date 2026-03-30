import { parseIsoAsUtcDate } from "./calendarDateUtils";
import { normalizeShiftCode } from "./codeNormalizeUtils";
import { isCnStatutoryHoliday, WEEKEND_REST_MODE } from "../../utils/liteSchedulerEngine";

export const DATE_SHIFT_MODE = Object.freeze({
  REST: "REST",
  DAY: "DAY",
  NIGHT: "NIGHT",
  BOTH: "BOTH",
});

export function normalizeWeekendRestMode(mode) {
  const normalized = String(mode || "").trim().toUpperCase();
  if (normalized === WEEKEND_REST_MODE.NONE) {
    return WEEKEND_REST_MODE.NONE;
  }
  if (normalized === WEEKEND_REST_MODE.SINGLE) {
    return WEEKEND_REST_MODE.SINGLE;
  }
  return WEEKEND_REST_MODE.DOUBLE;
}

export function normalizeDateShiftMode(mode) {
  const normalized = String(mode || "").trim().toUpperCase();
  if (normalized === DATE_SHIFT_MODE.REST) {
    return DATE_SHIFT_MODE.REST;
  }
  if (normalized === DATE_SHIFT_MODE.DAY) {
    return DATE_SHIFT_MODE.DAY;
  }
  if (normalized === DATE_SHIFT_MODE.NIGHT) {
    return DATE_SHIFT_MODE.NIGHT;
  }
  if (normalized === DATE_SHIFT_MODE.BOTH) {
    return DATE_SHIFT_MODE.BOTH;
  }
  return "";
}

export function normalizeDateShiftModeByDate(input) {
  const out = {};
  if (!input || typeof input !== "object") {
    return out;
  }
  for (const [dateText, modeText] of Object.entries(input)) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(dateText || "").trim())) {
      continue;
    }
    const mode = normalizeDateShiftMode(modeText);
    if (!mode) {
      continue;
    }
    out[String(dateText).trim()] = mode;
  }
  return out;
}

export function defaultDateShiftMode(dateText, skipStatutoryHolidays, weekendRestMode) {
  if (!dateText) {
    return DATE_SHIFT_MODE.DAY;
  }
  if (!skipStatutoryHolidays) {
    return DATE_SHIFT_MODE.DAY;
  }
  if (isCnStatutoryHoliday(dateText)) {
    return DATE_SHIFT_MODE.REST;
  }
  const parsed = parseIsoAsUtcDate(dateText);
  if (!parsed) {
    return DATE_SHIFT_MODE.DAY;
  }
  const weekday = parsed.getUTCDay();
  if (weekendRestMode === WEEKEND_REST_MODE.NONE) {
    return DATE_SHIFT_MODE.DAY;
  }
  if (weekendRestMode === WEEKEND_REST_MODE.SINGLE) {
    return weekday === 0 ? DATE_SHIFT_MODE.REST : DATE_SHIFT_MODE.DAY;
  }
  return weekday === 0 || weekday === 6 ? DATE_SHIFT_MODE.REST : DATE_SHIFT_MODE.DAY;
}

export function resolveDateShiftMode(dateText, skipStatutoryHolidays, weekendRestMode, dateShiftModeByDate) {
  const manualMode = normalizeDateShiftMode(dateShiftModeByDate?.[dateText]);
  if (manualMode) {
    return manualMode;
  }
  return defaultDateShiftMode(dateText, skipStatutoryHolidays, normalizeWeekendRestMode(weekendRestMode));
}

export function shiftIsOpenInDateMode(shiftCode, mode) {
  const normalizedShift = normalizeShiftCode(shiftCode);
  const normalizedMode = normalizeDateShiftMode(mode);
  if (normalizedMode === DATE_SHIFT_MODE.REST) {
    return false;
  }
  if (normalizedMode === DATE_SHIFT_MODE.NIGHT) {
    return normalizedShift === "NIGHT";
  }
  if (normalizedMode === DATE_SHIFT_MODE.BOTH) {
    return normalizedShift === "DAY" || normalizedShift === "NIGHT";
  }
  return normalizedShift === "DAY";
}

export function buildVisibleShiftCodeSet(mode) {
  const normalizedMode = normalizeDateShiftMode(mode);
  if (normalizedMode === DATE_SHIFT_MODE.REST) {
    return new Set();
  }
  if (normalizedMode === DATE_SHIFT_MODE.NIGHT) {
    return new Set(["NIGHT"]);
  }
  if (normalizedMode === DATE_SHIFT_MODE.BOTH) {
    return new Set(["DAY", "NIGHT"]);
  }
  return new Set(["DAY"]);
}

export function buildDateShiftModeByDateMap(dateShiftModeByDate, dateText, mode) {
  const nextMap = { ...(dateShiftModeByDate || {}) };
  const normalizedMode = normalizeDateShiftMode(mode);
  if (!normalizedMode) {
    delete nextMap[dateText];
  } else {
    nextMap[dateText] = normalizedMode;
  }
  return nextMap;
}

export function resolveDateShiftModeAfterAddNight(currentMode) {
  const normalizedMode = normalizeDateShiftMode(currentMode);
  if (normalizedMode === DATE_SHIFT_MODE.REST) {
    return DATE_SHIFT_MODE.NIGHT;
  }
  if (normalizedMode === DATE_SHIFT_MODE.NIGHT || normalizedMode === DATE_SHIFT_MODE.BOTH) {
    return normalizedMode;
  }
  return DATE_SHIFT_MODE.BOTH;
}
