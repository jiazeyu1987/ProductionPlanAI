import {
  createDispatchCommand,
  deleteOrderPoolOrder,
  approveDispatchCommand,
  patchOrderPoolOrder,
} from "./ordersPoolCommandClient";
import {
  listOrderPool,
  listScheduleVersions,
  listMesReportings,
  listScheduleVersionTasks,
} from "./ordersPoolQueryClient";

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

export async function fetchOrdersPoolSnapshot() {
  const [poolRes, versionsRes, reportingsRes] = await Promise.all([
    listOrderPool(),
    listScheduleVersions(),
    listMesReportings(),
  ]);

  const versions = versionsRes.items ?? [];
  const referenceVersion = pickReferenceVersion(versions);
  if (!referenceVersion?.version_no) {
    return {
      allRows: poolRes.items ?? [],
      reportings: reportingsRes.items ?? [],
      scheduledOrderNos: [],
    };
  }

  const tasksRes = await listScheduleVersionTasks(referenceVersion.version_no);
  const scheduledOrderNos = [
    ...new Set(
      (tasksRes.items ?? [])
        .map((item) => String(item.order_no || "").trim())
        .filter(Boolean),
    ),
  ];

  return {
    allRows: poolRes.items ?? [],
    reportings: reportingsRes.items ?? [],
    scheduledOrderNos,
  };
}

export async function createAndApproveDispatchCommand({ orderNo, commandType }) {
  const created = await createDispatchCommand({
    command_type: commandType,
    target_order_no: orderNo,
    target_order_type: "production",
    effective_time: new Date().toISOString(),
    reason: `快速操作：${commandType}`,
    created_by: "dispatcher01",
  });
  const commandId = String(created?.command_id || "").trim();
  if (!commandId) {
    throw new Error("创建调度指令失败：缺少指令编号。");
  }
  await approveDispatchCommand(commandId, {
    approver: "dispatcher01",
    decision: "APPROVED",
    decision_reason: `快速操作自动审批：${commandType}`,
    decision_time: new Date().toISOString(),
  });
}

export function patchOrderExpectedStartDate(orderNo, expectedStartDate) {
  return patchOrderPoolOrder(orderNo, { expected_start_date: expectedStartDate });
}

export function markOrderCompleted(orderNo, row) {
  const qty = Number(row?.order_qty);
  const safeQty = Number.isFinite(qty) && qty > 0 ? qty : 0;
  const productCode = String(row?.product_code || "").trim();
  const items = productCode
    ? [
        {
          productCode,
          qty: safeQty,
          completedQty: safeQty,
        },
      ]
    : [];

  return patchOrderPoolOrder(orderNo, {
    status: "DONE",
    order_status: "DONE",
    workshop_completed_qty: safeQty,
    outer_completed_qty: safeQty,
    items,
  });
}

export function deleteOrder(orderNo) {
  return deleteOrderPoolOrder(orderNo);
}
