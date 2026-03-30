export function mapLiteSnapshotRow(row, deps) {
  const {
    makeId,
    formatSnapshotName,
    normalizeScenario,
    now = Date.now(),
  } = deps;
  return {
    id: String(row?.id || makeId("snapshot")),
    name: String(row?.name || "").trim() || formatSnapshotName(new Date(now)),
    createdAt: Number(row?.createdAt) || now,
    updatedAt: Number(row?.updatedAt) || Number(row?.createdAt) || now,
    scenario: normalizeScenario(row?.scenario),
  };
}

export function sortSnapshotsByUpdatedAtDesc(rows) {
  return [...rows].sort((a, b) => b.updatedAt - a.updatedAt);
}
