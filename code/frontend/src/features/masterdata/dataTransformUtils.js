import { normalizeCompanyCode, normalizeProcessCode } from "./codeNormalizeUtils";
import { normalizeDateShiftModeByDate, normalizeWeekendRestMode } from "./dateShiftModeUtils";

export function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

export function parseConfigResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  const processConfigs = Array.isArray(body.process_configs) ? body.process_configs : [];
  const lineTopology = Array.isArray(body.line_topology) ? body.line_topology : [];
  const sectionLeaderBindings = Array.isArray(body.section_leader_bindings) ? body.section_leader_bindings : [];
  const resourcePool = Array.isArray(body.resource_pool) ? body.resource_pool : [];
  const materialAvailability = Array.isArray(body.material_availability) ? body.material_availability : [];
  const skipStatutoryHolidays = body.skip_statutory_holidays === true || body.skipStatutoryHolidays === true;
  const weekendRestMode = normalizeWeekendRestMode(body.weekend_rest_mode ?? body.weekendRestMode);
  const dateShiftModeByDate = normalizeDateShiftModeByDate(body.date_shift_mode_by_date ?? body.dateShiftModeByDate);
  const normalizedLineTopology = lineTopology.map((row) => ({
    ...row,
    company_code: normalizeCompanyCode(row.company_code ?? row.companyCode),
    workshop_code: String(row.workshop_code ?? row.workshopCode ?? "").trim().toUpperCase(),
    line_code: String(row.line_code ?? row.lineCode ?? "").trim().toUpperCase(),
    line_name: String(row.line_name ?? row.lineName ?? "").trim(),
    process_code: normalizeProcessCode(row.process_code ?? row.processCode),
    capacity_per_shift: row.capacity_per_shift ?? row.capacityPerShift ?? "",
    required_workers: row.required_workers ?? row.requiredWorkers ?? "",
    required_machines: row.required_machines ?? row.requiredMachines ?? "",
    enabled_flag: toInt(row.enabled_flag ?? row.enabledFlag ?? row.enabled ?? 1, 1) === 1 ? 1 : 0,
  }));
  const normalizedSectionLeaderBindings = sectionLeaderBindings.map((row) => ({
    ...row,
    company_code: normalizeCompanyCode(row.company_code ?? row.companyCode),
  }));
  return {
    processConfigs,
    lineTopology: normalizedLineTopology,
    sectionLeaderBindings: normalizedSectionLeaderBindings,
    resourcePool,
    materialAvailability,
    horizonStartDate: body.horizon_start_date || "",
    horizonDays: toInt(body.horizon_days, 0),
    shiftsPerDay: 2,
    skipStatutoryHolidays,
    weekendRestMode,
    dateShiftModeByDate,
  };
}

export function parseScheduleCalendarRulesResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  return {
    horizonStartDate: body.horizon_start_date || "",
    horizonDays: toInt(body.horizon_days, 0),
    skipStatutoryHolidays: body.skip_statutory_holidays === true || body.skipStatutoryHolidays === true,
    weekendRestMode: normalizeWeekendRestMode(body.weekend_rest_mode ?? body.weekendRestMode),
    dateShiftModeByDate: normalizeDateShiftModeByDate(body.date_shift_mode_by_date ?? body.dateShiftModeByDate),
  };
}

export function firstDateFromRows(rows) {
  const dates = [...new Set((rows || []).map((row) => String(row?.calendar_date || "").trim()).filter(Boolean))].sort();
  return dates.length > 0 ? dates[0] : "";
}

export function firstDateFromConfig(configData) {
  return firstDateFromRows([
    ...(configData?.resourcePool || []),
    ...(configData?.materialAvailability || []),
  ]);
}

export function normalizeVersionNo(row) {
  return String(row?.version_no || row?.versionNo || "").trim();
}

export function pickCalendarSourceSchedule(items) {
  const schedules = Array.isArray(items) ? items : [];
  if (schedules.length === 0) {
    return null;
  }
  const published = schedules.filter((row) => String(row?.status || "").trim().toUpperCase() === "PUBLISHED");
  if (published.length > 0) {
    return published[published.length - 1];
  }
  return schedules[schedules.length - 1];
}

export function buildCalendarOrderDigestByDate(schedule) {
  if (!schedule || !Array.isArray(schedule.allocations)) {
    return {};
  }
  const qtyByDateOrder = new Map();
  for (const row of schedule.allocations) {
    const date = String(row?.date || "").trim();
    const orderNo = String(row?.orderNo || row?.order_no || "").trim();
    const scheduledQty = Math.max(0, toNumber(row?.scheduledQty ?? row?.scheduled_qty, 0));
    if (!date || !orderNo || scheduledQty <= 1e-9) {
      continue;
    }
    const key = `${date}#${orderNo}`;
    qtyByDateOrder.set(key, (qtyByDateOrder.get(key) || 0) + scheduledQty);
  }

  const out = {};
  for (const [key, qty] of qtyByDateOrder.entries()) {
    const splitIndex = key.indexOf("#");
    if (splitIndex < 0) {
      continue;
    }
    const date = key.slice(0, splitIndex);
    const orderNo = key.slice(splitIndex + 1);
    if (!out[date]) {
      out[date] = [];
    }
    out[date].push({ orderNo, qty });
  }

  for (const [date, rows] of Object.entries(out)) {
    out[date] = rows
      .slice()
      .sort((a, b) => {
        const byQty = toNumber(b.qty, 0) - toNumber(a.qty, 0);
        if (Math.abs(byQty) > 1e-9) {
          return byQty;
        }
        return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
      });
  }

  return out;
}
