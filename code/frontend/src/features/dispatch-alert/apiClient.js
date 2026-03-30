import { loadList, postContract } from "../../services/api";

export function listDispatchCommands() {
  return loadList("/internal/v1/internal/dispatch-commands");
}

export function createDispatchCommand(payload) {
  return postContract("/internal/v1/internal/dispatch-commands", payload);
}

export function approveDispatchCommand(commandId, payload) {
  return postContract(`/internal/v1/internal/dispatch-commands/${commandId}/approvals`, payload);
}

export function listAlerts(status) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return loadList(`/internal/v1/internal/alerts${qs}`);
}

export function ackAlert(alertId, payload) {
  return postContract(`/internal/v1/internal/alerts/${alertId}/ack`, payload);
}

export function closeAlert(alertId, payload) {
  return postContract(`/internal/v1/internal/alerts/${alertId}/close`, payload);
}

export function listScheduleVersionTasks(versionNo) {
  return loadList(`/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/tasks`);
}

export function listMesReportings() {
  return loadList("/v1/mes/reportings");
}

export function listAuditLogs(requestId) {
  const qs = requestId ? `?request_id=${encodeURIComponent(requestId)}` : "";
  return loadList(`/internal/v1/internal/audit-logs${qs}`);
}

export function getSimulationState() {
  return loadList("/internal/v1/internal/simulation/state");
}

export function listSimulationEvents() {
  return loadList("/internal/v1/internal/simulation/events?page=1&page_size=100");
}

export function runSimulation(payload) {
  return postContract("/internal/v1/internal/simulation/run", payload);
}

export function resetSimulation(payload) {
  return postContract("/internal/v1/internal/simulation/reset", payload);
}

export function addManualProductionOrder(payload = {}) {
  return postContract("/internal/v1/internal/simulation/manual/add-production-order", payload);
}

export function advanceSimulationOneDay(payload) {
  return postContract("/internal/v1/internal/simulation/manual/advance-day", payload);
}

export function resetManualSimulation(payload = {}) {
  return postContract("/internal/v1/internal/simulation/manual/reset", payload);
}
