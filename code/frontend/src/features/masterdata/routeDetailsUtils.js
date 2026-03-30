import { toNumber } from "./dataTransformUtils";

function unique(values) {
  return [...new Set((values || []).filter(Boolean))];
}

export function buildProcessDetails(routes, processCodeFilter) {
  if (!processCodeFilter) {
    return null;
  }
  const rows = (routes || []).filter(
    (row) => String(row?.process_code || "").toUpperCase() === String(processCodeFilter).toUpperCase(),
  );
  if (rows.length === 0) {
    return null;
  }
  return {
    processCode: rows[0].process_code,
    processName: rows[0].process_name_cn || rows[0].process_code || "-",
    routeCount: rows.length,
    products: unique(rows.map((row) => row.product_name_cn || row.product_code)).join(", ") || "-",
  };
}

export function buildProductDetails(routes, productCodeFilter) {
  if (!productCodeFilter) {
    return null;
  }
  const rows = (routes || []).filter(
    (row) => String(row?.product_code || "").toUpperCase() === String(productCodeFilter).toUpperCase(),
  );
  if (rows.length === 0) {
    return null;
  }
  const orderedRows = [...rows].sort((a, b) => toNumber(a?.sequence_no, 0) - toNumber(b?.sequence_no, 0));
  return {
    productCode: rows[0].product_code || "-",
    productName: rows[0].product_name_cn || rows[0].product_code || "-",
    routeNo: rows[0].route_no || "-",
    routeName: rows[0].route_name_cn || rows[0].route_no || "-",
    stepCount: orderedRows.length,
    steps: orderedRows.map((row) => row.process_name_cn || row.process_code || "-").join(" -> "),
  };
}
