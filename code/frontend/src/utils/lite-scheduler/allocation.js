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

function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function resolveLineCapacity(line, date) {
  if (Object.prototype.hasOwnProperty.call(line.capacityOverrides, date)) {
    return positiveOr(line.capacityOverrides[date], line.baseCapacity);
  }
  return positiveOr(line.baseCapacity, 0);
}

export function orderSortForReplan(a, b) {
  const seqCmp = (Number(a.orderSeq) || 0) - (Number(b.orderSeq) || 0);
  if (seqCmp !== 0) {
    return seqCmp;
  }
  return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
}

export function makeAllocation(orderId, lineId, date, workloadDays, source, lockId) {
  return {
    id: makeId("alloc"),
    orderId,
    lineId,
    date,
    workloadDays: round3(workloadDays),
    source,
    lockId: lockId || null,
  };
}

export function sortLocksByDateAndSeq(locks, compareDate) {
  return (locks || []).slice().sort((a, b) => {
    const startCmp = compareDate(a.startDate, b.startDate);
    if (startCmp !== 0) {
      return startCmp;
    }
    return (a.seq || 0) - (b.seq || 0);
  });
}
