import { orderSortForReplan } from "./allocation";
import {
  buildDateRange,
  compareDate,
  diffDays,
  nextBusinessDate,
  parseIsoDate,
} from "./calendar";
import {
  alignToBusinessDate,
  countBusinessDaysInclusive,
  moveBusinessDays,
} from "./constraints";
import { buildScheduleSummary } from "./scoring";

export function buildLiteScheduleByDurationManual(scenario, helpers) {
  const { EPSILON, clampNumber, round3, makeId, makeLineOrderKey } = helpers;
  const dates = buildDateRange(
    scenario.horizonStart,
    scenario.horizonDays,
    scenario.skipStatutoryHolidays,
    scenario.weekendRestMode,
    scenario.dateWorkModeByDate,
  );
  const lines = scenario.lines.filter((line) => line.enabled !== false);
  const orders = scenario.orders
    .slice()
    .sort((a, b) => orderSortForReplan(a, b, {}));
  const warnings = [];
  const allocations = [];
  const orderSegments = {};
  const todayAnchor = alignToBusinessDate(scenario.horizonStart, scenario);

  if (scenario.locks.length > 0) {
    warnings.push("按天数模式下忽略锁定片段。");
  }

  lines.forEach((line) => {
    let cursor = null;
    orders.forEach((order) => {
      const plannedDays = Math.max(
        0,
        Math.round(clampNumber(order.linePlanDays?.[line.id], 0)),
      );
      if (plannedDays <= 0) {
        return;
      }
      const releaseStart = alignToBusinessDate(order.releaseDate, scenario);
      if (!cursor) {
        cursor = releaseStart;
      }
      const startDate =
        compareDate(cursor, releaseStart) >= 0 ? cursor : releaseStart;
      const plannedEndDate = moveBusinessDays(startDate, plannedDays - 1, scenario);
      const key = makeLineOrderKey(line.id, order.id);
      const rawManualFinish = scenario.manualFinishByLineOrder?.[key];
      const manualFinishDate = parseIsoDate(rawManualFinish)
        ? rawManualFinish
        : null;

      let actualEndDate = plannedEndDate;
      let finishSource = "PLANNED";
      if (manualFinishDate) {
        actualEndDate =
          compareDate(manualFinishDate, startDate) < 0
            ? startDate
            : manualFinishDate;
        finishSource = "MANUAL";
      } else if (compareDate(todayAnchor, plannedEndDate) > 0) {
        actualEndDate = todayAnchor;
        finishSource = "EXTENDED";
      }

      const segment = {
        lineId: line.id,
        lineName: line.name,
        orderId: order.id,
        startDate,
        plannedEndDate,
        actualEndDate,
        plannedDays,
        manualFinishDate,
        finishSource,
      };
      if (!orderSegments[order.id]) {
        orderSegments[order.id] = [];
      }
      orderSegments[order.id].push(segment);

      dates.forEach((date) => {
        if (compareDate(date, startDate) < 0 || compareDate(date, actualEndDate) > 0) {
          return;
        }
        allocations.push({
          id: makeId("alloc"),
          orderId: order.id,
          lineId: line.id,
          date,
          workloadDays: 1,
          source: "DURATION",
          lockId: null,
          segmentStartDate: startDate,
          plannedEndDate,
          actualEndDate,
          finishSource,
          manualFinishDate,
        });
      });

      cursor = nextBusinessDate(
        actualEndDate,
        scenario.skipStatutoryHolidays,
        scenario.weekendRestMode,
        scenario.dateWorkModeByDate,
      );
    });
  });

  orders.forEach((order) => {
    const plannedCount = Object.values(order.linePlanDays || {}).reduce(
      (sum, value) => sum + Math.max(0, Math.round(clampNumber(value, 0))),
      0,
    );
    if (plannedCount <= 0) {
      warnings.push(`订单 ${order.orderNo} 未设置产线计划天数。`);
    }
  });

  const allocationMap = {};
  const orderAllocByDate = {};
  allocations.forEach((item) => {
    const key = `${item.lineId}|${item.date}`;
    if (!allocationMap[key]) {
      allocationMap[key] = [];
    }
    allocationMap[key].push(item);

    const dateMap = (orderAllocByDate[item.orderId] =
      orderAllocByDate[item.orderId] || {});
    dateMap[item.date] = round3((dateMap[item.date] || 0) + item.workloadDays);
  });

  const lineRows = lines.map((line) => {
    let assignedTotal = 0;
    let capacityTotal = 0;
    const daily = {};
    dates.forEach((date) => {
      const items = allocationMap[`${line.id}|${date}`] || [];
      const assigned = round3(
        items.reduce((sum, item) => sum + item.workloadDays, 0),
      );
      const cap = 1;
      assignedTotal = round3(assignedTotal + assigned);
      capacityTotal = round3(capacityTotal + cap);
      daily[date] = {
        capacity: cap,
        assigned,
        utilization: cap > EPSILON ? assigned / cap : 0,
        items,
      };
    });
    return {
      lineId: line.id,
      lineName: line.name,
      assignedTotal,
      capacityTotal,
      utilization: capacityTotal > EPSILON ? assignedTotal / capacityTotal : 0,
      daily,
    };
  });

  const orderRows = orders.map((order) => {
    const segments = orderSegments[order.id] || [];
    const plannedDays = round3(
      Object.values(order.linePlanDays || {}).reduce(
        (maxValue, value) =>
          Math.max(maxValue, Math.max(0, Math.round(clampNumber(value, 0)))),
        0,
      ),
    );
    const dateMap = orderAllocByDate[order.id] || {};
    const scheduledDays = round3(
      Object.values(dateMap).reduce((sum, value) => sum + value, 0),
    );

    let completedDays = 0;
    let completionDate = null;
    let hasExtendedSegment = false;
    let manualFinishedCount = 0;
    segments.forEach((segment) => {
      const endForCompleted =
        compareDate(segment.actualEndDate, todayAnchor) <= 0
          ? segment.actualEndDate
          : todayAnchor;
      const segmentCompletedDays = countBusinessDaysInclusive(
        segment.startDate,
        endForCompleted,
        scenario,
      );
      completedDays = round3(Math.max(completedDays, segmentCompletedDays));
      if (segment.finishSource === "EXTENDED") {
        hasExtendedSegment = true;
      } else if (
        !completionDate ||
        compareDate(segment.actualEndDate, completionDate) > 0
      ) {
        completionDate = segment.actualEndDate;
      }
      if (segment.manualFinishDate) {
        manualFinishedCount += 1;
      }
    });
    if (hasExtendedSegment) {
      completionDate = null;
    }

    const safeCompleted = Math.min(plannedDays, completedDays);
    const remainingDays = hasExtendedSegment
      ? round3(Math.max(1, plannedDays - safeCompleted))
      : round3(Math.max(0, plannedDays - safeCompleted));
    const allManualFinished =
      segments.length > 0 && manualFinishedCount === segments.length;
    const hasManualFinished = manualFinishedCount > 0;
    const finishStatus =
      segments.length === 0
        ? "未配置"
        : allManualFinished
          ? "已结束"
          : hasManualFinished
            ? "部分报结束"
            : "未报结束";
    const actualFinishDate = allManualFinished ? completionDate : null;
    const reasonParts = [];
    if (segments.length === 0) {
      reasonParts.push("未配置按天数产线计划");
    } else if (hasExtendedSegment) {
      reasonParts.push("存在未报结束产线，后续订单顺延");
    } else {
      reasonParts.push("按计划天数排产，可手动报结束");
    }

    return {
      ...order,
      lineWorkloads: order.linePlanDays || {},
      workloadDays: plannedDays,
      completedDays: safeCompleted,
      scheduledDays,
      autoScheduledDays: scheduledDays,
      lockedScheduledDays: 0,
      remainingDays,
      completionDate,
      actualFinishDate,
      finishStatus,
      delayDays: completionDate
        ? Math.max(0, diffDays(completionDate, order.dueDate))
        : hasExtendedSegment
          ? Math.max(0, diffDays(todayAnchor, order.dueDate))
          : null,
      reason: `${reasonParts.join("；")}。`,
    };
  });

  const totalCapacity = round3(
    lineRows.reduce((sum, line) => sum + line.capacityTotal, 0),
  );
  const totalAssigned = round3(
    lineRows.reduce((sum, line) => sum + line.assignedTotal, 0),
  );
  const totalRemaining = round3(
    orderRows.reduce((sum, row) => sum + Math.max(0, row.remainingDays || 0), 0),
  );

  return {
    scenario,
    dates,
    allocations,
    warnings,
    lineRows,
    orderRows,
    summary: buildScheduleSummary({
      scenario,
      dates,
      lineRows,
      orderRows,
      totalOrders: orders.length,
      totalCapacity,
      totalAssigned,
      totalRemaining,
      epsilon: EPSILON,
    }),
  };
}
