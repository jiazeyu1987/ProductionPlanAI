export function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export function formatNumber(value, digits = 1) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return n.toFixed(digits);
}

export function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return `${(n * 100).toFixed(1)}%`;
}

export function formatExportWorkload(value, durationMode) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "";
  }
  if (durationMode) {
    return String(Math.max(0, Math.round(n)));
  }
  const rounded = Math.round(n * 1000) / 1000;
  return String(rounded);
}

export function formatAutoOrderNo(seqValue) {
  const seq = Math.max(1, Math.round(toNumber(seqValue, 1)));
  return `PO-${String(seq).padStart(4, "0")}`;
}
