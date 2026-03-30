import { normalizeProductCode } from "./codeNormalizeUtils";

export function buildProductOptions(materialAvailabilityRows, routes) {
  const byCode = new Map();
  for (const row of materialAvailabilityRows || []) {
    const productCode = normalizeProductCode(row?.product_code);
    if (!productCode) {
      continue;
    }
    byCode.set(productCode, row?.product_name_cn || row?.product_name || productCode);
  }
  for (const row of routes || []) {
    const productCode = normalizeProductCode(row?.product_code);
    if (!productCode || byCode.has(productCode)) {
      continue;
    }
    byCode.set(productCode, row?.product_name_cn || row?.product_name || productCode);
  }
  return [...byCode.entries()]
    .map(([code, name]) => ({ code, name: String(name || code).trim() || code }))
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-Hans-CN"));
}
