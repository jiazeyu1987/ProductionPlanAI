import {
  mapLiteSnapshotRow,
  sortSnapshotsByUpdatedAtDesc,
} from "./mappers";

export const LITE_SCENARIO_STORAGE_KEY = "liteScheduler.scenario.v1";
export const LITE_SNAPSHOT_STORAGE_KEY = "liteScheduler.scenario.snapshots.v1";

export function readLiteScenario({ createDefaultScenario, normalizeScenario }) {
  if (typeof window === "undefined") {
    return createDefaultScenario();
  }
  try {
    const raw = window.localStorage.getItem(LITE_SCENARIO_STORAGE_KEY);
    if (!raw) {
      return createDefaultScenario();
    }
    return normalizeScenario(JSON.parse(raw));
  } catch {
    return createDefaultScenario();
  }
}

export function writeLiteScenario(scenario) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(
    LITE_SCENARIO_STORAGE_KEY,
    JSON.stringify(scenario),
  );
}

export function readLiteSnapshots({
  makeId,
  formatSnapshotName,
  normalizeScenario,
}) {
  if (typeof window === "undefined") {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(LITE_SNAPSHOT_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    const mapped = parsed.map((row) =>
      mapLiteSnapshotRow(row, {
        makeId,
        formatSnapshotName,
        normalizeScenario,
      }),
    );
    return sortSnapshotsByUpdatedAtDesc(mapped);
  } catch {
    return [];
  }
}

export function writeLiteSnapshots(rows) {
  if (typeof window === "undefined") {
    return;
  }
  const safeRows = Array.isArray(rows) ? rows : [];
  window.localStorage.setItem(
    LITE_SNAPSHOT_STORAGE_KEY,
    JSON.stringify(safeRows),
  );
}
