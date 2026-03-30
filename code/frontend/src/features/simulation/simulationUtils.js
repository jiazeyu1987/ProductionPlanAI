export const TAB_BATCH = "batch";
export const TAB_MANUAL = "manual";
export const SHOW_BATCH_SIMULATION = false;

export const scenarioOptions = [
  { value: "STABLE", label: "稳定" },
  { value: "TIGHT", label: "紧张" },
  { value: "BREAKDOWN", label: "故障" }
];

export function toNumber(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export function formatClientLocalDate(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

