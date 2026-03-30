import { formatDateTimeByField } from "../../utils/datetime";

export function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

export function formatQty(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return n.toFixed(2);
}

export function formatSignedQty(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n) < 1e-9) {
    return "0";
  }
  const sign = n > 0 ? "+" : "-";
  return `${sign}${formatQty(Math.abs(n))}`;
}

export function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return `${Math.round(n)}%`;
  }
  return `${n.toFixed(2)}%`;
}

export function formatDiffForView(value, field = "") {
  if (Array.isArray(value)) {
    return value.map((item) => formatDiffForView(item, field));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, formatDiffForView(item, key)]));
  }
  return formatDateTimeByField(value, field);
}

