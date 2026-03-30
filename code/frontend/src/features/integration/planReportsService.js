import {
  listPlanReportOrderPool,
  listPlanReportScheduleVersions,
  listWorkshopMonthlyPlan,
  listWorkshopWeeklyPlan,
} from "./apiClient";

function pickReportVersion(versions) {
  const rows = Array.isArray(versions) ? versions : [];
  if (rows.length === 0) {
    return "";
  }
  const published = rows.filter((item) => item.status === "PUBLISHED");
  if (published.length > 0) {
    return published[published.length - 1].version_no || "";
  }
  return rows[rows.length - 1].version_no || "";
}

export async function fetchPlanReportsSnapshot(nextVersionNo = "") {
  const versionsRes = await listPlanReportScheduleVersions();
  const versions = versionsRes.items ?? [];

  let resolvedVersionNo = String(nextVersionNo || "").trim();
  if (resolvedVersionNo && !versions.some((row) => row.version_no === resolvedVersionNo)) {
    resolvedVersionNo = "";
  }
  if (!resolvedVersionNo) {
    resolvedVersionNo = pickReportVersion(versions);
  }

  const resolvedVersion = versions.find((row) => row.version_no === resolvedVersionNo) || null;
  const [weeklyRes, monthlyRes, orderPoolRes] = await Promise.all([
    listWorkshopWeeklyPlan(resolvedVersionNo),
    listWorkshopMonthlyPlan(resolvedVersionNo),
    listPlanReportOrderPool(),
  ]);

  const orderProductCodeMap = {};
  for (const row of orderPoolRes.items ?? []) {
    const orderNo = String(row.order_no || "").trim();
    const productCode = String(row.product_code || "").trim();
    if (orderNo && productCode) {
      orderProductCodeMap[orderNo] = productCode;
    }
  }

  return {
    versions,
    resolvedVersionNo,
    resolvedVersionStatusName:
      resolvedVersion?.status_name_cn || resolvedVersion?.status || (resolvedVersionNo ? "-" : "实时状态"),
    weeklyRawRows: weeklyRes.items ?? [],
    monthlyRows: monthlyRes.items ?? [],
    orderProductCodeMap,
  };
}

