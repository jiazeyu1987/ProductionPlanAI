import {
  createDefaultLiteScenario,
  normalizeLiteScenario,
} from "../../utils/liteSchedulerEngine";
import { formatSnapshotName, makeId } from "./snapshotUtils";
import {
  readLiteScenario,
  readLiteSnapshots,
  writeLiteScenario,
  writeLiteSnapshots,
} from "./storage";

export function loadLiteScenario() {
  return readLiteScenario({
    createDefaultScenario: createDefaultLiteScenario,
    normalizeScenario: normalizeLiteScenario,
  });
}

export function saveLiteScenario(scenario) {
  writeLiteScenario(scenario);
}

export function loadLiteSnapshots() {
  return readLiteSnapshots({
    makeId,
    formatSnapshotName,
    normalizeScenario: normalizeLiteScenario,
  });
}

export function saveLiteSnapshots(rows) {
  writeLiteSnapshots(rows);
}

