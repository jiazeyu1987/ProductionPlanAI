function pad2(value) {
  return String(value).padStart(2, "0");
}

export function todayDateKey() {
  const now = new Date();
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
}

function isDateKey(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value || "").trim());
}

export function shiftDateKey(dateKey, dayOffset) {
  const source = isDateKey(dateKey) ? dateKey : todayDateKey();
  const [year, month, day] = source.split("-").map((part) => Number(part));
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + dayOffset);
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

export function formatQty(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return n.toFixed(2);
}

export function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return `${n.toFixed(2)}%`;
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function shiftSortIndex(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "DAY" || normalized === "D") {
    return 0;
  }
  if (normalized === "NIGHT" || normalized === "N") {
    return 1;
  }
  return 9;
}

function normalizeShiftCode(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "D") {
    return "DAY";
  }
  if (normalized === "N") {
    return "NIGHT";
  }
  return normalized;
}

function shiftNameCn(shiftCode, fallbackName) {
  if (fallbackName) {
    return fallbackName;
  }
  const normalized = normalizeShiftCode(shiftCode);
  if (normalized === "DAY") {
    return "白班";
  }
  if (normalized === "NIGHT") {
    return "夜班";
  }
  return normalized || "-";
}

export function summarizeShiftLoad(rows, dateKey) {
  return (rows || [])
    .filter((row) => String(row.calendar_date || "").trim() === dateKey)
    .map((row, index) => ({
      ...row,
      id:
        row.id ||
        `${row.calendar_date || ""}#${row.shift_code || row.shift_name_cn || ""}#${
          row.process_code || ""
        }#${index}`
    }))
    .sort((a, b) => {
      const byShift = shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code);
      if (byShift !== 0) {
        return byShift;
      }
      const byLoad = Number(b.load_rate || 0) - Number(a.load_rate || 0);
      if (Math.abs(byLoad) > 1e-9) {
        return byLoad;
      }
      return String(a.process_name_cn || a.process_code || "").localeCompare(
        String(b.process_name_cn || b.process_code || ""),
        "zh-Hans-CN"
      );
    });
}

export function summarizeProcessLoad(rows, dateKey) {
  const map = new Map();
  for (const row of rows || []) {
    if (String(row.calendar_date || "").trim() !== dateKey) {
      continue;
    }
    const processCode = String(row.process_code || "").trim();
    const processName = row.process_name_cn || row.process_name || processCode || "-";
    const key = processCode || processName;
    if (!map.has(key)) {
      map.set(key, {
        id: key,
        process_code: processCode,
        process_name: processName,
        scheduled_qty: 0,
        max_capacity_qty: 0
      });
    }
    const item = map.get(key);
    item.scheduled_qty += Number(row.scheduled_qty || 0);
    item.max_capacity_qty += Number(row.max_capacity_qty || 0);
  }

  return [...map.values()]
    .map((item) => ({
      id: item.id,
      process_code: item.process_code,
      process_name: item.process_name,
      scheduled_qty: item.scheduled_qty,
      max_capacity_qty: item.max_capacity_qty,
      load_rate:
        item.max_capacity_qty > 1e-9
          ? (item.scheduled_qty / item.max_capacity_qty) * 100
          : item.scheduled_qty > 1e-9
            ? 100
            : 0
    }))
    .sort((a, b) => {
      const byQty = b.scheduled_qty - a.scheduled_qty;
      if (Math.abs(byQty) > 1e-9) {
        return byQty;
      }
      return String(a.process_name).localeCompare(String(b.process_name), "zh-Hans-CN");
    });
}

export function loadRateStyle(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return null;
  }
  if (n >= 100) {
    return { color: "#b42318", fontWeight: 600 };
  }
  if (n >= 85) {
    return { color: "#b54708", fontWeight: 600 };
  }
  return null;
}

function calcNeededGroups(row) {
  const scheduledQty = toNumber(row.scheduled_qty);
  const capacityPerShift = toNumber(row.capacity_per_shift);
  if (scheduledQty <= 1e-9 || capacityPerShift <= 1e-9) {
    return 0;
  }
  return Math.ceil(scheduledQty / capacityPerShift);
}

