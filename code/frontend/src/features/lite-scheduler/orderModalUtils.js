import { addDays } from "../../utils/liteSchedulerEngine";
import { formatAutoOrderNo, toNumber } from "./numberFormatUtils";

export function buildModalLineTotals(scenario, prevLineTotals = {}) {
  const totals = {};
  (scenario?.lines || []).forEach((line) => {
    const prevValue = prevLineTotals?.[line.id];
    totals[line.id] = prevValue === undefined || prevValue === null ? "0" : String(prevValue);
  });
  return totals;
}

export function buildModalLinePlanDays(scenario, prevLinePlanDays = {}) {
  const days = {};
  (scenario?.lines || []).forEach((line) => {
    const prevValue = prevLinePlanDays?.[line.id];
    days[line.id] = prevValue === undefined || prevValue === null ? "0" : String(prevValue);
  });
  return days;
}

export function buildModalLinePlanQuantities(scenario, prevLinePlanQuantities = {}) {
  const quantities = {};
  (scenario?.lines || []).forEach((line) => {
    const prevValue = prevLinePlanQuantities?.[line.id];
    quantities[line.id] = prevValue === undefined || prevValue === null ? "0" : String(prevValue);
  });
  return quantities;
}

export function createOrderModalForm(scenario) {
  return {
    orderNo: formatAutoOrderNo(scenario?.nextOrderSeq || 1),
    productName: "",
    spec: "",
    batchNo: "",
    dueDate: addDays(scenario?.horizonStart, 7),
    releaseDate: scenario?.horizonStart,
    priority: "NORMAL",
    lineTotals: buildModalLineTotals(scenario),
    linePlanDays: buildModalLinePlanDays(scenario),
    linePlanQuantities: buildModalLinePlanQuantities(scenario),
  };
}

export function createOrderModalFormFromOrder(scenario, order) {
  const lineTotals = buildModalLineTotals(scenario);
  const linePlanDays = buildModalLinePlanDays(scenario);
  const linePlanQuantities = buildModalLinePlanQuantities(scenario);

  Object.entries(order?.lineWorkloads || {}).forEach(([lineId, value]) => {
    if (Object.prototype.hasOwnProperty.call(lineTotals, lineId)) {
      lineTotals[lineId] = String(value);
    }
  });
  Object.entries(order?.linePlanDays || {}).forEach(([lineId, value]) => {
    if (Object.prototype.hasOwnProperty.call(linePlanDays, lineId)) {
      linePlanDays[lineId] = String(Math.max(0, Math.round(toNumber(value, 0))));
    }
  });
  Object.entries(order?.linePlanQuantities || {}).forEach(([lineId, value]) => {
    if (Object.prototype.hasOwnProperty.call(linePlanQuantities, lineId)) {
      linePlanQuantities[lineId] = String(Math.max(0, Math.round(toNumber(value, 0))));
    }
  });

  return {
    orderNo: String(order?.orderNo || ""),
    productName: String(order?.productName || ""),
    spec: String(order?.spec || ""),
    batchNo: String(order?.batchNo || ""),
    dueDate: order?.dueDate || addDays(scenario?.horizonStart, 7),
    releaseDate: order?.releaseDate || scenario?.horizonStart,
    priority: order?.priority === "URGENT" ? "URGENT" : "NORMAL",
    lineTotals,
    linePlanDays,
    linePlanQuantities,
  };
}
