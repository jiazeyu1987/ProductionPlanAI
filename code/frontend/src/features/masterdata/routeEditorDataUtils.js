import { normalizeDependencyType, normalizeProductCode } from "./codeNormalizeUtils";
import { toNumber } from "./dataTransformUtils";

export function createEmptyRouteStep() {
  return {
    process_code: "",
    dependency_type: "FS",
  };
}

export function buildRouteStepsByProduct(routes) {
  const map = new Map();
  for (const row of routes || []) {
    const productCode = normalizeProductCode(row?.product_code);
    if (!productCode) {
      continue;
    }
    const list = map.get(productCode) || [];
    list.push({
      process_code: String(row?.process_code || "").trim().toUpperCase(),
      dependency_type: normalizeDependencyType(row?.dependency_type),
      sequence_no: toNumber(row?.sequence_no, list.length + 1),
    });
    map.set(productCode, list);
  }
  for (const [productCode, list] of map.entries()) {
    list.sort((a, b) => toNumber(a.sequence_no, 0) - toNumber(b.sequence_no, 0));
    map.set(productCode, list);
  }
  return map;
}
