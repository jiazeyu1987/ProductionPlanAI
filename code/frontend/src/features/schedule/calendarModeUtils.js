import { isCnStatutoryHoliday, WEEKEND_REST_MODE } from "../../utils/liteSchedulerEngine";
import { parseIsoAsUtcDate } from "./calendarDateUtils";

export const DATE_SHIFT_MODE = {
  REST: "REST",
  DAY: "DAY",
  NIGHT: "NIGHT",
  BOTH: "BOTH"
};

export const DATE_SHIFT_MODE_LABEL = {
  [DATE_SHIFT_MODE.REST]: "\u4f11\u606f",
  [DATE_SHIFT_MODE.DAY]: "\u767d\u73ed",
  [DATE_SHIFT_MODE.NIGHT]: "\u591c\u73ed",
  [DATE_SHIFT_MODE.BOTH]: "\u767d\u591c\u73ed"
};

export const WEEKEND_REST_MODE_OPTIONS = [
  { value: WEEKEND_REST_MODE.DOUBLE, label: "\u53cc\u4f11" },
  { value: WEEKEND_REST_MODE.SINGLE, label: "\u5355\u4f11" },
  { value: WEEKEND_REST_MODE.NONE, label: "\u65e0\u4f11" }
];

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

export function normalizeCodeText(value) {
  return String(value || "").trim().toUpperCase();
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
    const date = String(dateText || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      continue;
    }
    const mode = normalizeDateShiftMode(modeText);
    if (!mode) {
      continue;
    }
    out[date] = mode;
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
  const mode = normalizeWeekendRestMode(weekendRestMode);
  if (mode === WEEKEND_REST_MODE.NONE) {
    return DATE_SHIFT_MODE.DAY;
  }
  if (mode === WEEKEND_REST_MODE.SINGLE) {
    return weekday === 0 ? DATE_SHIFT_MODE.REST : DATE_SHIFT_MODE.DAY;
  }
  return weekday === 0 || weekday === 6 ? DATE_SHIFT_MODE.REST : DATE_SHIFT_MODE.DAY;
}

export function resolveDateShiftMode(dateText, skipStatutoryHolidays, weekendRestMode, dateShiftModeByDate) {
  const manualMode = normalizeDateShiftMode(dateShiftModeByDate?.[dateText]);
  if (manualMode) {
    return manualMode;
  }
  return defaultDateShiftMode(dateText, skipStatutoryHolidays, weekendRestMode);
}

export function dayModeButtonClass(modeText) {
  const mode = normalizeDateShiftMode(modeText);
  if (mode === DATE_SHIFT_MODE.BOTH) {
    return "schedule-calendar-mode-btn-both";
  }
  if (mode === DATE_SHIFT_MODE.DAY) {
    return "schedule-calendar-mode-btn-day";
  }
  return "";
}

export function nightModeButtonClass(modeText) {
  const mode = normalizeDateShiftMode(modeText);
  if (mode === DATE_SHIFT_MODE.BOTH) {
    return "schedule-calendar-mode-btn-both";
  }
  if (mode === DATE_SHIFT_MODE.NIGHT) {
    return "schedule-calendar-mode-btn-night";
  }
  return "";
}

export function nextDateShiftMode(currentModeText, clickedModeText) {
  const currentMode = normalizeDateShiftMode(currentModeText);
  const clickedMode = normalizeDateShiftMode(clickedModeText);
  if (!clickedMode) {
    return currentMode || DATE_SHIFT_MODE.DAY;
  }
  if (clickedMode === DATE_SHIFT_MODE.REST) {
    return DATE_SHIFT_MODE.REST;
  }
  if (clickedMode === DATE_SHIFT_MODE.DAY) {
    if (currentMode === DATE_SHIFT_MODE.NIGHT) {
      return DATE_SHIFT_MODE.BOTH;
    }
    if (currentMode === DATE_SHIFT_MODE.BOTH) {
      return DATE_SHIFT_MODE.DAY;
    }
    return DATE_SHIFT_MODE.DAY;
  }
  if (clickedMode === DATE_SHIFT_MODE.NIGHT) {
    if (currentMode === DATE_SHIFT_MODE.DAY) {
      return DATE_SHIFT_MODE.BOTH;
    }
    if (currentMode === DATE_SHIFT_MODE.BOTH) {
      return DATE_SHIFT_MODE.NIGHT;
    }
    return DATE_SHIFT_MODE.NIGHT;
  }
  return DATE_SHIFT_MODE.DAY;
}
