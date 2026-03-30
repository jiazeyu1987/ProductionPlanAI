export const EPSILON = 1e-6;

export const PLANNING_MODE = Object.freeze({
  QTY_CAPACITY: "QTY_CAPACITY",
  DURATION_MANUAL_FINISH: "DURATION_MANUAL_FINISH",
});

export function clampNumber(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return fallback;
  }
  return n;
}

export function round3(value) {
  return Math.round(value * 1000) / 1000;
}

export function positiveOr(value, fallback = 0) {
  return Math.max(0, round3(clampNumber(value, fallback)));
}

export function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function makeLineOrderKey(lineId, orderId) {
  return `${String(lineId || "").trim()}|${String(orderId || "").trim()}`;
}

