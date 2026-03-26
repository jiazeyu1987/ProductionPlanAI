import { useEffect, useMemo, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";
import { formatDateTimeByField } from "../utils/datetime";

const STATUS_CN = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  SUPERSEDED: "已替代",
  ROLLED_BACK: "已回滚"
};

const RULE_VERSION_CN = {
  "RULE-P0-BASE": "P0基础规则"
};

const STRATEGY_LABEL = {
  KEY_ORDER_FIRST: "关键订单优先",
  MAX_CAPACITY_FIRST: "最大产能优先",
  MIN_DELAY_FIRST: "交期最小延期优先"
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

function formatSignedQty(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n) < 1e-9) {
    return "0";
  }
  const sign = n > 0 ? "+" : "-";
  return `${sign}${formatQty(Math.abs(n))}`;
}

function taskKey(row) {
  return [
    String(row.order_no || ""),
    String(row.process_code || ""),
    String(row.calendar_date || ""),
    String(row.shift_code || "")
  ].join("|");
}

function changeType(baseQty, currentQty) {
  if (Math.abs(baseQty) < 1e-9 && Math.abs(currentQty) >= 1e-9) {
    return "新增";
  }
  if (Math.abs(baseQty) >= 1e-9 && Math.abs(currentQty) < 1e-9) {
    return "移除";
  }
  return currentQty > baseQty ? "增加" : "减少";
}

function changeTypeClass(type) {
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

function buildDiffRows(baseTasks, currentTasks) {
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
        change_type: changeType(baseQty, currentQty)
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

function formatDiffForView(value, field = "") {
  if (Array.isArray(value)) {
    return value.map((item) => formatDiffForView(item, field));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, formatDiffForView(item, key)]));
  }
  return formatDateTimeByField(value, field);
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

function reasonToText(reasonCode) {
  if (reasonCode === "CAPACITY_LIMIT") {
    return "当前班次可用产能不足（人力/设备/物料或班次容量受限）";
  }
  if (reasonCode === "DEPENDENCY_LIMIT") {
    return "受前序工序约束，后序暂时不能继续排。";
  }
  if (!reasonCode) {
    return "未标记原因";
  }
  return reasonCode;
}

function processExplainToText(row) {
  if (row?.explain_cn) {
    return row.explain_cn;
  }
  const processName = row?.process_name_cn || row?.process_code || "该工序";
  const targetQty = formatQty(row?.target_qty);
  const scheduledQty = formatQty(row?.scheduled_qty);
  const unscheduledQty = toNumber(row?.unscheduled_qty);
  const scheduleRate = formatPercent(row?.schedule_rate);
  if (unscheduledQty <= 1e-9) {
    return `${processName}目标量${targetQty}，已分配${scheduledQty}（${scheduleRate}），当前约束下已排满。`;
  }
  const reasonText = reasonToText(row?.top_block_reason_code);
  return (
    `${processName}目标量${targetQty}，已分配${scheduledQty}（${scheduleRate}），` +
    `仍有${formatQty(row?.unscheduled_qty)}未排，主要受“${reasonText}”影响。`
  );
}

function maxAllocationExplainToText(row) {
  if (row?.max_allocation_explain_cn) {
    return row.max_allocation_explain_cn;
  }
  const processName = row?.process_name_cn || row?.process_code || "该工序";
  const qty = formatQty(row?.max_allocation_qty);
  const orderNo = row?.max_allocation_order_no || "-";
  const date = row?.max_allocation_date || "-";
  const shiftName = row?.max_allocation_shift_name_cn || row?.max_allocation_shift_code || "";
  return `${processName}在${date}${shiftName}对订单${orderNo}分配到峰值${qty}，这是在当班可行约束下形成的最大单次投放。`;
}

