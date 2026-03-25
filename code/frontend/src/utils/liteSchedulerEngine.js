const DAY_MS = 24 * 60 * 60 * 1000;
const EPSILON = 1e-6;

function pad2(value) {
  return String(value).padStart(2, "0");
}

function toIsoDateFromUtc(date) {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
}

function parseIsoDate(value) {
  const text = String(value || "").trim();
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }
  const ts = Date.UTC(year, month - 1, day);
  const date = new Date(ts);
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() + 1 !== month ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return { year, month, day, ts };
}

export function isoToday() {
  return toIsoDateFromUtc(new Date());
}

export function addDays(dateText, offset) {
  const parsed = parseIsoDate(dateText);
  if (!parsed) {
    return dateText;
  }
  return toIsoDateFromUtc(new Date(parsed.ts + Number(offset || 0) * DAY_MS));
}

function dayNumber(dateText) {
  const parsed = parseIsoDate(dateText);
  return parsed ? Math.round(parsed.ts / DAY_MS) : null;
}

export function compareDate(a, b) {
  if (a === b) {
    return 0;
  }
  const aDay = dayNumber(a);
  const bDay = dayNumber(b);
  if (aDay === null && bDay === null) {
    return String(a || "").localeCompare(String(b || ""));
  }
  if (aDay === null) {
    return -1;
  }
  if (bDay === null) {
    return 1;
  }
  return aDay - bDay;
}

export function diffDays(laterDate, earlierDate) {
  const a = dayNumber(laterDate);
  const b = dayNumber(earlierDate);
  if (a === null || b === null) {
    return 0;
  }
  return a - b;
}

function clampNumber(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return fallback;
  }
  return n;
}

function round3(value) {
  return Math.round(value * 1000) / 1000;
}

function positiveOr(value, fallback = 0) {
  return Math.max(0, round3(clampNumber(value, fallback)));
}

function integerInRange(value, fallback, min, max) {
  const n = Math.round(clampNumber(value, fallback));
  return Math.min(max, Math.max(min, n));
}

function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function buildDateRange(startDate, horizonDays) {
  const safeDays = integerInRange(horizonDays, 30, 1, 120);
  return Array.from({ length: safeDays }, (_, idx) => addDays(startDate, idx));
}

function normalizePriority(value) {
  if (String(value || "").toUpperCase() === "URGENT") {
    return "URGENT";
  }
  return "NORMAL";
}

function normalizeLine(line, index) {
  const id = String(line?.id || makeId("line"));
  const name = String(line?.name || "").trim() || `产线-${index + 1}`;
  const baseCapacity = positiveOr(line?.baseCapacity, 1);
  const enabled = line?.enabled !== false;
  const capacityOverrides = {};
  Object.entries(line?.capacityOverrides || {}).forEach(([dateKey, capacityValue]) => {
    if (parseIsoDate(dateKey)) {
      capacityOverrides[dateKey] = positiveOr(capacityValue, baseCapacity);
    }
  });
  return { id, name, baseCapacity, capacityOverrides, enabled };
}

function normalizeOrder(order, index, horizonStart) {
  const id = String(order?.id || makeId("order"));
  const orderNo = String(order?.orderNo || "").trim() || `SO-${pad2(index + 1)}`;
  const workloadDays = positiveOr(order?.workloadDays, 1);
  const completedDays = Math.min(workloadDays, positiveOr(order?.completedDays, 0));
  const dueDate = parseIsoDate(order?.dueDate) ? order.dueDate : addDays(horizonStart, 7);
  const releaseDate = parseIsoDate(order?.releaseDate) ? order.releaseDate : horizonStart;
  return {
    id,
    orderNo,
    workloadDays,
    completedDays,
    dueDate,
    releaseDate,
    priority: normalizePriority(order?.priority)
  };
}

