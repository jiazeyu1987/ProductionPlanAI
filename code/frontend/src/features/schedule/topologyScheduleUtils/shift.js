export function shiftSortIndex(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "DAY" || normalized === "D") {
    return 0;
  }
  if (normalized === "NIGHT" || normalized === "N") {
    return 1;
  }
  return 9;
}

export function normalizeShiftCode(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "D") {
    return "DAY";
  }
  if (normalized === "N") {
    return "NIGHT";
  }
  return normalized || "DAY";
}

