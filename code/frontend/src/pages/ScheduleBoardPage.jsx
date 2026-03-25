import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract, postLegacy } from "../services/api";

const REASON_TEXT = {
  NONE: "无",
  CAPACITY_MANPOWER: "人力不足",
  CAPACITY_MACHINE: "设备不足",
  CAPACITY_UNKNOWN: "综合产能不足",
  MATERIAL_SHORTAGE: "物料不足",
  DEPENDENCY_BLOCKED: "前序工序未释放",
  FROZEN_BY_POLICY: "订单冻结策略",
  LOCKED_PRESERVED: "锁单保留基线",
  UNKNOWN: "未识别原因"
};

const DIMENSION_TEXT = {
  NONE: "无",
  CAPACITY: "人机产能",
  MATERIAL: "物料",
  DEPENDENCY: "工序依赖",
  POLICY: "策略约束",
  UNKNOWN: "未识别"
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
    row?.reason_code || row?.reasonCode || row?.last_block_reason || row?.lastBlockReason || ""
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
      lock_flag: Number(task?.lock_flag) === 1 ? 1 : 0
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
        dimension_counter: {}
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
      row?.blocking_dimension || row?.blockingDimension || inferDimensionByReason(reasonCode)
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
          : (totalScheduledQty > 1e-9 ? (item.scheduled_qty / totalScheduledQty) * 100 : 0);
      const qtyShareRaw = totalScheduledQty > 1e-9 ? (item.scheduled_qty / totalScheduledQty) * 100 : 0;

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
        explain_cn: explain
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
  { code: "MIN_DELAY_FIRST", label: "交期最小延期优先" }
];

function normalizeStrategyCode(value) {
  const raw = String(value || "").trim().toUpperCase();
  return STRATEGY_OPTIONS.some((item) => item.code === raw) ? raw : "KEY_ORDER_FIRST";
}

function strategyLabel(value) {
  const code = normalizeStrategyCode(value);
  return STRATEGY_OPTIONS.find((item) => item.code === code)?.label || code;
}