function normalizeLock(lock, index, fallbackStart) {
  const id = String(lock?.id || makeId("lock"));
  const orderId = String(lock?.orderId || "");
  const lineId = String(lock?.lineId || "");
  const startDateRaw = parseIsoDate(lock?.startDate) ? lock.startDate : fallbackStart;
  const endDateRaw = parseIsoDate(lock?.endDate) ? lock.endDate : startDateRaw;
  const startDate = compareDate(startDateRaw, endDateRaw) <= 0 ? startDateRaw : endDateRaw;
  const endDate = compareDate(startDateRaw, endDateRaw) <= 0 ? endDateRaw : startDateRaw;
  const workloadDays = positiveOr(lock?.workloadDays, 0);
  return { id, orderId, lineId, startDate, endDate, workloadDays, seq: Number(lock?.seq ?? index) };
}

export function createDefaultLiteScenario(baseDate = isoToday()) {
  const start = parseIsoDate(baseDate) ? baseDate : isoToday();
  return {
    schemaVersion: 1,
    horizonStart: start,
    horizonDays: 30,
    lines: [
      {
        id: makeId("line"),
        name: "导管产线-1",
        baseCapacity: 1,
        capacityOverrides: {},
        enabled: true
      }
    ],
    orders: [],
    locks: [],
    simulationLogs: []
  };
}

export function normalizeLiteScenario(input) {
  const fallback = createDefaultLiteScenario();
  const horizonStart = parseIsoDate(input?.horizonStart) ? input.horizonStart : fallback.horizonStart;
  const horizonDays = integerInRange(input?.horizonDays, 30, 1, 120);

  const lines = Array.isArray(input?.lines)
    ? input.lines.map((line, index) => normalizeLine(line, index)).filter((line) => line.id)
    : [];
  const safeLines = lines.length > 0 ? lines : fallback.lines;

  const orders = Array.isArray(input?.orders)
    ? input.orders.map((order, index) => normalizeOrder(order, index, horizonStart))
    : [];

  const locks = Array.isArray(input?.locks)
    ? input.locks
        .map((lock, index) => normalizeLock(lock, index, horizonStart))
        .filter((lock) => lock.workloadDays > EPSILON && lock.orderId && lock.lineId)
    : [];

  const simulationLogs = Array.isArray(input?.simulationLogs)
    ? input.simulationLogs
        .map((row) => ({
          date: parseIsoDate(row?.date) ? row.date : horizonStart,
          completedWorkload: positiveOr(row?.completedWorkload, 0),
          note: String(row?.note || "").trim()
        }))
        .slice(0, 30)
    : [];

  return {
    schemaVersion: 1,
    horizonStart,
    horizonDays,
    lines: safeLines,
    orders,
    locks,
    simulationLogs
  };
}

function resolveLineCapacity(line, date) {
  if (Object.prototype.hasOwnProperty.call(line.capacityOverrides, date)) {
    return positiveOr(line.capacityOverrides[date], line.baseCapacity);
  }
  return positiveOr(line.baseCapacity, 0);
}

function orderSortForReplan(a, b, remainingByOrder) {
  const dueCmp = compareDate(a.dueDate, b.dueDate);
  if (dueCmp !== 0) {
    return dueCmp;
  }
  if (a.priority !== b.priority) {
    return a.priority === "URGENT" ? -1 : 1;
  }
  const remainingCmp = (remainingByOrder[b.id] || 0) - (remainingByOrder[a.id] || 0);
  if (Math.abs(remainingCmp) > EPSILON) {
    return remainingCmp;
  }
  return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
}

function makeAllocation(orderId, lineId, date, workloadDays, source, lockId) {
  return {
    id: makeId("alloc"),
    orderId,
    lineId,
    date,
    workloadDays: round3(workloadDays),
    source,
    lockId: lockId || null
  };
}

