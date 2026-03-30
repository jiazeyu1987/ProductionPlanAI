import { toNumber } from "./scheduleVersionsFormattersUtils";

export function taskKey(row) {
  return [
    String(row.order_no || ""),
    String(row.process_code || ""),
    String(row.calendar_date || ""),
    String(row.shift_code || ""),
  ].join("|");
}

export function changeType(baseQty, currentQty) {
  if (Math.abs(baseQty) < 1e-9 && Math.abs(currentQty) >= 1e-9) {
    return "新增";
  }
  if (Math.abs(baseQty) >= 1e-9 && Math.abs(currentQty) < 1e-9) {
    return "移除";
  }
  return currentQty > baseQty ? "增加" : "减少";
}

export function changeTypeClass(type) {
  if (type === "新增") {
    return "diff-tag diff-tag-new";
  }
  if (type === "移除") {
    return "diff-tag diff-tag-removed";
  }
  if (type === "增加") {
    return "diff-tag diff-tag-up";
  }
  return "diff-tag diff-tag-down";
}

export function buildDiffRows(baseTasks, currentTasks) {
  const baseMap = new Map(baseTasks.map((row) => [taskKey(row), row]));
  const currentMap = new Map(currentTasks.map((row) => [taskKey(row), row]));
  const allKeys = [...new Set([...baseMap.keys(), ...currentMap.keys()])];

  const rows = allKeys
    .map((key) => {
      const base = baseMap.get(key);
      const current = currentMap.get(key);
      const baseQty = toNumber(base?.plan_qty);
      const currentQty = toNumber(current?.plan_qty);
      const deltaQty = currentQty - baseQty;
      if (Math.abs(deltaQty) < 1e-9) {
        return null;
      }
      const ref = current || base || {};
      return {
        id: key,
        order_no: ref.order_no || "-",
        process_code: ref.process_code || "",
        process_name: ref.process_name_cn || ref.process_code || "-",
        calendar_date: ref.calendar_date || "-",
        shift_name: ref.shift_name_cn || ref.shift_code || "-",
        base_qty: baseQty,
        current_qty: currentQty,
        delta_qty: deltaQty,
        change_type: changeType(baseQty, currentQty),
      };
    })
    .filter(Boolean);

  return rows.sort((a, b) => {
    if (a.order_no !== b.order_no) {
      return String(a.order_no).localeCompare(String(b.order_no), "zh-Hans-CN");
    }
    if (a.process_name !== b.process_name) {
      return String(a.process_name).localeCompare(String(b.process_name), "zh-Hans-CN");
    }
    if (a.calendar_date !== b.calendar_date) {
      return String(a.calendar_date).localeCompare(String(b.calendar_date), "zh-Hans-CN");
    }
    return String(a.shift_name).localeCompare(String(b.shift_name), "zh-Hans-CN");
  });
}

