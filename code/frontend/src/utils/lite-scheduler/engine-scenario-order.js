import { addDays, parseIsoDate } from "./calendar";
import { EPSILON, clampNumber, makeId, positiveOr, round3 } from "./engine-core";

function pad2(value) {
  return String(value).padStart(2, "0");
}

function extractTrailingNumber(text) {
  const match = String(text || "").match(/(\d+)(?!.*\d)/);
  if (!match) {
    return null;
  }
  const n = Number(match[1]);
  return Number.isFinite(n) ? n : null;
}

function normalizeOrderLineWorkloads(inputValue) {
  const normalized = {};
  Object.entries(inputValue || {}).forEach(([lineIdRaw, daysRaw]) => {
    const lineId = String(lineIdRaw || "").trim();
    const days = positiveOr(daysRaw, 0);
    if (lineId && days > EPSILON) {
      normalized[lineId] = round3((normalized[lineId] || 0) + days);
    }
  });
  return normalized;
}

function normalizeOrderLinePlanDays(inputValue) {
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

function normalizeOrderLinePlanQuantities(inputValue) {
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

export function normalizeOrder(order, index, horizonStart) {
  const id = String(order?.id || makeId("order"));
  const orderNo =
    String(order?.orderNo || "").trim() || `SO-${pad2(index + 1)}`;
  const productName = String(
    order?.productName ?? order?.product_name ?? order?.product_name_cn ?? "",
  ).trim();
  const spec = String(
    order?.spec ?? order?.specModel ?? order?.spec_model ?? "",
  ).trim();
  const batchNo = String(
    order?.batchNo ?? order?.batch_no ?? order?.production_batch_no ?? "",
  ).trim();
  const inferredSeq = extractTrailingNumber(orderNo) ?? index + 1;
  const orderSeqRaw = Number(order?.orderSeq);
  const orderSeq = Number.isFinite(orderSeqRaw)
    ? Math.max(1, Math.round(orderSeqRaw))
    : Math.max(1, inferredSeq);
  const lineWorkloads = normalizeOrderLineWorkloads(order?.lineWorkloads);
  const linePlanDays = normalizeOrderLinePlanDays(
    order?.linePlanDays ?? order?.line_plan_days,
  );
  const linePlanQuantities = normalizeOrderLinePlanQuantities(
    order?.linePlanQuantities ?? order?.line_plan_quantities,
  );
  const lineWorkloadTotal = round3(
    Object.values(lineWorkloads).reduce((sum, value) => sum + value, 0),
  );
  const linePlanDayMax = round3(
    Object.values(linePlanDays).reduce(
      (maxValue, value) => Math.max(maxValue, value),
      0,
    ),
  );
  const baseWorkloadDays = positiveOr(
    order?.workloadDays,
    lineWorkloadTotal > EPSILON
      ? lineWorkloadTotal
      : linePlanDayMax > EPSILON
        ? linePlanDayMax
        : 1,
  );
  const workloadDays = Math.max(
    baseWorkloadDays,
    lineWorkloadTotal,
    linePlanDayMax,
  );
  const completedDays = Math.min(
    workloadDays,
    positiveOr(order?.completedDays, 0),
  );
  const dueDate = parseIsoDate(order?.dueDate)
    ? order.dueDate
    : addDays(horizonStart, 7);
  const releaseDate = parseIsoDate(order?.releaseDate)
    ? order.releaseDate
    : horizonStart;
  return {
    id,
    orderNo,
    productName,
    spec,
    batchNo,
    workloadDays,
    completedDays,
    dueDate,
    releaseDate,
    priority: String(order?.priority || "").trim() || "NORMAL",
    orderSeq,
    lineWorkloads,
    linePlanDays,
    linePlanQuantities,
  };
}

export function inferNextOrderSeq(orders) {
  let maxSeq = 0;
  (orders || []).forEach((order) => {
    const seq = Number(order?.orderSeq);
    if (Number.isFinite(seq) && seq > maxSeq) {
      maxSeq = seq;
    }
  });
  return Math.max(1, maxSeq + 1);
}

