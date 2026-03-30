export function routeGroupKey(row) {
  return [
    row.route_no || "",
    row.route_name_cn || "",
    row.product_code || "",
    row.product_name_cn || "",
  ].join("|");
}

export function normalizeShiftCode(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "D") {
    return "DAY";
  }
  if (normalized === "N") {
    return "NIGHT";
  }
  return normalized;
}

export function shiftSortIndex(shiftCode) {
  const normalized = normalizeShiftCode(shiftCode);
  if (normalized === "DAY") {
    return 0;
  }
  if (normalized === "NIGHT") {
    return 1;
  }
  return 9;
}

export function normalizeProcessCode(value) {
  return String(value || "").trim().toUpperCase();
}

export function normalizeCompanyCode(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized || "COMPANY-MAIN";
}

export function normalizeProductCode(value) {
  return String(value || "").trim().toUpperCase();
}

export function normalizeDependencyType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized === "SS" ? "SS" : "FS";
}
