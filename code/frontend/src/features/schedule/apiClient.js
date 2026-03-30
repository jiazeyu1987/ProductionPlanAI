import { loadList, postContract, postLegacy } from "../../services/api";

export function listScheduleVersions() {
  return loadList("/internal/v1/internal/schedule-versions");
}

export function listScheduleVersionTasks(versionNo) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/tasks`,
  );
}

export function listScheduleVersionAlgorithm(versionNo) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/algorithm`,
  );
}

export function listScheduleVersionDiff(versionNo, compareWith) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/diff?compare_with=${encodeURIComponent(compareWith)}`,
  );
}

export function listLegacySchedules() {
  return loadList("/api/schedules");
}

export function generateLegacySchedule(payload) {
  return postLegacy("/api/schedules/generate", payload);
}

export function listOrderPool() {
  return loadList("/internal/v1/internal/order-pool");
}

export function listScheduleVersionDailyProcessLoad(versionNo) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/daily-process-load`,
  );
}

export function publishScheduleVersion(versionNo, payload) {
  return postContract(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/publish`,
    payload,
  );
}

export function rollbackScheduleVersion(versionNo, payload) {
  return postContract(
    `/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/rollback`,
    payload,
  );
}

export function getMasterdataConfig() {
  return loadList("/internal/v1/internal/masterdata/config");
}

export function getScheduleCalendarRules() {
  return loadList("/internal/v1/internal/schedule-calendar/rules");
}

export function saveScheduleCalendarRules(payload) {
  return postContract("/internal/v1/internal/schedule-calendar/rules", payload);
}
