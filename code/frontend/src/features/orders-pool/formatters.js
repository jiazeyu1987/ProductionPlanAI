function toIntText(value) {
  const n = Number(value);
  return Number.isFinite(n) ? String(Math.round(n)) : "-";
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function isInProgressStatus(status) {
  return String(status || "").toUpperCase() === "IN_PROGRESS";
}

function commandLabel(commandType) {
  if (commandType === "LOCK") {
    return "锁单";
  }
  if (commandType === "UNLOCK") {
    return "解锁";
  }
  if (commandType === "PRIORITY") {
    return "提优先级";
  }
  if (commandType === "UNPRIORITY") {
    return "解除优先级";
  }
  return commandType;
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

function progressText(row) {
  const total = toNumber(row?.order_qty);
  const completed = toNumber(row?.completed_qty);
  const rateRaw = Number(row?.progress_rate);
  const rate = Number.isFinite(rateRaw)
    ? Math.max(0, Math.min(100, rateRaw))
    : total > 1e-9
      ? (completed / total) * 100
      : 0;
  return `${toIntText(completed)} / ${toIntText(total)}（${formatPercent(rate)}）`;
}

function isFinalProcess(reporting) {
  const code = String(
    reporting?.process_code || reporting?.processCode || "",
  ).toUpperCase();
  if (code === "PROC_STERILE" || code.includes("STERILE")) {
    return true;
  }
  const name = String(
    reporting?.process_name_cn || reporting?.process_name || "",
  );
  return name.includes("灭菌");
}

function materialSupplyTypeName(row) {
  const name = String(row?.child_material_supply_type_name_cn || "").trim();
  if (name) {
    return name;
  }
  const type = String(row?.child_material_supply_type || "").toUpperCase();
  if (type === "SELF_MADE") {
    return "自制";
  }
  if (type === "PURCHASED") {
    return "外购";
  }
  return "未知";
}

function normalizeOrderNoKey(value) {
  const compact = String(value || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  if (!compact) {
    return "";
  }
  return compact.replace("M00", "MO0");
}

const FORCED_UNFINISHED_ORDER_NO_KEYS = new Set([
  "881M0091041",
  "881M0090957",
  "881M0090955",
  "881M0090954",
].map((item) => normalizeOrderNoKey(item)));

const ERP_ORDER_STATUS_TEXT_MAP = {
  Z: "暂存",
  A: "创建",
  B: "审核中",
  C: "已审核",
  D: "重新审核",
};

const COMPLETED_STATUS_TEXTS = new Set([
  "DONE",
  "COMPLETED",
  "CLOSED",
  "已完成",
]);

function isForcedUnfinishedOrder(row) {
  return FORCED_UNFINISHED_ORDER_NO_KEYS.has(
    normalizeOrderNoKey(row?.order_no),
  );
}

function mappedErpOrderStatusText(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (!normalized) {
    return "";
  }
  return ERP_ORDER_STATUS_TEXT_MAP[normalized] || normalized;
}

function isCompletedStatusText(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return COMPLETED_STATUS_TEXTS.has(normalized);
}

function isOrderCompleted(row) {
  if (!row) {
    return false;
  }
  if (isForcedUnfinishedOrder(row)) {
    return false;
  }

  const explicit = String(row?.order_status ?? row?.orderStatus ?? "").trim().toUpperCase();
  if (explicit) {
    if (explicit === "OPEN" || explicit === "IN_PROGRESS") {
      return false;
    }
    if (isCompletedStatusText(explicit)) {
      return true;
    }
  }

  if (isCompletedStatusText(row?.status_name_cn) || isCompletedStatusText(row?.status)) {
    return true;
  }

  const total = toNumber(row?.order_qty);
  const completed = toNumber(row?.completed_qty);
  const remaining = toNumber(row?.remaining_qty);

  if (total > 1e-9) {
    const rawRemaining = Number(row?.remaining_qty);
    if (Number.isFinite(rawRemaining)) {
      return rawRemaining <= 1e-9;
    }
    if (completed + 1e-9 >= total) {
      return true;
    }
  }

  const rate = Number(row?.progress_rate);
  if (Number.isFinite(rate)) {
    return rate >= 99.999;
  }
  return false;
}

function orderStatusText(row) {
  if (!row) {
    return "-";
  }
  if (isOrderCompleted(row)) {
    return "已完成";
  }

  const named = mappedErpOrderStatusText(row?.status_name_cn);
  if (named && named !== "OPEN" && !isCompletedStatusText(named)) {
    return `未完成（${named}）`;
  }
  const code = mappedErpOrderStatusText(row?.status);
  if (code && code !== "OPEN" && !isCompletedStatusText(code)) {
    return `未完成（${code}）`;
  }
  return "未完成";
}

export {
  commandLabel,
  formatPercent,
  isFinalProcess,
  isForcedUnfinishedOrder,
  isInProgressStatus,
  isOrderCompleted,
  mappedErpOrderStatusText,
  materialSupplyTypeName,
  normalizeOrderNoKey,
  orderStatusText,
  progressText,
  toIntText,
  toNumber,
};
