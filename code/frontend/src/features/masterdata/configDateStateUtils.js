import { buildHorizonDates, monthTextFromDate } from "./calendarDateUtils";

function unique(values) {
  return [...new Set((values || []).filter(Boolean))];
}

export function buildConfigDates(horizonStartDate, horizonDays, resourcePoolRows, materialAvailabilityRows) {
  const horizonDates = buildHorizonDates(horizonStartDate, horizonDays);
  const dates = unique([
    ...horizonDates,
    ...(resourcePoolRows || []).map((row) => String(row?.calendar_date || "").trim()),
    ...(materialAvailabilityRows || []).map((row) => String(row?.calendar_date || "").trim()),
  ]).sort();
  if (dates.length > 0) {
    return dates;
  }
  return horizonStartDate ? [horizonStartDate] : [];
}

export function resolveSelectedConfigDateUpdate(configDates, selectedConfigDate) {
  const dates = Array.isArray(configDates) ? configDates : [];
  if (dates.length === 0) {
    return selectedConfigDate ? "" : null;
  }
  if (!selectedConfigDate || !dates.includes(selectedConfigDate)) {
    return dates[0];
  }
  return null;
}

export function resolveCalendarMonthInitUpdate(calendarMonth, selectedConfigDate, horizonStartDate) {
  const monthText = monthTextFromDate(selectedConfigDate || horizonStartDate);
  if (!monthText || calendarMonth) {
    return null;
  }
  return monthText;
}