export function summarizeShiftDemand(rows) {
  const map = new Map();
  for (const row of rows || []) {
    const shiftCode = normalizeShiftCode(row.shift_code);
    const key = shiftCode || "UNKNOWN";
    if (!map.has(key)) {
      map.set(key, {
        id: key,
        shift_code: key,
        shift_name_cn: shiftNameCn(key, row.shift_name_cn),
        scheduled_qty_total: 0,
        required_workers_total: 0,
        required_machines_total: 0
      });
    }
    const groups = calcNeededGroups(row);
    const requiredWorkers = groups * toNumber(row.required_workers);
    const requiredMachines = groups * toNumber(row.required_machines);
    const item = map.get(key);
    item.scheduled_qty_total += toNumber(row.scheduled_qty);
    item.required_workers_total += requiredWorkers;
    item.required_machines_total += requiredMachines;
  }
  return [...map.values()].sort((a, b) => shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code));
}

export function buildShiftEquipmentRows(selectedShiftCode, shiftRows, equipments) {
  const shiftCode = normalizeShiftCode(selectedShiftCode);
  if (!shiftCode) {
    return [];
  }

  const processNeedMap = new Map();
  for (const row of shiftRows || []) {
    if (normalizeShiftCode(row.shift_code) !== shiftCode) {
      continue;
    }
    const processCode = String(row.process_code || "").trim();
    if (!processCode) {
      continue;
    }
    const groups = calcNeededGroups(row);
    const neededMachines = groups * toNumber(row.required_machines);
    if (!processNeedMap.has(processCode)) {
      processNeedMap.set(processCode, {
        id: processCode,
        process_code: processCode,
        process_name_cn: row.process_name_cn || processCode,
        needed_machines: 0
      });
    }
    const item = processNeedMap.get(processCode);
    item.needed_machines += neededMachines;
  }

  const equipmentByProcess = new Map();
  for (const equipment of equipments || []) {
    const processCode = String(equipment.process_code || "").trim();
    const equipmentCode = String(equipment.equipment_code || "").trim();
    if (!processCode || !equipmentCode) {
      continue;
    }
    const status = String(equipment.status || "").trim().toUpperCase();
    if (status && status !== "AVAILABLE") {
      continue;
    }
    const workshopCode = String(equipment.workshop_code || "").trim();
    const lineCode = String(equipment.line_code || "").trim();
    if (!equipmentByProcess.has(processCode)) {
      equipmentByProcess.set(processCode, new Map());
    }
    equipmentByProcess.get(processCode).set(equipmentCode, {
      equipment_code: equipmentCode,
      workshop_code: workshopCode,
      line_code: lineCode,
      display_name: `${equipmentCode}${lineCode ? `(${lineCode})` : ""}`
    });
  }

  return [...processNeedMap.values()]
    .map((item) => {
      const availableMap = equipmentByProcess.get(item.process_code) || new Map();
      const availableList = [...availableMap.values()].sort((a, b) => {
        const byWorkshop = String(a.workshop_code || "").localeCompare(
          String(b.workshop_code || ""),
          "zh-Hans-CN"
        );
        if (byWorkshop !== 0) {
          return byWorkshop;
        }
        const byLine = String(a.line_code || "").localeCompare(String(b.line_code || ""), "zh-Hans-CN");
        if (byLine !== 0) {
          return byLine;
        }
        return String(a.equipment_code || "").localeCompare(String(b.equipment_code || ""), "zh-Hans-CN");
      });
      const needCount = Math.max(0, Math.ceil(item.needed_machines));
      const selectedList = availableList.slice(0, needCount);
      const shortage = Math.max(0, needCount - availableList.length);
      const groupMap = new Map();
      for (const selected of selectedList) {
        const workshop = selected.workshop_code || "-";
        const line = selected.line_code || "-";
        const groupKey = `${workshop}/${line}`;
        if (!groupMap.has(groupKey)) {
          groupMap.set(groupKey, []);
        }
        groupMap.get(groupKey).push(selected.equipment_code);
      }
      const grouped = [...groupMap.entries()]
        .map(([group, codes]) => `${group}: ${codes.join("、")}`)
        .join("；");
      return {
        ...item,
        needed_machines: needCount,
        available_machines: availableList.length,
        equipment_list: selectedList.length > 0 ? selectedList.map((x) => x.display_name).join("、") : "-",
        equipment_group_summary: grouped || "-",
        shortage
      };
    })
    .sort((a, b) =>
      String(a.process_name_cn || a.process_code).localeCompare(
        String(b.process_name_cn || b.process_code),
        "zh-Hans-CN"
      )
    );
}

