import { normalizeShiftCode, shiftSortIndex } from "./shift";

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export function normalizeTaskRows(items) {
  const rows = Array.isArray(items) ? items : [];
  const result = [];
  let id = 1;
  for (const row of rows) {
    const date = String(row?.calendar_date ?? row?.calendarDate ?? row?.date ?? "").trim();
    const orderNo = String(row?.order_no ?? row?.orderNo ?? "").trim();
    const processCode = String(row?.process_code ?? row?.processCode ?? "").trim().toUpperCase();
    if (!date || !orderNo || !processCode) {
      continue;
    }
    result.push({
      id: `task-${id++}`,
      date,
      orderNo,
      processCode,
      processName: String(row?.process_name_cn ?? row?.processNameCn ?? processCode).trim() || processCode,
      shiftCode: normalizeShiftCode(row?.shift_code ?? row?.shiftCode),
      planQty: Math.max(0, toNumber(row?.plan_qty ?? row?.planQty ?? row?.scheduled_qty ?? row?.scheduledQty, 0)),
      planStartTime: String(row?.plan_start_time ?? row?.planStartTime ?? "").trim()
    });
  }
  result.sort((a, b) => {
    if (a.date !== b.date) {
      return String(a.date).localeCompare(String(b.date), "zh-Hans-CN");
    }
    if (a.processCode !== b.processCode) {
      return String(a.processCode).localeCompare(String(b.processCode), "zh-Hans-CN");
    }
    if (a.planStartTime !== b.planStartTime) {
      return String(a.planStartTime).localeCompare(String(b.planStartTime), "zh-Hans-CN");
    }
    const byShift = shiftSortIndex(a.shiftCode) - shiftSortIndex(b.shiftCode);
    if (byShift !== 0) {
      return byShift;
    }
    return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
  });
  return result;
}

