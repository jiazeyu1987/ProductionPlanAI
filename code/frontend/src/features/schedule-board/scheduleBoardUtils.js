const REASON_TEXT = {
  NONE: "无",
  CAPACITY_MANPOWER: "人力不足",
  CAPACITY_MACHINE: "设备不足",
  CAPACITY_UNKNOWN: "综合产能不足",
  MATERIAL_SHORTAGE: "物料不足",
  DEPENDENCY_BLOCKED: "前序工序未释放",
  FROZEN_BY_POLICY: "订单冻结策略",
  LOCKED_PRESERVED: "锁单保留基线",
  UNKNOWN: "未识别原因",
};

const DIMENSION_TEXT = {
  NONE: "无",
  CAPACITY: "人机产能",
  MATERIAL: "物料",
  DEPENDENCY: "工序依赖",
  POLICY: "策略约束",
  UNKNOWN: "未识别",
};

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function formatQty(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return n.toFixed(2);
}

function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return `${Math.round(n)}%`;
  }
  return `${n.toFixed(2)}%`;
}

function normalizeVersionNo(row) {
  return String(row?.version_no || row?.versionNo || "");
}

function normalizeReasonCode(code) {
  const normalized = String(code || "").trim().toUpperCase();
  if (!normalized) {
    return "UNKNOWN";
  }
  if (normalized === "CAPACITY_LIMIT") {
    return "CAPACITY_UNKNOWN";
  }
  if (normalized === "DEPENDENCY_LIMIT") {
    return "DEPENDENCY_BLOCKED";
  }
  return normalized;
}

function reasonToText(code) {
  const normalized = normalizeReasonCode(code);
  return REASON_TEXT[normalized] || normalized;
}

function inferDimensionByReason(reasonCode) {
  const normalized = normalizeReasonCode(reasonCode);
  if (normalized.startsWith("CAPACITY_")) {
    return "CAPACITY";
  }
  if (normalized === "MATERIAL_SHORTAGE") {
    return "MATERIAL";
  }
  if (normalized === "DEPENDENCY_BLOCKED") {
    return "DEPENDENCY";
  }
  if (normalized === "FROZEN_BY_POLICY" || normalized === "LOCKED_PRESERVED") {
    return "POLICY";
  }
  return "UNKNOWN";
}

function dimensionToText(code) {
  const normalized = String(code || "").trim().toUpperCase();
  return DIMENSION_TEXT[normalized] || DIMENSION_TEXT.UNKNOWN;
}

function topCode(counter) {
  const entries = Object.entries(counter || {});
  if (entries.length === 0) {
    return "";
  }
  entries.sort((a, b) => {
    if (b[1] !== a[1]) {
      return b[1] - a[1];
    }
    return String(a[0]).localeCompare(String(b[0]));
  });
  return entries[0][0];
}

function findReasonCode(row) {
  const direct = normalizeReasonCode(
    row?.reason_code ||
      row?.reasonCode ||
      row?.last_block_reason ||
      row?.lastBlockReason ||
      "",
  );
  if (direct !== "UNKNOWN") {
    return direct;
  }
  if (Array.isArray(row?.reasons) && row.reasons.length > 0) {
    return normalizeReasonCode(row.reasons[0]);
  }
  return "UNKNOWN";
}

