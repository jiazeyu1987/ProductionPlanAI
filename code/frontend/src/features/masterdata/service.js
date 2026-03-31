import {
  copyMasterdataRoute,
  createMasterdataRoute,
  deleteMasterdataRoute,
  getMasterdataConfig,
  getScheduleCalendarRules,
  listScheduleVersions,
  listOrderMaterialAvailability,
  listProcessRoutes,
  updateMasterdataRoute,
} from ".";

export async function fetchMasterdataBootstrap() {
  const [routeRes, configRes, rulesRes, schedulesRes] = await Promise.all([
    listProcessRoutes(),
    getMasterdataConfig(),
    getScheduleCalendarRules(),
    listScheduleVersions(),
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
    throw new Error("请先填写目标产品编码。");
  }
  if ((normalizedMode === "edit" || normalizedMode === "copy") && !sourceCode) {
    throw new Error("请先选择来源产品编码。");
  }
  if (normalizedSteps.length === 0) {
    throw new Error("至少需要配置一个工序步骤。");
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

  throw new Error("未知的工艺路线操作。");
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
