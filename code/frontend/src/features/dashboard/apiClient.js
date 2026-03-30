import { loadList } from "../../services/api";

export function listDashboardOrderPool() {
  return loadList("/internal/v1/internal/order-pool");
}

export function listDashboardOpenAlerts() {
  return loadList("/internal/v1/internal/alerts?status=OPEN");
}

export function listDashboardScheduleVersions() {
  return loadList("/internal/v1/internal/schedule-versions");
}

export function listDashboardShiftProcessLoad(versionNo) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/shift-process-load`,
  );
}

