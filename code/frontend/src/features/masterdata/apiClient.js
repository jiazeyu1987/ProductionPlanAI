import { loadList, postContract } from "../../services/api";

export function listProcessRoutes() {
  return loadList("/v1/mes/process-routes");
}

export function getMasterdataConfig() {
  return loadList("/internal/v1/internal/masterdata/config");
}

export function saveMasterdataConfig(payload) {
  return postContract("/internal/v1/internal/masterdata/config", payload);
}

export function getScheduleCalendarRules() {
  return loadList("/internal/v1/internal/schedule-calendar/rules");
}

export function saveScheduleCalendarRules(payload) {
  return postContract("/internal/v1/internal/schedule-calendar/rules", payload);
}

export function listOrderMaterialAvailability(refresh = false) {
  const suffix = refresh ? "?refresh=true" : "";
  return loadList(`/internal/v1/internal/material-availability/orders${suffix}`);
}

export function listLegacySchedules() {
  return loadList("/api/schedules");
}

export function createMasterdataRoute(payload) {
  return postContract("/internal/v1/internal/masterdata/routes/create", payload);
}

export function updateMasterdataRoute(payload) {
  return postContract("/internal/v1/internal/masterdata/routes/update", payload);
}

export function copyMasterdataRoute(payload) {
  return postContract("/internal/v1/internal/masterdata/routes/copy", payload);
}

export function deleteMasterdataRoute(payload) {
  return postContract("/internal/v1/internal/masterdata/routes/delete", payload);
}
