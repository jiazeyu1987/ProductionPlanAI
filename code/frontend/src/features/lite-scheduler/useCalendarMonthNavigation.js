export function useCalendarMonthNavigation({
  calendarMonth,
  setCalendarMonth,
  scenarioHorizonStart,
  parseMonthText,
  formatMonthText,
  monthTextFromDate,
  addDays,
  isoToday,
}) {
  function selectCalendarMonth(monthText) {
    const parsed = parseMonthText(monthText);
    if (!parsed) {
      return;
    }
    const { year, month } = parsed;
    setCalendarMonth(formatMonthText(year, month));
  }

  function moveCalendarMonth(step) {
    const fallbackMonth =
      monthTextFromDate(scenarioHorizonStart) ||
      monthTextFromDate(addDays(isoToday(), 0));
    const base = parseMonthText(calendarMonth) || parseMonthText(fallbackMonth);
    if (!base) {
      return;
    }
    const next = new Date(
      Date.UTC(base.year, base.month - 1 + Number(step || 0), 1),
    );
    selectCalendarMonth(
      formatMonthText(next.getUTCFullYear(), next.getUTCMonth() + 1),
    );
  }

  return {
    selectCalendarMonth,
    moveCalendarMonth,
  };
}