export default function ScheduleBoardPage() {
  const [versions, setVersions] = useState([]);
  const [selected, setSelected] = useState("");
  const [strategyCode, setStrategyCode] = useState("KEY_ORDER_FIRST");
  const [tasks, setTasks] = useState([]);
  const [orderAllocationRows, setOrderAllocationRows] = useState([]);
  const [dailyProcessLoadRows, setDailyProcessLoadRows] = useState([]);
  const [algorithmDetail, setAlgorithmDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState("");
  const detailRequestSeqRef = useRef(0);
  const selectedVersion = useMemo(
    () => versions.find((item) => normalizeVersionNo(item) === selected) || null,
    [selected, versions]
  );
  const canPublishSelected = String(selectedVersion?.status || "").toUpperCase() === "DRAFT";

  const boardSummary = useMemo(() => {
    if (!orderAllocationRows.length) {
      return null;
    }
    const totalScheduledQty = orderAllocationRows.reduce((sum, row) => sum + toNumber(row.scheduled_qty), 0);
    const totalUnscheduledQty = orderAllocationRows.reduce((sum, row) => sum + toNumber(row.unscheduled_qty), 0);
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
      topReasonCode
    };
  }, [orderAllocationRows]);

  async function loadVersions() {
    const data = await loadList("/internal/v1/internal/schedule-versions");
    const items = data.items ?? [];
    setVersions(items);
    if (items.length === 0) {
      setSelected("");
      return;
    }
    const exists = items.some((item) => normalizeVersionNo(item) === selected);
    if (!selected || !exists) {
      setSelected(normalizeVersionNo(items[items.length - 1]));
    }
  }

  async function loadVersionDetail(versionNo) {
    const requestSeq = detailRequestSeqRef.current + 1;
    detailRequestSeqRef.current = requestSeq;
    if (!versionNo) {
      setTasks([]);
      setAlgorithmDetail(null);
      setOrderAllocationRows([]);
      setDailyProcessLoadRows([]);
      setDetailLoading(false);
      return;
    }
    setDetailLoading(true);
    setTasks([]);
    setAlgorithmDetail(null);
    setOrderAllocationRows([]);
    setDailyProcessLoadRows([]);
    try {
      const [tasksRes, algorithmRes, schedulesRes, orderPoolRes, dailyLoadRes] = await Promise.all([
        loadList(`/internal/v1/internal/schedule-versions/${versionNo}/tasks`),
        loadList(`/internal/v1/internal/schedule-versions/${versionNo}/algorithm`),
        loadList("/api/schedules"),
        loadList("/internal/v1/internal/order-pool"),
        loadList(`/internal/v1/internal/schedule-versions/${versionNo}/daily-process-load`)
      ]);
      if (requestSeq !== detailRequestSeqRef.current) {
        return;
      }
      setTasks(tasksRes.items ?? []);
      setAlgorithmDetail(algorithmRes);
      setStrategyCode(
        normalizeStrategyCode(
          algorithmRes?.strategy_code ||
            algorithmRes?.metadata?.schedule_strategy_code ||
            algorithmRes?.metadata?.scheduleStrategyCode
        )
      );
      const scheduleItems = schedulesRes.items ?? [];
      const selectedSchedule = scheduleItems.find((item) => normalizeVersionNo(item) === versionNo);
      setOrderAllocationRows(buildOrderAllocationRows(selectedSchedule, orderPoolRes.items ?? [], tasksRes.items ?? []));
      setDailyProcessLoadRows(dailyLoadRes.items ?? []);
    } finally {
      if (requestSeq === detailRequestSeqRef.current) {
        setDetailLoading(false);
      }
    }
  }

  async function generate() {
    setMessage("");
    const nextStrategyCode = normalizeStrategyCode(strategyCode);
    const schedule = await postLegacy("/api/schedules/generate", {
      base_version_no: selected || null,
      autoReplan: false,
      strategy_code: nextStrategyCode
    });
    const versionNo = String(schedule.version_no || schedule.versionNo || "");
    setMessage(`生成完成：${versionNo || "-"}（${strategyLabel(nextStrategyCode)}）`);
    await loadVersions();
    if (versionNo) {
      setSelected(versionNo);
    }
  }

  async function publishSelectedDraft() {
    if (!selected) {
      setMessage("请先选择草稿版本。");
      return;
    }
    if (!canPublishSelected) {
      setMessage("当前选择的版本不是草稿，无法发布。");
      return;
    }
    setPublishing(true);
    setMessage("");
    try {
      await postContract(`/internal/v1/internal/schedule-versions/${selected}/publish`, {
        operator: "publisher01",
        reason: "调度台发布草稿"
      });
      setMessage(`宸插彂甯冪増鏈細${selected}`);
      await loadVersions();
      await loadVersionDetail(selected);
    } catch (e) {
      setMessage(e.message);
    } finally {
      setPublishing(false);
    }
  }

  useEffect(() => {
    loadVersions().catch(() => {});
  }, []);

  useEffect(() => {
    loadVersionDetail(selected).catch(() => {});
  }, [selected]);

  return (
    <section>
      <h2>调度台</h2>
      <div className="toolbar">
        <label>
          策略
          <select value={strategyCode} onChange={(e) => setStrategyCode(normalizeStrategyCode(e.target.value))}>
            {STRATEGY_OPTIONS.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <button onClick={() => generate().catch((e) => setMessage(e.message))}>鐢熸垚鑽夌鐗堟湰</button>
        <button
          disabled={!selected || !canPublishSelected || publishing}
          onClick={() => publishSelectedDraft().catch((e) => setMessage(e.message))}
        >
          {publishing ? "鍙戝竷涓?.." : "鍙戝竷鑽夌"}
        </button>
        <select value={selected} onChange={(e) => setSelected(e.target.value)}>
          <option value="">璇烽€夋嫨鐗堟湰</option>
          {versions.map((item) => (
            <option key={normalizeVersionNo(item)} value={normalizeVersionNo(item)}>
              {normalizeVersionNo(item)} / {item.status_name_cn || item.status || "-"} / {item.strategy_name_cn || strategyLabel(item.strategy_code)}
            </option>
          ))}
        </select>
      </div>

      {message ? <p className="notice">{message}</p> : null}
      <p className="hint">
        璁㈠崟鍒楀睍绀虹殑鏄敓浜ц鍗曞彿銆備竴涓鍗曚細琚媶鎴愬琛屼换鍔★紝涓嶅悓宸ュ簭/鏃ユ湡/鐝锛屾墍浠ヤ細閲嶅鍑虹幇锛岃繖鏄甯哥幇璞°€?      </p>
      <p className="hint">
        涓嬮潰鈥滆祫婧愬崰姣斺€濇寜褰撳墠鑽夌涓殑浜哄姏+璁惧鐢ㄩ噺浼扮畻锛涘鏋滆鍗曟病鏈夎祫婧愮敤閲忔暟鎹紝鍥為€€涓鸿鍒掗噺鍗犳瘮銆?      </p>

      {boardSummary ? (
        <div className="card-grid">
          <article className="metric-card">
            <span>鍙備笌鍒嗛厤璁㈠崟</span>
            <strong>{boardSummary.orderCount}</strong>
          </article>
          <article className="metric-card">
            <span>宸插垎閰嶆€婚噺</span>
            <strong>{formatQty(boardSummary.totalScheduledQty)}</strong>
          </article>
          <article className="metric-card">
            <span>鏈帓鎬婚噺</span>
            <strong>{formatQty(boardSummary.totalUnscheduledQty)}</strong>
          </article>
          <article className="metric-card">
            <span>棣栬鐡堕</span>
            <strong>{reasonToText(boardSummary.topReasonCode)}</strong>
          </article>
        </div>
      ) : null}

      <div className="panel">
        <h3>鍒嗛厤鍒楄〃</h3>
        {detailLoading ? <p className="hint">姝ｅ湪鍒囨崲鑽夌骞跺埛鏂板垎閰嶈В閲?..</p> : null}
        {algorithmDetail ? (
          <p className="hint">
            版本 {algorithmDetail.version_no || selected || "-"}：任务{" "}
            {formatQty(algorithmDetail?.summary?.task_count)} 个，已排{" "}
            {formatQty(algorithmDetail?.summary?.scheduled_qty)}，完成率{" "}
            {formatPercent(algorithmDetail?.summary?.schedule_completion_rate)}。
          </p>
        ) : null}
        {algorithmDetail ? (
          <p className="hint">当前策略：{strategyLabel(algorithmDetail.strategy_code || strategyCode)}</p>
        ) : null}
        <SimpleTable
          columns={[
            {
              key: "order_no",
              title: "璁㈠崟",
              render: (value) =>
                value ? (
                  <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(value)}`}>
                    {value}
                  </Link>
                ) : (
                  "-"
                )
            },
            { key: "priority_label", title: "优先级" },
            { key: "lock_label", title: "鏄惁閿佸崟" },
            { key: "scheduled_qty", title: "宸插垎閰嶉噺", render: (value) => formatQty(value) },
            { key: "resource_share", title: "璧勬簮鍗犳瘮", render: (value) => formatPercent(value) },
            { key: "qty_share", title: "计划量占比", render: (value) => formatPercent(value) },
            { key: "unscheduled_qty", title: "未排量", render: (value) => formatQty(value) },
            { key: "bottleneck_reason_text", title: "涓昏鐡堕" },
            { key: "bottleneck_dimension_text", title: "鐡堕缁村害" },
            {
              key: "explain_cn",
              title: "为什么这么分配",
              render: (value) => <span className="cell-wrap">{value || "-"}</span>
            }
          ]}
          rows={orderAllocationRows}
        />
      </div>

      <div className="panel">
        <h3>浠诲姟鏄庣粏</h3>
        <p className="hint">受影响任务：{tasks.length} 条。</p>
        <SimpleTable
          columns={[
            {
              key: "order_no",
              title: "璁㈠崟",
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
              key: "process_code",
              title: "宸ュ簭",
              render: (value, row) => {
                const processCode = value || "";
                const processName = row.process_name_cn || processCode || "-";
                if (!processCode) {
                  return processName;
                }
                return (
                  <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(processCode)}`}>
                    {processName}
                  </Link>
                );
              }
            },
            { key: "calendar_date", title: "鏃ユ湡" },
            {
              key: "shift_code",
              title: "鐝",
              render: (value, row) => row.shift_name_cn || value || "-"
            },
            { key: "plan_qty", title: "计划量", render: (value) => formatQty(value) }
          ]}
          rows={tasks}
        />
      </div>

      <div className="panel">
        <h3>每天工序工作量与最大产能</h3>
        <p className="hint">按“日期 × 工序”展示当日已安排量、最大产能和负荷率。</p>
        <SimpleTable
          columns={[
            { key: "calendar_date", title: "鏃ユ湡" },
            { key: "process_name_cn", title: "宸ュ簭" },
            { key: "scheduled_qty", title: "宸插畨鎺掗噺", render: (value) => formatQty(value) },
            { key: "max_capacity_qty", title: "最大产能", render: (value) => formatQty(value) },
            { key: "load_rate", title: "负荷率", render: (value) => formatPercent(value) },
            { key: "open_shift_count", title: "寮€鏀剧彮娆℃暟", render: (value) => formatQty(value) }
          ]}
          rows={dailyProcessLoadRows}
        />
      </div>
    </section>
  );
}

