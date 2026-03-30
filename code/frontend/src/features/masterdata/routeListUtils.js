import {
  normalizeDependencyType,
  normalizeProductCode,
  routeGroupKey,
} from "./codeNormalizeUtils";
import { toNumber } from "./dataTransformUtils";

export function resolveRouteListProductName(row, routeProductNameByCode) {
  const productCode = normalizeProductCode(row?.product_code);
  if (productCode && routeProductNameByCode?.[productCode]) {
    return routeProductNameByCode[productCode];
  }
  const productName = String(row?.product_name_cn || row?.product_name || "").trim();
  if (productName) {
    return productName;
  }
  return productCode || "-";
}

export function resolveRouteListDisplayCode(row, routeProductNameByCode) {
  const productCode = normalizeProductCode(row?.product_code);
  if (productCode && routeProductNameByCode?.[productCode]) {
    return productCode;
  }
  const productName = String(row?.product_name_cn || row?.product_name || "").trim();
  if (productName) {
    return productName;
  }
  return productCode || "-";
}

export function dependencyTypeLabel(value) {
  return normalizeDependencyType(value) === "SS" ? "SS" : "FS";
}

export function isRouteVisibleInList(row, processCodeFilter, productCodeFilter, hiddenRouteProductCodes) {
  const normalizedProductCode = normalizeProductCode(row?.product_code);
  if (hiddenRouteProductCodes?.has(normalizedProductCode)) {
    return false;
  }
  const matchProcess = processCodeFilter
    ? String(row?.process_code || "").toUpperCase() === String(processCodeFilter).toUpperCase()
    : true;
  const matchProduct = productCodeFilter
    ? normalizedProductCode === String(productCodeFilter).toUpperCase()
    : true;
  return matchProcess && matchProduct;
}

export function sortRoutesForList(rows) {
  return [...(rows || [])].sort((a, b) => {
    const byRoute = String(a?.route_no || "").localeCompare(String(b?.route_no || ""), "zh-Hans-CN");
    if (byRoute !== 0) {
      return byRoute;
    }
    const byProduct = String(a?.product_code || "").localeCompare(String(b?.product_code || ""), "zh-Hans-CN");
    if (byProduct !== 0) {
      return byProduct;
    }
    return toNumber(a?.sequence_no, 0) - toNumber(b?.sequence_no, 0);
  });
}

export function buildGroupedRouteRowsForList(filteredRoutes) {
  if (!filteredRoutes || filteredRoutes.length === 0) {
    return [];
  }

  const result = [];
  let index = 0;
  let productGroupIndex = 0;

  while (index < filteredRoutes.length) {
    const start = filteredRoutes[index];
    const groupKey = routeGroupKey(start);
    let span = 1;

    while (index + span < filteredRoutes.length && routeGroupKey(filteredRoutes[index + span]) === groupKey) {
      span += 1;
    }

    for (let offset = 0; offset < span; offset += 1) {
      result.push({
        ...filteredRoutes[index + offset],
        _routeProductRowSpan: offset === 0 ? span : 0,
        _routeProductGroupIndex: productGroupIndex,
      });
    }

    index += span;
    productGroupIndex += 1;
  }

  return result;
}
