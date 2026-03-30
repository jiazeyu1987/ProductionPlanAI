import {
  addDays,
} from "../../utils/liteSchedulerEngine";
import { formatAutoOrderNo, toNumber } from "./numberFormatUtils";
import { makeId } from "./snapshotUtils";

export function buildInsertOrderMutation({ orderId, insertDate }) {
  return (prev) => {
    const targetOrder = prev.orders.find((row) => row.id === orderId);
    if (!targetOrder) {
      return prev;
    }
    const safeInsertDate = insertDate || prev.horizonStart;
    const sortedIds = prev.orders
      .slice()
      .sort(
        (a, b) =>
          (toNumber(a.orderSeq, 0) || 0) - (toNumber(b.orderSeq, 0) || 0),
      )
      .map((row) => row.id);
    const withoutTarget = sortedIds.filter((id) => id !== orderId);
    const reordered = [orderId, ...withoutTarget];
    const seqMap = Object.fromEntries(reordered.map((id, idx) => [id, idx + 1]));
    const nextOrders = prev.orders.map((row) => {
      if (row.id !== orderId) {
        return {
          ...row,
          orderSeq: seqMap[row.id] || toNumber(row.orderSeq, 1),
        };
      }
      return { ...row, releaseDate: safeInsertDate, orderSeq: 1 };
    });
    return {
      ...prev,
      orders: nextOrders,
      locks: prev.locks.filter((lock) => Number(lock.seq) >= 0),
    };
  };
}

export function buildOrderUpsertResult({
  scenario,
  orderModalForm,
  editingOrderId,
  isDurationMode,
  lineNameMap,
}) {
  const inputOrderNo = String(orderModalForm.orderNo || "").trim();
  const productName = String(orderModalForm.productName || "").trim();
  const spec = String(orderModalForm.spec || "").trim();
  const batchNo = String(orderModalForm.batchNo || "").trim();
  const autoOrderNo = formatAutoOrderNo(scenario?.nextOrderSeq || 1);
  const editingOrderNo =
    scenario.orders.find((order) => order.id === editingOrderId)?.orderNo || "";
  const orderNo = inputOrderNo || editingOrderNo || autoOrderNo;
  const dueDate = orderModalForm.dueDate || addDays(scenario.horizonStart, 7);
  const releaseDate = orderModalForm.releaseDate || scenario.horizonStart;

  if (isDurationMode) {
    const linePlanDays = {};
    Object.entries(orderModalForm.linePlanDays || {}).forEach(
      ([lineIdRaw, value]) => {
        const lineId = String(lineIdRaw || "").trim();
        const days = Math.max(0, Math.round(toNumber(value, 0)));
        if (!lineId || !lineNameMap[lineId] || days <= 0) {
          return;
        }
        linePlanDays[lineId] = days;
      },
    );
    const linePlanQuantities = {};
    Object.entries(orderModalForm.linePlanQuantities || {}).forEach(
      ([lineIdRaw, value]) => {
        const lineId = String(lineIdRaw || "").trim();
        const qty = Math.max(0, Math.round(toNumber(value, 0)));
        if (!lineId || !lineNameMap[lineId] || qty <= 0) {
          return;
        }
        linePlanQuantities[lineId] = qty;
      },
    );
    const maxPlanDays = Object.values(linePlanDays).reduce(
      (maxValue, value) => Math.max(maxValue, value),
      0,
    );
    if (maxPlanDays <= 0) {
      return { error: "请至少填写一条产线的计划天数。" };
    }

    if (editingOrderId) {
      return {
        mutator: (prev) => ({
          ...prev,
          orders: prev.orders.map((order) => {
            if (order.id !== editingOrderId) {
              return order;
            }
            return {
              ...order,
              orderNo,
              productName,
              spec,
              batchNo,
              workloadDays: maxPlanDays,
              completedDays: Math.min(toNumber(order.completedDays, 0), maxPlanDays),
              dueDate,
              releaseDate,
              priority: "NORMAL",
              linePlanDays,
              linePlanQuantities,
            };
          }),
        }),
        message: `订单已更新：${orderNo}`,
      };
    }

    return {
      mutator: (prev) => ({
        ...prev,
        nextOrderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))) + 1,
        orders: [
          ...prev.orders,
          {
            id: makeId("order"),
            orderNo,
            productName,
            spec,
            batchNo,
            orderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))),
            workloadDays: maxPlanDays,
            completedDays: 0,
            dueDate,
            releaseDate,
            priority: "NORMAL",
            lineWorkloads: {},
            linePlanDays,
            linePlanQuantities,
          },
        ],
      }),
      message: `订单已新增：${orderNo}`,
    };
  }

  const lineWorkloads = {};
  Object.entries(orderModalForm.lineTotals || {}).forEach(([lineIdRaw, value]) => {
    const lineId = String(lineIdRaw || "").trim();
    const workload = Math.max(0, toNumber(value, 0));
    if (!lineId || !lineNameMap[lineId] || workload <= 0) {
      return;
    }
    lineWorkloads[lineId] = (lineWorkloads[lineId] || 0) + workload;
  });
  const totalWorkload = Object.values(lineWorkloads).reduce(
    (sum, value) => sum + value,
    0,
  );
  if (totalWorkload <= 0) {
    return { error: "请至少填写一条产线的工作量。" };
  }

  if (editingOrderId) {
    return {
      mutator: (prev) => ({
        ...prev,
        orders: prev.orders.map((order) => {
          if (order.id !== editingOrderId) {
            return order;
          }
          return {
            ...order,
            orderNo,
            productName,
            spec,
            batchNo,
            workloadDays: totalWorkload,
            completedDays: Math.min(toNumber(order.completedDays, 0), totalWorkload),
            dueDate,
            releaseDate,
            priority: "NORMAL",
            lineWorkloads,
            linePlanQuantities: {},
          };
        }),
      }),
      message: `订单已更新：${orderNo}`,
    };
  }

  return {
    mutator: (prev) => ({
      ...prev,
      nextOrderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))) + 1,
      orders: [
        ...prev.orders,
        {
          id: makeId("order"),
          orderNo,
          productName,
          spec,
          batchNo,
          orderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))),
          workloadDays: totalWorkload,
          completedDays: 0,
          dueDate,
          releaseDate,
          priority: "NORMAL",
          lineWorkloads,
          linePlanDays: {},
          linePlanQuantities: {},
        },
      ],
    }),
    message: `订单已新增：${orderNo}`,
  };
}

