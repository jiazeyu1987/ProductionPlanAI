export function parseIsoAsUtcDate(dateText) {
  const text = String(dateText || "");
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
  return new Date(Date.UTC(year, month - 1, day));
}

export function formatUtcDateToIso(date) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function parseMonthText(monthText) {
  const text = String(monthText || "").trim();
  const match = text.match(/^(\d{4})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (
    !Number.isInteger(year) ||
    !Number.isInteger(month) ||
    month < 1 ||
    month > 12
  ) {
    return null;
  }
  return { year, month };
}

export function formatMonthText(year, month) {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}`;
}

export function getMonthDayCount(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function monthTextFromDate(dateText) {
  const parsedDate = parseIsoAsUtcDate(dateText);
  if (!parsedDate) {
    return null;
  }
  return formatMonthText(
    parsedDate.getUTCFullYear(),
    parsedDate.getUTCMonth() + 1,
  );
}

export function buildCalendarWeeksByMonth(monthText) {
  const parsed = parseMonthText(monthText);
  if (!parsed) {
    return [];
  }
  const { year, month } = parsed;
  const daysInMonth = getMonthDayCount(year, month);
  const monthStart = new Date(Date.UTC(year, month - 1, 1));
  const leadingEmptyCount = (monthStart.getUTCDay() + 6) % 7;
  const cells = [];
  for (let i = 0; i < leadingEmptyCount; i += 1) {
    cells.push(null);
  }
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(formatUtcDateToIso(new Date(Date.UTC(year, month - 1, day))));
  }
  const trailingEmptyCount = (7 - (cells.length % 7)) % 7;
  for (let i = 0; i < trailingEmptyCount; i += 1) {
    cells.push(null);
  }
  const weeks = [];
  for (let idx = 0; idx < cells.length; idx += 7) {
    weeks.push(cells.slice(idx, idx + 7));
  }
  return weeks;
}
