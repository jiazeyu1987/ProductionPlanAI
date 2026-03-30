import {
  addDays,
  compareDate,
  isSkippedPlanningDate,
  nextBusinessDate,
  parseIsoDate,
} from "./calendar";

function clampNumber(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return fallback;
  }
  return n;
}

export function alignToBusinessDate(dateText, scenario) {
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

export function moveBusinessDays(startDate, offset, scenario) {
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

export function countBusinessDaysInclusive(startDate, endDate, scenario) {
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
