import { downloadContractFile, loadList, postContract } from "../../services/api";

export function listIntegrationInbox() {
  return loadList("/internal/v1/internal/integration/inbox");
}

export function listIntegrationOutbox() {
  return loadList("/internal/v1/internal/integration/outbox");
}

export function retryIntegrationOutbox(messageId, payload = {}) {
  return postContract(`/internal/v1/internal/integration/outbox/${messageId}/retry`, payload);
}

export function listWorkshopWeeklyPlan(versionNo = "") {
  const query = versionNo ? `?version_no=${encodeURIComponent(versionNo)}` : "";
  return loadList(`/v1/reports/workshop-weekly-plan${query}`);
}

export function listWorkshopMonthlyPlan(versionNo = "") {
  const query = versionNo ? `?version_no=${encodeURIComponent(versionNo)}` : "";
  return loadList(`/v1/reports/workshop-monthly-plan${query}`);
}

export function exportWorkshopWeeklyPlan(versionNo = "") {
  const query = versionNo ? `?version_no=${encodeURIComponent(versionNo)}` : "";
  return downloadContractFile(`/v1/reports/workshop-weekly-plan/export${query}`, "workshop-weekly-plan.xlsx");
}

export function exportWorkshopMonthlyPlan(versionNo = "") {
  const query = versionNo ? `?version_no=${encodeURIComponent(versionNo)}` : "";
  return downloadContractFile(`/v1/reports/workshop-monthly-plan/export${query}`, "workshop-monthly-plan.xls");
}

export function listMesEquipments() {
  return loadList("/v1/mes/equipments");
}

export function listPlanReportScheduleVersions() {
  return loadList("/internal/v1/internal/schedule-versions");
}

export function listPlanReportOrderPool() {
  return loadList("/internal/v1/internal/order-pool");
}
