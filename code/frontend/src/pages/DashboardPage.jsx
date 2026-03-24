import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { loadList } from "../services/api";
import SimpleTable from "../components/SimpleTable";

const STATUS_CN = {
  OPEN: "待处理",
  ACKED: "已确认",
  CLOSED: "已关闭"
};

const ALERT_TYPE_CN = {
  PROGRESS_GAP: "进度偏差",
  EQUIPMENT_DOWN: "设备故障"
};

const SEVERITY_CN = {
  CRITICAL: "严重",
  WARN: "警告",
  INFO: "提示"
};

function pad2(value) {
  return String(value).padStart(2, "0");
}

function todayDateKey() {
  const now = new Date();
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
}

function isDateKey(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value || "").trim());
}

function shiftDateKey(dateKey, dayOffset) {
  const source = isDateKey(dateKey) ? dateKey : todayDateKey();
  const [year, month, day] = source.split("-").map((part) => Number(part));
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + dayOffset);
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
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
  return `${n.toFixed(2)}%`;
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

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

function summarizeShiftLoad(rows, dateKey) {
  return rows
    .filter((row) => String(row.calendar_date || "").trim() === dateKey)
    .map((row, index) => ({
      ...row,
      id:
        row.id
        || `${row.calendar_date || ""}#${row.shift_code || row.shift_name_cn || ""}#${row.process_code || ""}#${index}`
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

function summarizeProcessLoad(rows, dateKey) {
  const map = new Map();
  for (const row of rows) {
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

function loadRateStyle(value) {
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

function calcNeededGroups(row) {
  const scheduledQty = toNumber(row.scheduled_qty);
  const capacityPerShift = toNumber(row.capacity_per_shift);
  if (scheduledQty <= 1e-9 || capacityPerShift <= 1e-9) {
    return 0;
  }
  return Math.ceil(scheduledQty / capacityPerShift);
}

function summarizeShiftDemand(rows) {
  const map = new Map();
  for (const row of rows) {
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

function buildShiftEquipmentRows(selectedShiftCode, shiftRows, equipments) {
  const shiftCode = normalizeShiftCode(selectedShiftCode);
  if (!shiftCode) {
    return [];
  }

  const processNeedMap = new Map();
  for (const row of shiftRows) {
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
  for (const equipment of equipments) {
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
        const byWorkshop = String(a.workshop_code || "").localeCompare(String(b.workshop_code || ""), "zh-Hans-CN");
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
    .sort((a, b) => String(a.process_name_cn || a.process_code).localeCompare(String(b.process_name_cn || b.process_code), "zh-Hans-CN"));
}

export default function DashboardPage() {
  const [stats, setStats] = useState({
    orderPool: 0,
    openAlerts: 0,
    versions: 0,
    outboxFailed: 0
  });
  const [mustHandle, setMustHandle] = useState([]);
  const [selectedDateKey, setSelectedDateKey] = useState(todayDateKey());
  const [shiftProcessLoads, setShiftProcessLoads] = useState([]);
  const [equipments, setEquipments] = useState([]);
  const [selectedShiftForEquipments, setSelectedShiftForEquipments] = useState("");
  const [todayProcessMeta, setTodayProcessMeta] = useState({
    versionNo: "",
    versionStatus: ""
  });

  const selectedShiftRows = useMemo(
    () => summarizeShiftLoad(shiftProcessLoads, selectedDateKey),
    [shiftProcessLoads, selectedDateKey]
  );

  const todayProcessRows = useMemo(
    () => summarizeProcessLoad(shiftProcessLoads, selectedDateKey),
    [shiftProcessLoads, selectedDateKey]
  );

  const shiftDemandRows = useMemo(
    () => summarizeShiftDemand(selectedShiftRows),
    [selectedShiftRows]
  );

  const selectedShiftDemand = useMemo(
    () => shiftDemandRows.find((row) => row.shift_code === selectedShiftForEquipments) || null,
    [shiftDemandRows, selectedShiftForEquipments]
  );

  const shiftEquipmentRows = useMemo(
    () => buildShiftEquipmentRows(selectedShiftForEquipments, selectedShiftRows, equipments),
    [selectedShiftForEquipments, selectedShiftRows, equipments]
  );

  useEffect(() => {
    if (shiftDemandRows.length === 0) {
      setSelectedShiftForEquipments("");
      return;
    }
    const exists = shiftDemandRows.some((row) => row.shift_code === selectedShiftForEquipments);
    if (!exists) {
      setSelectedShiftForEquipments(shiftDemandRows[0].shift_code);
    }
  }, [shiftDemandRows, selectedShiftForEquipments]);

  useEffect(() => {
    let active = true;
    async function load() {
      const [orders, alerts, versions, outbox, equipmentRes] = await Promise.all([
        loadList("/internal/v1/internal/order-pool"),
        loadList("/internal/v1/internal/alerts?status=OPEN"),
        loadList("/internal/v1/internal/schedule-versions"),
        loadList("/internal/v1/internal/integration/outbox"),
        loadList("/v1/mes/equipments")
      ]);
      const versionItems = versions.items ?? [];
      const referenceVersion = pickReferenceVersion(versionItems);
      let shiftLoadRows = [];
      let versionNo = "";
      let versionStatus = "";
      if (referenceVersion?.version_no) {
        const shiftLoad = await loadList(
          `/internal/v1/internal/schedule-versions/${referenceVersion.version_no}/shift-process-load`
        );
        shiftLoadRows = shiftLoad.items ?? [];
        versionNo = referenceVersion.version_no;
        versionStatus = referenceVersion.status_name_cn || referenceVersion.status || "";
      }
      if (!active) {
        return;
      }
      setStats({
        orderPool: orders.total,
        openAlerts: alerts.total,
        versions: versions.total,
        outboxFailed: outbox.items.filter((x) => x.status !== "SUCCESS").length
      });
      setMustHandle(alerts.items.slice(0, 6));
      setShiftProcessLoads(shiftLoadRows);
      setEquipments(equipmentRes.items ?? []);
      setTodayProcessMeta({
        versionNo,
        versionStatus
      });
    }
    load().catch(() => {});
    const timer = setInterval(() => load().catch(() => {}), 60000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return (
    <section>
      <h2>经营看板</h2>
      <div className="card-grid">
        <Link className="metric-card metric-card-link" to="/orders/pool">
          <span>待排生产订单</span>
          <strong>{stats.orderPool}</strong>
        </Link>
        <Link className="metric-card metric-card-link" to="/alerts">
          <span>待处理预警</span>
          <strong>{stats.openAlerts}</strong>
        </Link>
        <Link className="metric-card metric-card-link" to="/schedule/versions">
          <span>版本数</span>
          <strong>{stats.versions}</strong>
        </Link>
        <Link className="metric-card metric-card-link" to="/ops/integration">
          <span>异常投递</span>
          <strong>{stats.outboxFailed}</strong>
        </Link>
      </div>
      <div className="panel">
        <div className="panel-head">
          <h3>今日必须处理</h3>
          <Link to="/alerts">前往预警中心</Link>
        </div>
        <SimpleTable
          columns={[
            { key: "alert_id", title: "预警ID" },
            {
              key: "alert_type",
              title: "类型",
              render: (value, row) => row.alert_type_name_cn || ALERT_TYPE_CN[value] || value || "-"
            },
            {
              key: "severity",
              title: "严重度",
              render: (value, row) => row.severity_name_cn || SEVERITY_CN[value] || value || "-"
            },
            {
              key: "order_no",
              title: "订单",
              render: (value) =>
                value ? (
                  <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(value)}`}>
                    {value}
                  </Link>
                ) : (
                  "-"
                )
            },
            {
              key: "status",
              title: "状态",
              render: (value, row) => row.status_name_cn || STATUS_CN[value] || value || "-"
            }
          ]}
          rows={mustHandle}
        />
      </div>
      <div className="panel">
        <div className="panel-head">
          <h3>工作安排与班次负荷</h3>
          <Link to="/schedule/board">前往调度台</Link>
        </div>
        <div className="toolbar">
          <button onClick={() => setSelectedDateKey((prev) => shiftDateKey(prev, -1))}>上一天</button>
          <input
            type="date"
            value={selectedDateKey}
            onChange={(e) => setSelectedDateKey(e.target.value ? e.target.value : todayDateKey())}
          />
          <button onClick={() => setSelectedDateKey((prev) => shiftDateKey(prev, 1))}>下一天</button>
        </div>
        <p className="hint">
          统计日期：{selectedDateKey}
          {todayProcessMeta.versionNo
            ? `；版本：${todayProcessMeta.versionNo}${todayProcessMeta.versionStatus ? ` / ${todayProcessMeta.versionStatus}` : ""}`
            : "；暂无可用排产版本"}
          {todayProcessMeta.versionNo ? "；最大产能按班次人力/设备约束计算。" : ""}
        </p>
        <p className="hint">班次总需求：按已安排量折算总需人力、总需设备。</p>
        <SimpleTable
          columns={[
            {
              key: "shift_name_cn",
              title: "班次",
              render: (_, row) => row.shift_name_cn || row.shift_code || "-"
            },
            {
              key: "scheduled_qty_total",
              title: "已安排总量",
              render: (value) => formatQty(value)
            },
            {
              key: "required_workers_total",
              title: "总需人力",
              render: (value) => formatQty(value)
            },
            {
              key: "required_machines_total",
              title: "总需设备",
              render: (value) => formatQty(value)
            },
            {
              key: "id",
              title: "设备列表",
              render: (_, row) => (
                <button type="button" onClick={() => setSelectedShiftForEquipments(row.shift_code)}>
                  {selectedShiftForEquipments === row.shift_code ? "当前查看" : "查看设备"}
                </button>
              )
            }
          ]}
          rows={shiftDemandRows}
        />
        {selectedShiftDemand ? (
          <>
            <p className="hint">
              设备清单：{selectedDateKey} {selectedShiftDemand.shift_name_cn}
              （总需设备 {formatQty(selectedShiftDemand.required_machines_total)} 台）。
            </p>
            <SimpleTable
              columns={[
                {
                  key: "process_name_cn",
                  title: "工序",
                  render: (_, row) =>
                    row.process_code ? (
                      <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                        {row.process_name_cn || row.process_code}
                      </Link>
                    ) : (
                      row.process_name_cn || "-"
                    )
                },
                {
                  key: "needed_machines",
                  title: "需设备数",
                  render: (value) => formatQty(value)
                },
                {
                  key: "available_machines",
                  title: "可选设备数",
                  render: (value) => formatQty(value)
                },
                {
                  key: "equipment_group_summary",
                  title: "车间/产线分组",
                  render: (value) => <span className="cell-wrap">{value || "-"}</span>
                },
                {
                  key: "equipment_list",
                  title: "建议设备列表",
                  render: (value) => <span className="cell-wrap">{value || "-"}</span>
                },
                {
                  key: "shortage",
                  title: "缺口",
                  render: (value) => formatQty(value)
                }
              ]}
              rows={shiftEquipmentRows}
            />
          </>
        ) : null}
        <p className="hint">班次明细：按班次与工序展示负荷。</p>
        <SimpleTable
          columns={[
            {
              key: "shift_code",
              title: "班次",
              render: (value, row) => row.shift_name_cn || value || "-"
            },
            {
              key: "process_name_cn",
              title: "工序",
              render: (_, row) =>
                row.process_code ? (
                  <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                    {row.process_name_cn || row.process_name || row.process_code}
                  </Link>
                ) : (
                  row.process_name_cn || row.process_name || "-"
                )
            },
            {
              key: "scheduled_qty",
              title: "已安排量",
              render: (value) => formatQty(value)
            },
            {
              key: "max_capacity_qty",
              title: "最大产能",
              render: (value) => formatQty(value)
            },
            {
              key: "load_rate",
              title: "负荷度",
              render: (value) => {
                const style = loadRateStyle(value);
                const text = formatPercent(value);
                return style ? <span style={style}>{text}</span> : text;
              }
            },
            {
              key: "capacity_per_shift",
              title: "每组产能/班",
              render: (value) => formatQty(value)
            },
            {
              key: "required_workers",
              title: "每组需人",
              render: (value) => formatQty(value)
            },
            {
              key: "required_machines",
              title: "每组需机",
              render: (value) => formatQty(value)
            },
            {
              key: "available_workers",
              title: "可用人力",
              render: (value) => formatQty(value)
            },
            {
              key: "available_machines",
              title: "可用设备",
              render: (value) => formatQty(value)
            }
          ]}
          rows={selectedShiftRows}
        />
        <p className="hint">日期汇总：按工序合并（跨班次）。</p>
        <SimpleTable
          columns={[
            {
              key: "process_name",
              title: "工序",
              render: (_, row) =>
                row.process_code ? (
                  <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                    {row.process_name}
                  </Link>
                ) : (
                  row.process_name || "-"
                )
            },
            {
              key: "scheduled_qty",
              title: "已安排量",
              render: (value) => formatQty(value)
            },
            {
              key: "max_capacity_qty",
              title: "最大产能",
              render: (value) => formatQty(value)
            },
            {
              key: "load_rate",
              title: "负荷度",
              render: (value) => formatPercent(value)
            }
          ]}
          rows={todayProcessRows}
        />
      </div>
    </section>
  );
}
