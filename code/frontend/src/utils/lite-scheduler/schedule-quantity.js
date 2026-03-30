import {
  makeAllocation,
  orderSortForReplan,
  resolveLineCapacity,
  sortLocksByDateAndSeq,
} from "./allocation";
import { addDays, buildDateRange, compareDate, diffDays } from "./calendar";
import { buildScheduleSummary } from "./scoring";

export function buildLiteScheduleByQuantityCapacity(scenario, helpers) {
  const { EPSILON, positiveOr, round3 } = helpers;
  const dates = buildDateRange(
    scenario.horizonStart,
    scenario.horizonDays,
    scenario.skipStatutoryHolidays,
    scenario.weekendRestMode,
    scenario.dateWorkModeByDate,
  );
  const lines = scenario.lines.filter((line) => line.enabled !== false);
  const orders = scenario.orders;
  const lineSet = new Set(lines.map((line) => line.id));

  const warnings = [];
  const allocations = [];
  const lineCapByDate = {};
  const lineRemainingCap = {};
  const orderRemaining = {};
  const orderLineRemaining = {};

  lines.forEach((line) => {
    lineCapByDate[line.id] = {};
    lineRemainingCap[line.id] = {};
    dates.forEach((date) => {
      const cap = resolveLineCapacity(line, date);
      lineCapByDate[line.id][date] = cap;
      lineRemainingCap[line.id][date] = cap;
    });
  });

  orders.forEach((order) => {
    const remaining = Math.max(0, round3(order.workloadDays - order.completedDays));
    orderRemaining[order.id] = remaining;

    const requested = {};
    Object.entries(order.lineWorkloads || {}).forEach(([lineId, qty]) => {
      if (!lineSet.has(lineId)) {
        warnings.push(`订单 ${order.orderNo} 指定产线 ${lineId} 不存在，已忽略。`);
        return;
      }
      if (qty > EPSILON) {
        requested[lineId] = qty;
      }
    });

    const requestedTotal = round3(
      Object.values(requested).reduce((sum, value) => sum + value, 0),
    );
    const ratio =
      requestedTotal > EPSILON && remaining > EPSILON
        ? Math.min(1, remaining / requestedTotal)
        : 0;

    const normalized = {};
    Object.entries(requested).forEach(([lineId, qty]) => {
      const value = round3(qty * ratio);
      if (value > EPSILON) {
        normalized[lineId] = value;
      }
    });
    orderLineRemaining[order.id] = normalized;
  });

  function reduceLineRequirement(orderId, lineId, qty) {
    const safeQty = positiveOr(qty, 0);
    if (safeQty <= EPSILON) {
      return;
    }
    const map = orderLineRemaining[orderId];
    if (!map || !Object.prototype.hasOwnProperty.call(map, lineId)) {
      return;
    }
    map[lineId] = round3((map[lineId] || 0) - safeQty);
    if (map[lineId] <= EPSILON) {
      delete map[lineId];
    }
  }

  function allocateOne(orderId, lineId, date, wanted, source, lockId = null) {
    const remainOrder = orderRemaining[orderId] ?? 0;
    const remainCap = lineRemainingCap[lineId]?.[date] ?? 0;
    const qty = Math.min(positiveOr(wanted, 0), remainOrder, remainCap);
    if (qty <= EPSILON) {
      return 0;
    }
    const safeQty = round3(qty);
    orderRemaining[orderId] = round3((orderRemaining[orderId] || 0) - safeQty);
    lineRemainingCap[lineId][date] = round3(
      (lineRemainingCap[lineId][date] || 0) - safeQty,
    );
    allocations.push(
      makeAllocation(orderId, lineId, date, safeQty, source, lockId),
    );
    return safeQty;
  }

  const sortedLocks = sortLocksByDateAndSeq(scenario.locks, compareDate);

  sortedLocks.forEach((lock) => {
    if (!Object.prototype.hasOwnProperty.call(orderRemaining, lock.orderId)) {
      warnings.push(`锁定片段 ${lock.id} 对应订单不存在，已跳过。`);
      return;
    }
    if (!lineRemainingCap[lock.lineId]) {
      warnings.push(`锁定片段 ${lock.id} 对应产线不存在，已跳过。`);
      return;
    }

    const lockDates = dates.filter(
      (date) =>
        compareDate(date, lock.startDate) >= 0 &&
        compareDate(date, lock.endDate) <= 0,
    );
    if (lockDates.length === 0) {
      warnings.push(`锁定片段 ${lock.id} 不在当前周期内，已跳过。`);
      return;
    }

    let left = Math.min(lock.workloadDays, orderRemaining[lock.orderId] || 0);
    lockDates.forEach((date) => {
      if (left <= EPSILON) {
        return;
      }
      const done = allocateOne(
        lock.orderId,
        lock.lineId,
        date,
        left,
        "LOCKED",
        lock.id,
      );
      reduceLineRequirement(lock.orderId, lock.lineId, done);
      left = round3(left - done);
    });
  });

  dates.forEach((date) => {
    const activeLineIds = lines
      .map((line) => line.id)
      .filter((lineId) => (lineRemainingCap[lineId]?.[date] || 0) > EPSILON);
    if (activeLineIds.length === 0) {
      return;
    }

    const candidates = orders
      .filter((order) => {
        const remains = orderRemaining[order.id] || 0;
        return remains > EPSILON && compareDate(date, order.releaseDate) >= 0;
      })
      .sort((a, b) => orderSortForReplan(a, b, orderRemaining));

    if (candidates.length === 0) {
      return;
    }

    candidates.forEach((order) => {
      const lineReq = orderLineRemaining[order.id] || {};
      Object.entries(lineReq)
        .filter(([, qty]) => qty > EPSILON)
        .sort((a, b) => b[1] - a[1])
        .forEach(([lineId, qty]) => {
          if (!lineRemainingCap[lineId]) {
            return;
          }
          const done = allocateOne(order.id, lineId, date, qty, "AUTO_LINE");
          reduceLineRequirement(order.id, lineId, done);
        });
    });

    candidates.forEach((order) => {
      let left = orderRemaining[order.id] || 0;
      if (left <= EPSILON) {
        return;
      }
      const dynamicLines = activeLineIds.slice().sort((a, b) => {
        return (
          (lineRemainingCap[b]?.[date] || 0) -
          (lineRemainingCap[a]?.[date] || 0)
        );
      });
      dynamicLines.forEach((lineId) => {
        if (left <= EPSILON) {
          return;
        }
        const done = allocateOne(order.id, lineId, date, left, "AUTO");
        left = round3(left - done);
      });
    });
  });

  const allocationMap = {};
  const orderAllocByDate = {};
  const orderAutoTotals = {};
  const orderLockedTotals = {};

  allocations.forEach((item) => {
    const lineDateKey = `${item.lineId}|${item.date}`;
    if (!allocationMap[lineDateKey]) {
      allocationMap[lineDateKey] = [];
    }
    allocationMap[lineDateKey].push(item);

    const dateMap = (orderAllocByDate[item.orderId] =
      orderAllocByDate[item.orderId] || {});
    dateMap[item.date] = round3((dateMap[item.date] || 0) + item.workloadDays);

    if (item.source === "LOCKED") {
      orderLockedTotals[item.orderId] = round3(
        (orderLockedTotals[item.orderId] || 0) + item.workloadDays,
      );
    } else {
      orderAutoTotals[item.orderId] = round3(
        (orderAutoTotals[item.orderId] || 0) + item.workloadDays,
      );
    }
  });

  Object.values(allocationMap).forEach((items) => {
    items.sort((a, b) => {
      const orderA = orders.find((row) => row.id === a.orderId);
      const orderB = orders.find((row) => row.id === b.orderId);
      if (orderA && orderB) {
        return orderSortForReplan(orderA, orderB, orderRemaining);
      }
      return String(a.orderId).localeCompare(String(b.orderId));
    });
  });

  const nominalDailyCapacity =
    dates.reduce((sum, date) => {
      return (
        sum +
        lines.reduce((lineSum, line) => {
          return lineSum + (lineCapByDate[line.id]?.[date] || 0);
        }, 0)
      );
    }, 0) / Math.max(dates.length, 1);

  const orderRows = orders.map((order) => {
    const remaining = orderRemaining[order.id] || 0;
    const dateMap = orderAllocByDate[order.id] || {};

    let completed = order.completedDays;
    let completionDate =
      completed >= order.workloadDays ? addDays(scenario.horizonStart, -1) : null;

    dates.forEach((date) => {
      if (completionDate) {
        return;
      }
      completed = round3(completed + (dateMap[date] || 0));
      if (completed + EPSILON >= order.workloadDays) {
        completionDate = date;
      }
    });

    if (
      !completionDate &&
      remaining > EPSILON &&
      nominalDailyCapacity > EPSILON
    ) {
      completionDate = addDays(
        dates[dates.length - 1],
        Math.ceil(remaining / nominalDailyCapacity),
      );
    }

    const hasLinePreference =
      Object.values(order.lineWorkloads || {}).reduce((sum, value) => sum + value, 0) >
      EPSILON;
    const reasonParts = [];
    if (hasLinePreference) {
      reasonParts.push("优先满足指定产线工作量");
    }
    if ((orderLockedTotals[order.id] || 0) > EPSILON) {
      reasonParts.push("手动锁定优先");
    }
    reasonParts.push("其余按订单顺序补齐");

    return {
      ...order,
      scheduledDays: round3(order.workloadDays - remaining - order.completedDays),
      autoScheduledDays: round3(orderAutoTotals[order.id] || 0),
      lockedScheduledDays: round3(orderLockedTotals[order.id] || 0),
      remainingDays: round3(remaining),
      completionDate,
      delayDays: completionDate
        ? Math.max(0, diffDays(completionDate, order.dueDate))
        : null,
      reason: `${reasonParts.join("；")}。`,
    };
  });

  const lineRows = lines.map((line) => {
    let assignedTotal = 0;
    let capacityTotal = 0;
    const daily = {};
    dates.forEach((date) => {
      const cap = lineCapByDate[line.id]?.[date] || 0;
      const items = allocationMap[`${line.id}|${date}`] || [];
      const assigned = round3(
        items.reduce((sum, item) => sum + item.workloadDays, 0),
      );
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

  const totalCapacity = round3(
    lines.reduce((sum, line) => {
      return (
        sum +
        dates.reduce((lineSum, date) => {
          return lineSum + (lineCapByDate[line.id]?.[date] || 0);
        }, 0)
      );
    }, 0),
  );
  const totalAssigned = round3(
    allocations.reduce((sum, item) => sum + item.workloadDays, 0),
  );
  const totalRemaining = round3(
    Object.values(orderRemaining).reduce((sum, value) => sum + value, 0),
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
