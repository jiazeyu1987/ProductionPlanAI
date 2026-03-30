import { listIntegrationOutbox, listMesEquipments } from "../integration/apiClient";
import {
  listDashboardOpenAlerts,
  listDashboardOrderPool,
  listDashboardScheduleVersions,
  listDashboardShiftProcessLoad,
} from "./apiClient";

function pickReferenceVersion(versions) {
  if (!versions || versions.length === 0) {
    return null;
  }
  const published = versions.filter((item) => item.status === "PUBLISHED");
  if (published.length > 0) {
    return published[published.length - 1];
  }
  return versions[versions.length - 1];
}

export async function fetchDashboardSnapshot() {
  const [orders, alerts, versions, outbox, equipmentRes] = await Promise.all([
    listDashboardOrderPool(),
    listDashboardOpenAlerts(),
    listDashboardScheduleVersions(),
    listIntegrationOutbox(),
    listMesEquipments(),
  ]);

  const versionItems = versions.items ?? [];
  const referenceVersion = pickReferenceVersion(versionItems);
  let shiftLoadRows = [];
  let versionNo = "";
  let versionStatus = "";
  if (referenceVersion?.version_no) {
    const shiftLoad = await listDashboardShiftProcessLoad(referenceVersion.version_no);
    shiftLoadRows = shiftLoad.items ?? [];
    versionNo = referenceVersion.version_no;
    versionStatus = referenceVersion.status_name_cn || referenceVersion.status || "";
  }

  return {
    stats: {
      orderPool: orders.total,
      openAlerts: alerts.total,
      versions: versions.total,
      outboxFailed: (outbox.items ?? []).filter((x) => x.status !== "SUCCESS").length,
    },
    mustHandle: (alerts.items ?? []).slice(0, 6),
    shiftProcessLoads: shiftLoadRows,
    equipments: equipmentRes.items ?? [],
    todayProcessMeta: {
      versionNo,
      versionStatus,
    },
  };
}

