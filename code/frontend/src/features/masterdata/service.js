import {
  copyMasterdataRoute,
  createMasterdataRoute,
  deleteMasterdataRoute,
  getMasterdataConfig,
  getScheduleCalendarRules,
  listLegacySchedules,
  listOrderMaterialAvailability,
  listProcessRoutes,
  updateMasterdataRoute,
} from "./apiClient";

export async function fetchMasterdataBootstrap() {
  const [routeRes, configRes, rulesRes, schedulesRes] = await Promise.all([
    listProcessRoutes(),
    getMasterdataConfig().catch(() => null),
    getScheduleCalendarRules().catch(() => null),
    listLegacySchedules().catch(() => null),
  ]);

  return {
    routes: routeRes.items ?? [],
    configRes,
    rulesRes,
    schedules: schedulesRes?.items ?? [],
  };
}

export async function fetchOrderMaterialAvailability(refreshFromErp = false) {
  const res = await listOrderMaterialAvailability(refreshFromErp);
  return res.items ?? [];
}

export async function saveMasterdataRouteByMode({
  mode,
  sourceProductCode,
  targetProductCode,
  steps,
}) {
  const normalizedMode = String(mode || "").trim().toLowerCase();
  const sourceCode = String(sourceProductCode || "").trim().toUpperCase();
  const targetCode = String(targetProductCode || "").trim().toUpperCase();
  const normalizedSteps = Array.isArray(steps) ? steps.filter((row) => row?.process_code) : [];

  if ((normalizedMode === "create" || normalizedMode === "copy") && !targetCode) {
    throw new Error("璇峰厛濉啓鐩爣浜у搧缂栫爜銆?");
  }
  if ((normalizedMode === "edit" || normalizedMode === "copy") && !sourceCode) {
    throw new Error("璇峰厛閫夋嫨鏉ユ簮浜у搧缂栫爜銆?");
  }
  if (normalizedSteps.length === 0) {
    throw new Error("鑷冲皯闇€瑕侀厤缃竴涓伐搴忔楠ゃ€?");
  }

  if (normalizedMode === "create") {
    return createMasterdataRoute({
      product_code: targetCode,
      steps: normalizedSteps,
    });
  }
  if (normalizedMode === "edit") {
    return updateMasterdataRoute({
      product_code: sourceCode,
      steps: normalizedSteps,
    });
  }
  if (normalizedMode === "copy") {
    return copyMasterdataRoute({
      source_product_code: sourceCode,
      target_product_code: targetCode,
      steps: normalizedSteps,
    });
  }

  throw new Error("鏈煡鐨勫伐鑹鸿矾绾挎搷浣溿€?");
}

export async function deleteMasterdataRouteByProductCode(productCode) {
  const normalized = String(productCode || "").trim().toUpperCase();
  if (!normalized) {
    return false;
  }
  await deleteMasterdataRoute({
    product_code: normalized,
  });
  return true;
}
