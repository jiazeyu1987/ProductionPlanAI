import {
  normalizeCodeText,
  normalizeDateShiftModeByDate,
  normalizeWeekendRestMode
} from "../schedule/calendarModeUtils";

const TASK_BORDER_CLASS_NAMES = [
  "lite-cal-task-border-0",
  "lite-cal-task-border-1",
  "lite-cal-task-border-2",
  "lite-cal-task-border-3",
  "lite-cal-task-border-4",
  "lite-cal-task-border-5"
];

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export function normalizeVersionNo(row) {
  return String(row?.version_no || row?.versionNo || "").trim();
}

export function normalizeStrategyCode(value) {
  const normalized = String(value || "")
    .trim()
    .toUpperCase();
  if (normalized === "MAX_CAPACITY_FIRST") {
    return "MAX_CAPACITY_FIRST";
  }
  if (normalized === "MIN_DELAY_FIRST") {
    return "MIN_DELAY_FIRST";
  }
  return "KEY_ORDER_FIRST";
}

export function pickPreferredVersionNo(rows) {
  const items = Array.isArray(rows) ? rows : [];
  if (items.length === 0) {
    return "";
  }
  const published = items.filter(
    (row) => String(row?.status || "").trim().toUpperCase() === "PUBLISHED"
  );
  const target = published.length > 0 ? published[published.length - 1] : items[items.length - 1];
  return normalizeVersionNo(target);
}

function normalizeShiftCode(shiftCode) {
  const normalized = String(shiftCode || "")
    .trim()
    .toUpperCase();
  if (normalized === "D") {
    return "DAY";
  }
  if (normalized === "N") {
    return "NIGHT";
  }
  return normalized || "DAY";
}

export function shiftName(shiftCode) {
  const normalized = normalizeShiftCode(shiftCode);
  if (normalized === "NIGHT") {
    return "夜班";
  }
  return "白班";
}

export function formatCompactQty(value) {
  const n = toNumber(value, 0);
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return n.toFixed(2);
}

function resolveTaskColorIndex(taskId) {
  const text = String(taskId || "").trim();
  if (!text) {
    return 0;
  }
  let hash = 0;
  for (let idx = 0; idx < text.length; idx += 1) {
    hash = (hash * 31 + text.charCodeAt(idx)) % 2147483647;
  }
  return Math.abs(hash) % TASK_BORDER_CLASS_NAMES.length;
}

export function resolveTaskBorderClass(taskId) {
  return TASK_BORDER_CLASS_NAMES[resolveTaskColorIndex(taskId)];
}

export function parseMasterdataConfigResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  const lineTopology = Array.isArray(body.line_topology)
    ? body.line_topology
    : Array.isArray(body.lineTopology)
      ? body.lineTopology
      : [];
  const processConfigs = Array.isArray(body.process_configs)
    ? body.process_configs
    : Array.isArray(body.processConfigs)
      ? body.processConfigs
      : [];
  return {
    lineTopology,
    processConfigs
  };
}

export function parseCalendarRulesResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  return {
    skipStatutoryHolidays: body.skip_statutory_holidays === true || body.skipStatutoryHolidays === true,
    weekendRestMode: normalizeWeekendRestMode(body.weekend_rest_mode ?? body.weekendRestMode),
    dateShiftModeByDate: normalizeDateShiftModeByDate(body.date_shift_mode_by_date ?? body.dateShiftModeByDate)
  };
}

export function buildMasterdataTopologyHref(line) {
  const params = new URLSearchParams();
  params.set("tab", "config");
  params.set("config_sub", "topology");
  params.set("topology_view", "friendly");
  const companyCode = normalizeCodeText(line?.companyCode || "COMPANY-MAIN");
  const workshopCode = String(line?.workshopCode || "").trim();
  const lineCode = String(line?.lineCode || "")
    .trim()
    .toUpperCase();
  if (companyCode) {
    params.set("company_code", companyCode);
  }
  if (workshopCode) {
    params.set("workshop_code", workshopCode);
  }
  if (lineCode) {
    params.set("line_code", lineCode);
  }
  return `/masterdata?${params.toString()}`;
}

