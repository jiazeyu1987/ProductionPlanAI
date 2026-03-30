import { compareDate, parseIsoDate } from "./calendar";
import { EPSILON, clampNumber, makeId, makeLineOrderKey, positiveOr, round3 } from "./engine-core";

function parseLineOrderKey(text) {
  const parts = String(text || "").split("|");
  if (parts.length !== 2) {
    return null;
  }
  const lineId = String(parts[0] || "").trim();
  const orderId = String(parts[1] || "").trim();
  if (!lineId || !orderId) {
    return null;
  }
  return { lineId, orderId };
}

export function normalizeLock(lock, index, fallbackStart) {
  const id = String(lock?.id || makeId("lock"));
  const orderId = String(lock?.orderId || "").trim();
  const lineId = String(lock?.lineId || "").trim();
  const startDateRaw = parseIsoDate(lock?.startDate)
    ? lock.startDate
    : fallbackStart;
  const endDateRaw = parseIsoDate(lock?.endDate) ? lock.endDate : startDateRaw;
  const startDate =
    compareDate(startDateRaw, endDateRaw) <= 0 ? startDateRaw : endDateRaw;
  const endDate =
    compareDate(startDateRaw, endDateRaw) <= 0 ? endDateRaw : startDateRaw;
  const workloadDays = positiveOr(lock?.workloadDays, 0);
  return {
    id,
    orderId,
    lineId,
    startDate,
    endDate,
    workloadDays,
    seq: Number(lock?.seq ?? index),
  };
}

export function normalizeManualFinishByLineOrder(inputValue, orderIdSet, lineIdSet) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([keyRaw, dateRaw]) => {
    const parsedKey = parseLineOrderKey(keyRaw);
    if (!parsedKey) {
      return;
    }
    if (!lineIdSet.has(parsedKey.lineId) || !orderIdSet.has(parsedKey.orderId)) {
      return;
    }
    if (!parseIsoDate(dateRaw)) {
      return;
    }
    normalized[makeLineOrderKey(parsedKey.lineId, parsedKey.orderId)] = dateRaw;
  });
  return normalized;
}

export function normalizeOrderLinePlanDays(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([lineIdRaw, daysRaw]) => {
    const lineId = String(lineIdRaw || "").trim();
    const days = Math.max(0, Math.round(clampNumber(daysRaw, 0)));
    if (lineId && days > 0) {
      normalized[lineId] = days;
    }
  });
  return normalized;
}

export function normalizeOrderLinePlanQuantities(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([lineIdRaw, qtyRaw]) => {
    const lineId = String(lineIdRaw || "").trim();
    const qty = Math.max(0, Math.round(clampNumber(qtyRaw, 0)));
    if (lineId && qty > 0) {
      normalized[lineId] = qty;
    }
  });
  return normalized;
}

export function sanitizeLinePlans(order, lineIdSet) {
  const nextLineWorkloads = {};
  Object.entries(order.lineWorkloads || {}).forEach(([lineId, value]) => {
    if (lineIdSet.has(lineId) && value > EPSILON) {
      nextLineWorkloads[lineId] = value;
    }
  });
  const nextLinePlanDays = {};
  Object.entries(order.linePlanDays || {}).forEach(([lineId, value]) => {
    const days = Math.max(0, Math.round(clampNumber(value, 0)));
    if (lineIdSet.has(lineId) && days > 0) {
      nextLinePlanDays[lineId] = days;
    }
  });
  const nextLinePlanQuantities = {};
  Object.entries(order.linePlanQuantities || {}).forEach(([lineId, value]) => {
    const qty = Math.max(0, Math.round(clampNumber(value, 0)));
    if (lineIdSet.has(lineId) && qty > 0) {
      nextLinePlanQuantities[lineId] = qty;
    }
  });
  const lineTotal = round3(
    Object.values(nextLineWorkloads).reduce((sum, value) => sum + value, 0),
  );
  const linePlanTotal = round3(
    Object.values(nextLinePlanDays).reduce((sum, value) => sum + value, 0),
  );
  const workloadDays = Math.max(order.workloadDays, lineTotal, linePlanTotal);
  return {
    workloadDays,
    completedDays: Math.min(workloadDays, order.completedDays),
    lineWorkloads: nextLineWorkloads,
    linePlanDays: nextLinePlanDays,
    linePlanQuantities: nextLinePlanQuantities,
  };
}

