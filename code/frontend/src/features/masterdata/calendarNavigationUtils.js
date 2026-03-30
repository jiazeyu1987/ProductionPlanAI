import {
  formatMonthText,
  monthTextFromDate,
  parseMonthText,
} from "./calendarDateUtils";

export function resolveSelectedCalendarMonth(monthText) {
  const parsed = parseMonthText(monthText);
  if (!parsed) {
    return null;
  }
  return formatMonthText(parsed.year, parsed.month);
}

export function resolveMovedCalendarMonth(step, calendarMonth, selectedConfigDate, horizonStartDate) {
  const fallbackMonth = monthTextFromDate(selectedConfigDate || horizonStartDate);
  const base = parseMonthText(calendarMonth) || parseMonthText(fallbackMonth);
  if (!base) {
    return null;
  }
  const next = new Date(Date.UTC(base.year, base.month - 1 + Number(step || 0), 1));
  return formatMonthText(next.getUTCFullYear(), next.getUTCMonth() + 1);
}

export function resolveShiftedConfigDate(configDates, selectedConfigDate, offset) {
  const dates = Array.isArray(configDates) ? configDates : [];
  if (!selectedConfigDate || dates.length === 0) {
    return null;
  }
  const index = dates.indexOf(selectedConfigDate);
  if (index < 0) {
    return null;
  }
  const nextIndex = Math.max(0, Math.min(dates.length - 1, index + Number(offset || 0)));
  return dates[nextIndex];
}