function normalizeProcessSummaryRow(row) {
  const scheduledQty = toNumber(row?.scheduled_qty);
  const targetRaw = Number(row?.target_qty);
  const unscheduledRaw = Number(row?.unscheduled_qty);
  const maxAllocationRaw = Number(row?.max_allocation_qty);
  const rateRaw = Number(row?.schedule_rate);

  const targetQty = Number.isFinite(targetRaw) ? Math.max(0, targetRaw) : scheduledQty;
  const unscheduledQty = Number.isFinite(unscheduledRaw) ? Math.max(0, unscheduledRaw) : Math.max(0, targetQty - scheduledQty);
  const resolvedTargetQty = Math.max(targetQty, scheduledQty + unscheduledQty);
  const scheduleRate = Number.isFinite(rateRaw)
    ? Math.max(0, Math.min(100, rateRaw))
    : (resolvedTargetQty > 1e-9 ? Math.max(0, Math.min(100, (scheduledQty / resolvedTargetQty) * 100)) : 100);
  const maxAllocationQty = Number.isFinite(maxAllocationRaw) ? Math.max(0, maxAllocationRaw) : scheduledQty;

  return {
    ...row,
    target_qty: resolvedTargetQty,
    max_allocation_qty: maxAllocationQty,
    unscheduled_qty: unscheduledQty,
    schedule_rate: scheduleRate
  };
}

function normalizeStrategyCode(value) {
  const raw = String(value || "").trim().toUpperCase();
  if (raw === "MAX_CAPACITY_FIRST") {
    return "MAX_CAPACITY_FIRST";
  }
  if (raw === "MIN_DELAY_FIRST") {
    return "MIN_DELAY_FIRST";
  }
  return "KEY_ORDER_FIRST";
}

function strategyNarrativeLine(strategyCode) {
  const code = normalizeStrategyCode(strategyCode);
  if (code === "MAX_CAPACITY_FIRST") {
    return "本版采用“最大产能优先”，先让可一次性消化量更大的任务吃满当班能力，再补充其余订单。";
  }
  if (code === "MIN_DELAY_FIRST") {
    return "本版采用“交期最小延期优先”，先压缩最紧急交期任务的延期风险，再分配剩余能力。";
  }
  return "本版采用“关键订单优先”，先保障关键订单，再按交期与约束稳态扩展。";
}

function buildAlgorithmNarrative(detail) {
  if (!detail) {
    return [];
  }
  const summary = detail.summary || {};
  const processSummary = Array.isArray(detail.process_summary) ? detail.process_summary : [];
  const reasonSummary = Array.isArray(detail.unscheduled_reason_summary) ? detail.unscheduled_reason_summary : [];
  const topProcess = processSummary[0];
  const topReason = reasonSummary[0];
  const taskCount = Math.round(toNumber(summary.task_count));
  const targetQty = formatQty(summary.target_qty);
  const scheduledQty = formatQty(summary.scheduled_qty);
  const completionRate = formatPercent(summary.schedule_completion_rate);
  const unscheduledTaskCount = Math.round(toNumber(summary.unscheduled_task_count));
  const strategyCode = normalizeStrategyCode(
    detail.strategy_code || detail?.metadata?.schedule_strategy_code || detail?.metadata?.scheduleStrategyCode
  );
  const strategyLabel = STRATEGY_LABEL[strategyCode] || strategyCode;

  const lines = [];
  lines.push(`本版策略：${strategyLabel}。${strategyNarrativeLine(strategyCode)}`);
  lines.push(`本次共处理 ${taskCount} 个任务，目标量 ${targetQty}，已排 ${scheduledQty}，排产完成率 ${completionRate}。`);
  if (topProcess) {
    lines.push(
      `排产量最大的工序是“${topProcess.process_name_cn || topProcess.process_code || "-"}”，` +
        `该工序本版分配了 ${formatQty(topProcess.scheduled_qty)}。`
    );
  }
  if (unscheduledTaskCount > 0) {
    const reasonText = reasonToText(topReason?.reason_code);
    lines.push(
      `仍有 ${unscheduledTaskCount} 个任务未排产，主要受“${reasonText}”影响` +
        `${topReason ? `（${topReason.count} 条）` : ""}。`
    );
  } else {
    lines.push("当前没有未排产任务，说明在现有约束下已基本排满。");
  }
  return lines;
}

