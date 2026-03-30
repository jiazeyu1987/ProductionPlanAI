import {
  buildLiteSchedule,
  diffDays,
  makeLineOrderKey,
} from "../../utils/liteSchedulerEngine";
import { parseIsoAsUtcDate, parseMonthText } from "./calendarUtils";
import { getMonthDayCount, monthTextFromDate } from "./calendarUtils";
import { formatNumber, toNumber } from "./numberFormatUtils";
import { resolveTaskColorIndex, TASK_BORDER_CLASS_NAMES } from "./taskColorUtils";

export function buildHolidayYearHintText(horizonStart, supportedHolidayYears) {
  const startDate = parseIsoAsUtcDate(horizonStart);
  const startYear = startDate?.getUTCFullYear();
  const visibleHolidayYears = Number.isInteger(startYear)
    ? supportedHolidayYears.filter((year) => year >= startYear)
    : supportedHolidayYears;
  return visibleHolidayYears.length > 0
    ? visibleHolidayYears.join("、")
    : "暂无（请维护节假日配置）";
}

export function buildLineNameMap(lines) {
  return Object.fromEntries((lines || []).map((line) => [line.id, line.name]));
}

export function buildOrderMetaMap(orders) {
  return Object.fromEntries(
    (orders || []).map((order) => [
      order.id,
      {
        orderNo: order.orderNo,
        productName: order.productName || "",
        spec: order.spec || "",
        batchNo: order.batchNo || "",
        linePlanQuantities: order.linePlanQuantities || {},
        lineWorkloads: order.lineWorkloads || {},
      },
    ]),
  );
}

export function buildCalendarPlan({ calendarMonth, plan, scenario }) {
  const parsedMonth = parseMonthText(calendarMonth);
  if (!parsedMonth) {
    return plan;
  }
  const monthEndIso = `${calendarMonth}-${String(
    getMonthDayCount(parsedMonth.year, parsedMonth.month),
  ).padStart(2, "0")}`;
  const daysToMonthEnd = diffDays(monthEndIso, scenario.horizonStart) + 1;
  const expandedDays = Math.max(1, scenario.horizonDays, daysToMonthEnd);
  if (expandedDays <= scenario.horizonDays) {
    return plan;
  }
  return buildLiteSchedule({ ...scenario, horizonDays: expandedDays });
}

export function buildLineOrderColorClassMap(calendarPlan) {
  const colorIndexByLineOrder = {};
  calendarPlan.lineRows.forEach((line) => {
    let prevLineOrderKey = "";
    let prevColorIndex = -1;
    calendarPlan.dates.forEach((date) => {
      const items = line.daily[date]?.items || [];
      items.forEach((item) => {
        const lineOrderKey = makeLineOrderKey(line.lineId, item.orderId);
        let colorIndex =
          typeof colorIndexByLineOrder[lineOrderKey] === "number"
            ? colorIndexByLineOrder[lineOrderKey]
            : resolveTaskColorIndex(item.orderId);
        if (lineOrderKey !== prevLineOrderKey && colorIndex === prevColorIndex) {
          colorIndex = (colorIndex + 1) % TASK_BORDER_CLASS_NAMES.length;
        }
        colorIndexByLineOrder[lineOrderKey] = colorIndex;
        prevLineOrderKey = lineOrderKey;
        prevColorIndex = colorIndex;
      });
    });
  });
  const classMap = {};
  Object.entries(colorIndexByLineOrder).forEach(([lineOrderKey, colorIndex]) => {
    classMap[lineOrderKey] =
      TASK_BORDER_CLASS_NAMES[
        Math.abs(colorIndex) % TASK_BORDER_CLASS_NAMES.length
      ];
  });
  return classMap;
}

export function buildLineRows(planLineRows, scenarioLines) {
  return (planLineRows || []).map((row) => ({
    id: row.lineId,
    line_name: row.lineName,
    base_capacity:
      scenarioLines.find((line) => line.id === row.lineId)?.baseCapacity ?? 0,
    assigned_total: row.assignedTotal,
    capacity_total: row.capacityTotal,
    utilization: row.utilization,
  }));
}

export function buildTotalDailyCapacity(lines, isDurationMode) {
  return (lines || [])
    .filter((line) => line.enabled !== false)
    .reduce((sum, line) => {
      if (isDurationMode) {
        return sum + 1;
      }
      return sum + Math.max(0, toNumber(line.baseCapacity, 0));
    }, 0);
}

export function buildOrderRows({
  planOrderRows,
  lineNameMap,
  isDurationMode,
  totalDailyCapacity,
}) {
  return (planOrderRows || []).map((row) => {
    const plannedQty = Object.values(row.linePlanQuantities || {}).reduce(
      (sum, value) => sum + Math.max(0, Math.round(toNumber(value, 0))),
      0,
    );
    const lineWorkloadDesc = Object.entries(row.lineWorkloads || {})
      .map(
        ([lineId, qty]) =>
          `${lineNameMap[lineId] || lineId}: ${formatNumber(qty)}${isDurationMode ? "天" : "件"}`,
      )
      .join(" | ");
    return {
      id: row.id,
      order_no: row.orderNo,
      product_name: String(row.productName || "").trim() || "-",
      spec: String(row.spec || "").trim() || "-",
      batch_no: String(row.batchNo || "").trim() || "-",
      priority: row.priority === "URGENT" ? "加急" : "常规",
      workload_qty: row.workloadDays,
      planned_qty: plannedQty,
      completed_qty: row.completedDays,
      remaining_qty: row.remainingDays,
      remaining_plan_days:
        isDurationMode
          ? row.remainingDays
          : totalDailyCapacity > 0
            ? row.remainingDays / totalDailyCapacity
            : 0,
      finish_status: row.finishStatus || "-",
      actual_finish_date: row.actualFinishDate || "-",
      line_workloads: lineWorkloadDesc || "-",
      release_date: row.releaseDate,
      due_date: row.dueDate,
      completion_date: row.completionDate || "-",
      delay_days: row.delayDays ?? 0,
      reason: row.reason,
    };
  });
}

export function buildLineDailyCapacityRows(lines) {
  return (lines || []).map((line) => ({
    id: line.id,
    line_name: line.name,
    daily_capacity: line.baseCapacity,
  }));
}

export function buildSnapshotRows(snapshots) {
  return (snapshots || []).map((row) => ({
    id: row.id,
    name: row.name,
    updated_at: row.updatedAt,
    created_at: row.createdAt,
  }));
}

export function buildModalTotals({ orderModalForm, isDurationMode }) {
  const modalTotalWorkload = isDurationMode
    ? Object.values(orderModalForm.linePlanDays || {}).reduce(
        (maxValue, value) =>
          Math.max(maxValue, Math.max(0, Math.round(toNumber(value, 0)))),
        0,
      )
    : Object.values(orderModalForm.lineTotals || {}).reduce((sum, value) => {
        const parsed = Math.max(0, toNumber(value, 0));
        return sum + parsed;
      }, 0);
  const modalTotalPlanQty = Object.values(
    orderModalForm.linePlanQuantities || {},
  ).reduce((sum, value) => sum + Math.max(0, Math.round(toNumber(value, 0))), 0);
  return { modalTotalWorkload, modalTotalPlanQty };
}

export function ensureCalendarMonthFromHorizonStart({ calendarMonth, horizonStart }) {
  return monthTextFromDate(horizonStart) || calendarMonth || "2026-01";
}

