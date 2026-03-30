function pad2(n) {
  return String(n).padStart(2, "0");
}

export function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function formatSnapshotName(date = new Date()) {
  const year = date.getFullYear();
  const month = pad2(date.getMonth() + 1);
  const day = pad2(date.getDate());
  const hour = pad2(date.getHours());
  const minute = pad2(date.getMinutes());
  const second = pad2(date.getSeconds());
  return `${year}-${month}-${day} ${hour}-${minute}-${second}`;
}

export function formatSnapshotDisplay(dateMs) {
  const n = Number(dateMs);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return formatSnapshotName(new Date(n));
}