export function buildLiteSchedule(inputScenario) {
  const scenario = normalizeLiteScenario(inputScenario);
  const dates = buildDateRange(scenario.horizonStart, scenario.horizonDays);
  const lines = scenario.lines.filter((line) => line.enabled !== false);
  const orders = scenario.orders;

  const warnings = [];
  const allocations = [];
  const lineCapByDate = {};
  const lineRemainingCap = {};
  const orderRemaining = {};

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
    orderRemaining[order.id] = Math.max(0, round3(order.workloadDays - order.completedDays));
  });

  function allocateOne(orderId, lineId, date, wanted, source, lockId = null) {
    const remainOrder = orderRemaining[orderId] ?? 0;
    const remainCap = lineRemainingCap[lineId]?.[date] ?? 0;
    const qty = Math.min(positiveOr(wanted, 0), remainOrder, remainCap);
    if (qty <= EPSILON) {
      return 0;
    }
    const safeQty = round3(qty);
    orderRemaining[orderId] = round3((orderRemaining[orderId] || 0) - safeQty);
    lineRemainingCap[lineId][date] = round3((lineRemainingCap[lineId][date] || 0) - safeQty);
    allocations.push(makeAllocation(orderId, lineId, date, safeQty, source, lockId));
    return safeQty;
  }

  const sortedLocks = scenario.locks.slice().sort((a, b) => {
    const startCmp = compareDate(a.startDate, b.startDate);
    if (startCmp !== 0) {
      return startCmp;
    }
    return (a.seq || 0) - (b.seq || 0);
  });

  sortedLocks.forEach((lock) => {
    if (!orderRemaining.hasOwnProperty(lock.orderId)) {
      warnings.push(`锁定片段 ${lock.id} 对应订单不存在，已跳过。`);
      return;
    }
    if (!lineRemainingCap[lock.lineId]) {
      warnings.push(`锁定片段 ${lock.id} 对应产线不存在，已跳过。`);
      return;
    }
    const lockDates = dates.filter(
      (date) => compareDate(date, lock.startDate) >= 0 && compareDate(date, lock.endDate) <= 0
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
      const done = allocateOne(lock.orderId, lock.lineId, date, left, "LOCKED", lock.id);
      left = round3(left - done);
    });

    if (left > EPSILON) {
      warnings.push(`锁定片段 ${lock.id} 仍有 ${left} 天未落地（容量不足或超出范围）。`);
    }
  });

  dates.forEach((date) => {
    const lineIds = lines.map((line) => line.id).filter((lineId) => (lineRemainingCap[lineId]?.[date] || 0) > EPSILON);
    if (lineIds.length === 0) {
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
      let left = orderRemaining[order.id] || 0;
      if (left <= EPSILON) {
        return;
      }
      const dynamicLines = lineIds.slice().sort((a, b) => {
        return (lineRemainingCap[b]?.[date] || 0) - (lineRemainingCap[a]?.[date] || 0);
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

    const dateMap = (orderAllocByDate[item.orderId] = orderAllocByDate[item.orderId] || {});
    dateMap[item.date] = round3((dateMap[item.date] || 0) + item.workloadDays);

    if (item.source === "LOCKED") {
      orderLockedTotals[item.orderId] = round3((orderLockedTotals[item.orderId] || 0) + item.workloadDays);
    } else {
      orderAutoTotals[item.orderId] = round3((orderAutoTotals[item.orderId] || 0) + item.workloadDays);
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

  const nominalDailyCapacity = dates.reduce((sum, date) => {
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
    let completionDate = completed >= order.workloadDays ? addDays(scenario.horizonStart, -1) : null;

    dates.forEach((date) => {
      if (completionDate) {
        return;
      }
      completed = round3(completed + (dateMap[date] || 0));
      if (completed + EPSILON >= order.workloadDays) {
        completionDate = date;
      }
    });

    if (!completionDate && remaining > EPSILON && nominalDailyCapacity > EPSILON) {
      completionDate = addDays(dates[dates.length - 1], Math.ceil(remaining / nominalDailyCapacity));
    }

    const delayDays = completionDate ? Math.max(0, diffDays(completionDate, order.dueDate)) : null;
    const lockedQty = orderLockedTotals[order.id] || 0;
    const autoQty = orderAutoTotals[order.id] || 0;

    let reason = "按交期最小延期优先进行自动重排。";
    if (lockedQty > EPSILON && autoQty > EPSILON) {
      reason = "包含手动锁定区间，剩余工作量按交期优先自动补齐。";
    } else if (lockedQty > EPSILON) {
      reason = "当前分配主要来自手动锁定区间。";
    }

    return {
      ...order,
      scheduledDays: round3(order.workloadDays - remaining - order.completedDays),
      autoScheduledDays: round3(autoQty),
      lockedScheduledDays: round3(lockedQty),
      remainingDays: round3(remaining),
      completionDate,
      delayDays,
      reason
    };
  });

  const lineRows = lines.map((line) => {
    let assignedTotal = 0;
    let capacityTotal = 0;
    const daily = {};
    dates.forEach((date) => {
      const cap = lineCapByDate[line.id]?.[date] || 0;
      const items = allocationMap[`${line.id}|${date}`] || [];
      const assigned = round3(items.reduce((sum, item) => sum + item.workloadDays, 0));
      assignedTotal = round3(assignedTotal + assigned);
      capacityTotal = round3(capacityTotal + cap);
      daily[date] = {
        capacity: cap,
        assigned,
        utilization: cap > EPSILON ? assigned / cap : 0,
        items
      };
    });
    return {
      lineId: line.id,
      lineName: line.name,
      assignedTotal,
      capacityTotal,
      utilization: capacityTotal > EPSILON ? assignedTotal / capacityTotal : 0,
      daily
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
    }, 0)
  );
  const totalAssigned = round3(allocations.reduce((sum, item) => sum + item.workloadDays, 0));
  const delayedOrders = orderRows.filter((row) => Number(row.delayDays) > 0).length;
  const bottleneck = lineRows
    .slice()
    .sort((a, b) => b.utilization - a.utilization)[0];
  const totalRemaining = round3(Object.values(orderRemaining).reduce((sum, value) => sum + value, 0));

  return {
    scenario,
    dates,
    allocations,
    warnings,
    lineRows,
    orderRows,
    summary: {
      horizonStart: scenario.horizonStart,
      horizonEnd: dates[dates.length - 1],
      totalOrders: orders.length,
      totalCapacity,
      totalAssigned,
      utilization: totalCapacity > EPSILON ? totalAssigned / totalCapacity : 0,
      delayedOrders,
      totalRemaining,
      bottleneckLineId: bottleneck?.lineId || null,
      bottleneckLineName: bottleneck?.lineName || null
    }
  };
}

export function advanceLiteScenarioOneDay(inputScenario) {
  const scenario = normalizeLiteScenario(inputScenario);
  const plan = buildLiteSchedule(scenario);
  const today = scenario.horizonStart;
  const completedByOrder = {};
  const consumedByLock = {};

  plan.allocations
    .filter((item) => item.date === today)
    .forEach((item) => {
      completedByOrder[item.orderId] = round3((completedByOrder[item.orderId] || 0) + item.workloadDays);
      if (item.lockId) {
        consumedByLock[item.lockId] = round3((consumedByLock[item.lockId] || 0) + item.workloadDays);
      }
    });

  const newStart = addDays(today, 1);
  const nextOrders = scenario.orders.map((order) => {
    const completed = round3(order.completedDays + (completedByOrder[order.id] || 0));
    return {
      ...order,
      completedDays: Math.min(order.workloadDays, completed)
    };
  });

  const nextLocks = scenario.locks
    .map((lock) => {
      const consumed = consumedByLock[lock.id] || 0;
      const left = round3(lock.workloadDays - consumed);
      if (left <= EPSILON) {
        return null;
      }
      const startDate = compareDate(lock.startDate, newStart) < 0 ? newStart : lock.startDate;
      if (compareDate(startDate, lock.endDate) > 0) {
        return null;
      }
      return {
        ...lock,
        startDate,
        workloadDays: left
      };
    })
    .filter(Boolean);

  const completedToday = round3(Object.values(completedByOrder).reduce((sum, value) => sum + value, 0));
  const nextScenario = normalizeLiteScenario({
    ...scenario,
    horizonStart: newStart,
    orders: nextOrders,
    locks: nextLocks,
    simulationLogs: [
      {
        date: today,
        completedWorkload: completedToday,
        note: "按当前方案推进 1 天"
      },
      ...scenario.simulationLogs
    ].slice(0, 30)
  });
  const nextPlan = buildLiteSchedule(nextScenario);

  return {
    nextScenario,
    daySummary: {
      date: today,
      completedWorkload: completedToday,
      remainingWorkload: nextPlan.summary.totalRemaining,
      delayedOrders: nextPlan.summary.delayedOrders,
      message: `已推进至 ${newStart}，当日完成 ${completedToday} 天工作量。`
    }
  };
}
