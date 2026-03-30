import { loadList } from "../../services/api";

export function listOrderPool() {
  return loadList("/internal/v1/internal/order-pool");
}

export function listScheduleVersions() {
  return loadList("/internal/v1/internal/schedule-versions");
}

export function listMesReportings() {
  return loadList("/v1/mes/reportings");
}

export function listScheduleVersionTasks(versionNo) {
  return loadList(
    `/internal/v1/internal/schedule-versions/${versionNo}/tasks`,
  );
}

export function listOrderPoolMaterials(orderNo, refresh = false) {
  const suffix = refresh ? "?refresh=true" : "";
  return loadList(
    `/internal/v1/internal/order-pool/${encodeURIComponent(orderNo)}/materials${suffix}`,
  );
}

export function listMaterialChildrenByParentCode(parentMaterialCode, refresh = false) {
  const suffix = refresh ? "?refresh=true" : "";
  return loadList(
    `/internal/v1/internal/order-pool/materials/${encodeURIComponent(parentMaterialCode)}/children${suffix}`,
  );
}