function buildOrderAllocationRows(schedule, orderItems, taskItems = []) {
  const allocations = Array.isArray(schedule?.allocations) ? schedule.allocations : [];
  const unscheduled = Array.isArray(schedule?.unscheduled) ? schedule.unscheduled : [];
  const orderMetaByNo = new Map();
  for (const item of orderItems || []) {
    const key = String(item?.order_no || item?.production_order_no || "").trim();
    if (!key) {
      continue;
    }
    orderMetaByNo.set(key, item);
  }
  for (const task of taskItems || []) {
    const orderNo = String(task?.order_no || "").trim();
    if (!orderNo || orderMetaByNo.has(orderNo)) {
      continue;
    }
    orderMetaByNo.set(orderNo, {
      order_no: orderNo,
      urgent_flag: Number(task?.priority) === 1 ? 1 : 0,
      lock_flag: Number(task?.lock_flag) === 1 ? 1 : 0,
    });
  }
  const map = new Map();

  function ensure(orderNo) {
    if (!map.has(orderNo)) {
      map.set(orderNo, {
        id: orderNo,
        order_no: orderNo,
        scheduled_qty: 0,
        unscheduled_qty: 0,
        workers_used: 0,
        machines_used: 0,
        resource_units: 0,
        allocation_count: 0,
        unscheduled_task_count: 0,
        reason_counter: {},
        dimension_counter: {},
      });
    }
    return map.get(orderNo);
  }

  for (const row of allocations) {
    const orderNo = String(row?.orderNo || row?.order_no || "").trim();
    if (!orderNo) {
      continue;
    }
    const item = ensure(orderNo);
    const scheduledQty = toNumber(row?.scheduledQty ?? row?.scheduled_qty);
    const workers = Math.max(0, toNumber(row?.workersUsed ?? row?.workers_used));
    const machines = Math.max(0, toNumber(row?.machinesUsed ?? row?.machines_used));
    item.scheduled_qty += scheduledQty;
    item.workers_used += workers;
    item.machines_used += machines;
    item.resource_units += workers + machines;
    item.allocation_count += 1;
  }

  for (const row of unscheduled) {
    const orderNo = String(row?.orderNo || row?.order_no || "").trim();
    if (!orderNo) {
      continue;
    }
    const item = ensure(orderNo);
    const remaining = Math.max(0, toNumber(row?.remainingQty ?? row?.remaining_qty));
    const reasonCode = findReasonCode(row);
    const blockingDimension = String(
      row?.blocking_dimension ||
        row?.blockingDimension ||
        inferDimensionByReason(reasonCode),
    ).toUpperCase();
    item.unscheduled_qty += remaining;
    item.unscheduled_task_count += 1;
    item.reason_counter[reasonCode] = (item.reason_counter[reasonCode] || 0) + 1;
    item.dimension_counter[blockingDimension] = (item.dimension_counter[blockingDimension] || 0) + 1;
  }

  const rows = [...map.values()];
  const totalResourceUnits = rows.reduce((sum, item) => sum + item.resource_units, 0);
  const totalScheduledQty = rows.reduce((sum, item) => sum + item.scheduled_qty, 0);

  return rows
    .map((item) => {
      const hasUnscheduled = item.unscheduled_qty > 1e-9;
      const topReasonCode = hasUnscheduled ? topCode(item.reason_counter) || "UNKNOWN" : "NONE";
      const topDimension = hasUnscheduled ? topCode(item.dimension_counter) || "UNKNOWN" : "NONE";
      const meta = orderMetaByNo.get(item.order_no);
      const priorityLabel = Number(meta?.urgent_flag) === 1 ? "加急订单" : "常规订单";
      const lockLabel = Number(meta?.lock_flag) === 1 ? "是" : "否";
      const dueDate = meta?.promised_due_date || meta?.expected_due_date || "-";
      const resourceShareRaw =
        totalResourceUnits > 1e-9
          ? (item.resource_units / totalResourceUnits) * 100
          : totalScheduledQty > 1e-9
            ? (item.scheduled_qty / totalScheduledQty) * 100
            : 0;
      const qtyShareRaw =
        totalScheduledQty > 1e-9 ? (item.scheduled_qty / totalScheduledQty) * 100 : 0;

      let explain = `按当前策略，${priorityLabel}（交期 ${dueDate}）在本版草稿中获得了对应资源分配。`;
      if (item.scheduled_qty <= 1e-9 && item.unscheduled_qty > 1e-9) {
        explain += ` 当前未分配到可执行资源，主要瓶颈是“${reasonToText(topReasonCode)} / ${dimensionToText(topDimension)}”。`;
      } else if (item.unscheduled_qty > 1e-9) {
        explain +=
          ` 已分配 ${formatQty(item.scheduled_qty)}，资源占比 ${formatPercent(resourceShareRaw)}，` +
          `仍有 ${formatQty(item.unscheduled_qty)} 未排，主要瓶颈是“${reasonToText(topReasonCode)} / ${dimensionToText(topDimension)}”。`;
      } else {
        explain +=
          ` 已分配 ${formatQty(item.scheduled_qty)}，资源占比 ${formatPercent(resourceShareRaw)}，` +
          "当前没有未排任务。";
      }

      return {
        ...item,
        priority_label: priorityLabel,
        lock_label: lockLabel,
        due_date: dueDate,
        resource_share: resourceShareRaw,
        qty_share: qtyShareRaw,
        bottleneck_reason_code: topReasonCode,
        bottleneck_reason_text: reasonToText(topReasonCode),
        bottleneck_dimension: topDimension,
        bottleneck_dimension_text: dimensionToText(topDimension),
        explain_cn: explain,
      };
    })
    .sort((a, b) => {
      const byResource = b.resource_share - a.resource_share;
      if (Math.abs(byResource) > 1e-9) {
        return byResource;
      }
      const byScheduled = b.scheduled_qty - a.scheduled_qty;
      if (Math.abs(byScheduled) > 1e-9) {
        return byScheduled;
      }
      return String(a.order_no).localeCompare(String(b.order_no), "zh-Hans-CN");
    });
}

const STRATEGY_OPTIONS = [
  { code: "KEY_ORDER_FIRST", label: "关键订单优先" },
  { code: "MAX_CAPACITY_FIRST", label: "最大产能优先" },
  { code: "MIN_DELAY_FIRST", label: "交期最小延期优先" },
];

function normalizeStrategyCode(value) {
  const raw = String(value || "").trim().toUpperCase();
  return STRATEGY_OPTIONS.some((item) => item.code === raw) ? raw : "KEY_ORDER_FIRST";
}

function strategyLabel(value) {
  const code = normalizeStrategyCode(value);
  return STRATEGY_OPTIONS.find((item) => item.code === code)?.label || code;
}

function buildBoardSummary(orderAllocationRows) {
  if (!Array.isArray(orderAllocationRows) || orderAllocationRows.length === 0) {
    return null;
  }
  const totalScheduledQty = orderAllocationRows.reduce(
    (sum, row) => sum + toNumber(row.scheduled_qty),
    0,
  );
  const totalUnscheduledQty = orderAllocationRows.reduce(
    (sum, row) => sum + toNumber(row.unscheduled_qty),
    0,
  );
  const reasonCounter = {};
  for (const row of orderAllocationRows) {
    const key = row.bottleneck_reason_code || "UNKNOWN";
    if (toNumber(row.unscheduled_qty) <= 1e-9) {
      continue;
    }
    reasonCounter[key] = (reasonCounter[key] || 0) + 1;
  }
  const topReasonCode = totalUnscheduledQty > 1e-9 ? topCode(reasonCounter) || "UNKNOWN" : "NONE";
  return {
    orderCount: orderAllocationRows.length,
    totalScheduledQty,
    totalUnscheduledQty,
    topReasonCode,
  };
}

export {
  STRATEGY_OPTIONS,
  buildBoardSummary,
  buildOrderAllocationRows,
  dimensionToText,
  findReasonCode,
  formatPercent,
  formatQty,
  inferDimensionByReason,
  normalizeReasonCode,
  normalizeStrategyCode,
  normalizeVersionNo,
  reasonToText,
  strategyLabel,
  toNumber,
  topCode,
};

