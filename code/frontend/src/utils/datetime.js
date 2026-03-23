const DATE_TIME_FIELD_PATTERN = /(date|time|_at|deadline|due)/i;

function pad2(value) {
  return String(value).padStart(2, "0");
}

function formatParts(year, month, day, hour = 0, minute = 0, second = 0) {
  return `${year}-${pad2(month)}-${pad2(day)} ${pad2(hour)}-${pad2(minute)}-${pad2(second)}`;
}

function parseDateTimeString(value) {
  const text = String(value).trim();

  let match = text.match(
    /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2})(?::?(\d{2}))?(?::?(\d{2}))?)?(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$/
  );
  if (match) {
    return formatParts(
      Number(match[1]),
      Number(match[2]),
      Number(match[3]),
      Number(match[4] ?? 0),
      Number(match[5] ?? 0),
      Number(match[6] ?? 0)
    );
  }

  match = text.match(/^(\d{4})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/);
  if (match) {
    return formatParts(
      Number(match[1]),
      Number(match[2]),
      Number(match[3]),
      Number(match[4] ?? 0),
      Number(match[5] ?? 0),
      Number(match[6] ?? 0)
    );
  }

  return null;
}

export function formatDateTime(value) {
  if (value === null || value === undefined || value === "") {
    return value;
  }

  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return formatParts(
      value.getFullYear(),
      value.getMonth() + 1,
      value.getDate(),
      value.getHours(),
      value.getMinutes(),
      value.getSeconds()
    );
  }

  if (typeof value === "number" && Number.isFinite(value)) {
    const normalized = value > 1e12 ? value : value > 1e9 ? value * 1000 : NaN;
    if (!Number.isNaN(normalized)) {
      const date = new Date(normalized);
      if (!Number.isNaN(date.getTime())) {
        return formatParts(
          date.getFullYear(),
          date.getMonth() + 1,
          date.getDate(),
          date.getHours(),
          date.getMinutes(),
          date.getSeconds()
        );
      }
    }
    return value;
  }

  if (typeof value === "string") {
    return parseDateTimeString(value) ?? value;
  }

  return value;
}

export function shouldFormatDateTimeField(field) {
  return typeof field === "string" && DATE_TIME_FIELD_PATTERN.test(field);
}

export function formatDateTimeByField(value, field) {
  if (!shouldFormatDateTimeField(field)) {
    return value;
  }
  return formatDateTime(value);
}