export default function ScheduleVersionsPage() {
  const [rows, setRows] = useState([]);
  const [showDraft, setShowDraft] = useState(false);
  const [compareWith, setCompareWith] = useState("");
  const [diff, setDiff] = useState(null);
  const [algorithmDetail, setAlgorithmDetail] = useState(null);
  const [selectedMaxAllocationRow, setSelectedMaxAllocationRow] = useState(null);
  const [message, setMessage] = useState("");
  const algorithmNarrative = useMemo(() => buildAlgorithmNarrative(algorithmDetail), [algorithmDetail]);
  const processSummaryRows = useMemo(() => {
    const rows = Array.isArray(algorithmDetail?.process_summary) ? algorithmDetail.process_summary : [];
    return rows.map(normalizeProcessSummaryRow);
  }, [algorithmDetail]);

  const visibleRows = useMemo(
    () => (showDraft ? rows : rows.filter((row) => row.status !== "DRAFT")),
    [showDraft, rows]
  );

  async function refresh() {
    const data = await loadList("/internal/v1/internal/schedule-versions");
    setRows(data.items ?? []);
  }

  async function publish(versionNo) {
    await postContract(`/internal/v1/internal/schedule-versions/${versionNo}/publish`, {
      operator: "publisher01",
      reason: "MVP publish"
    });
    setMessage(`已发布版本：${versionNo}`);
    await refresh();
  }

  async function rollback(versionNo) {
    await postContract(`/internal/v1/internal/schedule-versions/${versionNo}/rollback`, {
      operator: "publisher01",
      reason: "MVP rollback simulation"
    });
    setMessage(`已回滚到 ${versionNo}`);
    await refresh();
  }

  async function compare(versionNo) {
    if (!compareWith) {
      setMessage("请先选择对比基线版本。");
      return;
    }
    const [summary, baseTasks, currentTasks] = await Promise.all([
      loadList(`/internal/v1/internal/schedule-versions/${versionNo}/diff?compare_with=${encodeURIComponent(compareWith)}`),
      loadList(`/internal/v1/internal/schedule-versions/${compareWith}/tasks`),
      loadList(`/internal/v1/internal/schedule-versions/${versionNo}/tasks`)
    ]);

    const diffRows = buildDiffRows(baseTasks.items ?? [], currentTasks.items ?? []);
    const affectedOrders = new Set(diffRows.map((row) => row.order_no)).size;
    const netDeltaQty = diffRows.reduce((sum, row) => sum + toNumber(row.delta_qty), 0);
    setDiff({
      baseVersionNo: compareWith,
      compareVersionNo: versionNo,
      summary,
      rows: diffRows,
      affectedOrders,
      netDeltaQty
    });
    setMessage("");
  }

  async function showAlgorithm(versionNo) {
    const detail = await loadList(`/internal/v1/internal/schedule-versions/${versionNo}/algorithm`);
    setAlgorithmDetail(detail);
    setSelectedMaxAllocationRow(null);
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  useEffect(() => {
    if (visibleRows.length === 0) {
      if (compareWith !== "") {
        setCompareWith("");
      }
      return;
    }
    const exists = visibleRows.some((row) => row.version_no === compareWith);
    if (!exists) {
      setCompareWith(visibleRows[0].version_no);
    }
  }, [compareWith, visibleRows]);

  return (
    <section>
      <h2>排产历史</h2>
      <div className="toolbar">
        <label>
          <input type="checkbox" checked={showDraft} onChange={(e) => setShowDraft(e.target.checked)} />
          显示草稿
        </label>
        <select value={compareWith} onChange={(e) => setCompareWith(e.target.value)}>
          <option value="">选择对比基线</option>
          {visibleRows.map((row) => (
            <option key={row.version_no} value={row.version_no}>
              {row.version_no}
            </option>
          ))}
        </select>
      </div>
      {message ? <p className="notice">{message}</p> : null}
      <SimpleTable
        columns={[
          { key: "version_no", title: "版本号" },
          {
            key: "status",
            title: "状态",
            render: (value, row) => row.status_name_cn || STATUS_CN[value] || value || "-"
          },
          {
            key: "rule_version_no",
            title: "规则版本",
            render: (value, row) => row.rule_version_name_cn || RULE_VERSION_CN[value] || value || "-"
          },
          { key: "created_by", title: "创建人" },
          { key: "created_at", title: "创建时间" },
          {
            key: "actions",
            title: "操作",
            render: (_, row) => (
              <div className="row-actions">
                <button onClick={() => compare(row.version_no).catch(() => {})}>差异</button>
                <button onClick={() => showAlgorithm(row.version_no).catch(() => {})}>算法</button>
                <button onClick={() => publish(row.version_no).catch(() => {})}>发布</button>
                <button onClick={() => rollback(row.version_no).catch(() => {})}>回滚</button>
              </div>
            )
          }
        ]}
        rows={visibleRows}
      />
      {diff ? (
        <>
          <div className="panel">
            <h3>差异摘要</h3>
            <p className="hint">
              基线版本：{diff.baseVersionNo}，对比版本：{diff.compareVersionNo}
            </p>
            <div className="card-grid">
              <article className="metric-card">
                <span>变化任务数</span>
                <strong>{diff.summary.changed_task_count ?? diff.rows.length}</strong>
              </article>
              <article className="metric-card">
                <span>受影响生产订单</span>
                <strong>{diff.affectedOrders}</strong>
              </article>
              <article className="metric-card">
                <span>订单出入变化</span>
                <strong>{diff.summary.changed_order_count ?? 0}</strong>
              </article>
              <article className="metric-card">
                <span>计划量净变化</span>
                <strong>{formatSignedQty(diff.netDeltaQty)}</strong>
              </article>
            </div>
            <p className="notice">
              相较 {diff.baseVersionNo}，版本 {diff.compareVersionNo} 共有{" "}
              {diff.summary.changed_task_count ?? diff.rows.length} 条任务发生变化，涉及 {diff.affectedOrders} 个生产订单。
            </p>
          </div>

          <div className="panel">
            <h3>差异明细</h3>
            <SimpleTable
              columns={[
                { key: "order_no", title: "生产订单" },
                { key: "process_name", title: "工序" },
                { key: "calendar_date", title: "日期" },
                { key: "shift_name", title: "班次" },
                { key: "base_qty", title: "基线计划量", render: (value) => formatQty(value) },
                { key: "current_qty", title: "当前计划量", render: (value) => formatQty(value) },
                {
                  key: "delta_qty",
                  title: "变化值",
                  render: (value) => <span className={toNumber(value) >= 0 ? "diff-value-up" : "diff-value-down"}>{formatSignedQty(value)}</span>
                },
                {
                  key: "change_type",
                  title: "变化类型",
                  render: (value) => <span className={changeTypeClass(value)}>{value}</span>
                }
              ]}
              rows={diff.rows}
            />
          </div>

          <details className="json-box">
            <summary>查看原始数据（高级）</summary>
            <pre>{JSON.stringify(formatDiffForView(diff.summary), null, 2)}</pre>
          </details>
        </>
      ) : null}
      {algorithmDetail ? (
        <div className="panel">
          <h3>算法说明（{algorithmDetail.version_no}）</h3>
          <p className="hint">
            策略：
            {algorithmDetail.strategy_name_cn ||
              STRATEGY_LABEL[
                normalizeStrategyCode(
                  algorithmDetail.strategy_code ||
                    algorithmDetail?.metadata?.schedule_strategy_code ||
                    algorithmDetail?.metadata?.scheduleStrategyCode
                )
              ]}
          </p>
          {algorithmNarrative.map((line, index) => (
            <p key={`${algorithmDetail.version_no}-line-${index}`} className={index === 0 ? "notice" : "hint"}>
              {line}
            </p>
          ))}

          <h4>优先级队列预览</h4>
          <SimpleTable
            columns={[
              { key: "order_no", title: "生产订单" },
              { key: "urgent_flag", title: "加急", render: (value) => (Number(value) === 1 ? "是" : "否") },
              { key: "due_date", title: "交期" },
              { key: "status_name_cn", title: "当前状态" }
            ]}
            rows={(algorithmDetail.priority_preview ?? []).slice(0, 8)}
          />

          <h4>工序分配概览</h4>
          <SimpleTable
            columns={[
              { key: "process_name_cn", title: "工序" },
              {
                key: "max_allocation_qty",
                title: "最大配量",
                render: (_, row) => {
                  const qty = toNumber(row.max_allocation_qty);
                  if (qty <= 1e-9) {
                    return "-";
                  }
                  return (
                    <button type="button" className="link-button" onClick={() => setSelectedMaxAllocationRow(row)}>
                      {formatQty(qty)}
                    </button>
                  );
                }
              },
              { key: "target_qty", title: "目标量", render: (value) => formatQty(value) },
              { key: "scheduled_qty", title: "已分配量", render: (value) => formatQty(value) },
              { key: "unscheduled_qty", title: "未排量", render: (value) => formatQty(value) },
              { key: "schedule_rate", title: "完成率", render: (value) => formatPercent(value) },
              { key: "allocation_count", title: "排产条数", render: (value) => formatQty(value) },
              { key: "explain_cn", title: "分配说明", render: (_, row) => processExplainToText(row) }
            ]}
            rows={processSummaryRows.slice(0, 8)}
          />
          {selectedMaxAllocationRow ? (
            <>
              <h4>最大配量说明（{selectedMaxAllocationRow.process_name_cn || selectedMaxAllocationRow.process_code || "-" }）</h4>
              <p className="notice">{maxAllocationExplainToText(selectedMaxAllocationRow)}</p>
              <p className="hint">
                峰值订单：{selectedMaxAllocationRow.max_allocation_order_no || "-"}；峰值班次：
                {selectedMaxAllocationRow.max_allocation_date || "-"}
                {selectedMaxAllocationRow.max_allocation_shift_name_cn || selectedMaxAllocationRow.max_allocation_shift_code || ""}
                ；工序峰值：{formatQty(selectedMaxAllocationRow.max_allocation_qty)}
              </p>
            </>
          ) : (
            <p className="hint">点击“最大配量”数值，可查看该工序为什么会分配到这个峰值。</p>
          )}

          <h4>未排产原因</h4>
          <SimpleTable
            columns={[
              { key: "reason_code", title: "原因编码" },
              { key: "reason_text", title: "原因说明", render: (_, row) => row.reason_name_cn || reasonToText(row.reason_code) },
              { key: "count", title: "任务数", render: (value) => formatQty(value) }
            ]}
            rows={(algorithmDetail.unscheduled_reason_summary ?? []).slice(0, 8)}
          />

          {(algorithmDetail.unscheduled_samples ?? []).length > 0 ? (
            <>
              <h4>未排产样例</h4>
              <SimpleTable
                columns={[
                  { key: "order_no", title: "生产订单" },
                  { key: "process_name_cn", title: "工序" },
                  { key: "remaining_qty", title: "未排数量", render: (value) => formatQty(value) },
                  {
                    key: "reasons",
                    title: "说明",
                    render: (value) =>
                      Array.isArray(value) && value.length > 0 ? value.map((item) => reasonToText(item)).join("；") : "-"
                  }
                ]}
                rows={(algorithmDetail.unscheduled_samples ?? []).slice(0, 8)}
              />
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

