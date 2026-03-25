import { useEffect, useMemo, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import {
  addDays,
  advanceLiteScenarioOneDay,
  buildLiteSchedule,
  createDefaultLiteScenario,
  normalizeLiteScenario
} from "../utils/liteSchedulerEngine";

const STORAGE_KEY = "liteScheduler.scenario.v1";

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function formatNumber(value, digits = 1) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return n.toFixed(digits);
}

function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return `${(n * 100).toFixed(1)}%`;
}

function loadScenario() {
  if (typeof window === "undefined") {
    return createDefaultLiteScenario();
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return createDefaultLiteScenario();
    }
    return normalizeLiteScenario(JSON.parse(raw));
  } catch {
    return createDefaultLiteScenario();
  }
}

function saveScenario(scenario) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(scenario));
}

function downloadJson(fileName, payload) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function LiteSchedulerPage() {
  const [scenario, setScenario] = useState(loadScenario);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [lineForm, setLineForm] = useState({ name: "", baseCapacity: "1" });
  const [orderForm, setOrderForm] = useState({
    orderNo: "",
    workloadDays: "1",
    dueDate: "",
    releaseDate: "",
    priority: "NORMAL"
  });
  const [capacityForm, setCapacityForm] = useState({
    lineId: "",
    date: "",
    capacity: "1"
  });
  const [lockForm, setLockForm] = useState({
    orderId: "",
    lineId: "",
    startDate: "",
    endDate: "",
    workloadDays: "1"
  });

  const plan = useMemo(() => buildLiteSchedule(scenario), [scenario]);

  useEffect(() => {
    saveScenario(scenario);
  }, [scenario]);

  useEffect(() => {
    setOrderForm((prev) => ({
      ...prev,
      releaseDate: prev.releaseDate || scenario.horizonStart,
      dueDate: prev.dueDate || addDays(scenario.horizonStart, 7)
    }));

    setCapacityForm((prev) => ({
      ...prev,
      lineId: scenario.lines.find((row) => row.id === prev.lineId)?.id || scenario.lines[0]?.id || "",
      date: prev.date || scenario.horizonStart
    }));

    setLockForm((prev) => ({
      ...prev,
      lineId: scenario.lines.find((row) => row.id === prev.lineId)?.id || scenario.lines[0]?.id || "",
      orderId: scenario.orders.find((row) => row.id === prev.orderId)?.id || scenario.orders[0]?.id || "",
      startDate: prev.startDate || scenario.horizonStart,
      endDate: prev.endDate || addDays(scenario.horizonStart, 2)
    }));
  }, [scenario.horizonStart, scenario.lines, scenario.orders]);

  useEffect(() => {
    if (!scenario.lines.find((row) => row.id === capacityForm.lineId)) {
      setCapacityForm((prev) => ({ ...prev, lineId: scenario.lines[0]?.id || "" }));
    }
    if (!scenario.lines.find((row) => row.id === lockForm.lineId)) {
      setLockForm((prev) => ({ ...prev, lineId: scenario.lines[0]?.id || "" }));
    }
    if (!scenario.orders.find((row) => row.id === lockForm.orderId)) {
      setLockForm((prev) => ({ ...prev, orderId: scenario.orders[0]?.id || "" }));
    }
  }, [scenario.lines, scenario.orders, capacityForm.lineId, lockForm.lineId, lockForm.orderId]);

  function applyScenario(mutator, successMessage) {
    setError("");
    setMessage("");
    try {
      setScenario((prev) => {
        const nextRaw = mutator(normalizeLiteScenario(prev));
        return normalizeLiteScenario(nextRaw);
      });
      if (successMessage) {
        setMessage(successMessage);
      }
    } catch (e) {
      setError(e.message || "操作失败");
    }
  }

  function addLine() {
    const name = String(lineForm.name || "").trim() || `产线-${scenario.lines.length + 1}`;
    const baseCapacity = Math.max(0, toNumber(lineForm.baseCapacity, 1));
    applyScenario(
      (prev) => ({
        ...prev,
        lines: [
          ...prev.lines,
          {
            id: makeId("line"),
            name,
            baseCapacity,
            capacityOverrides: {},
            enabled: true
          }
        ]
      }),
      `已新增产线：${name}`
    );
    setLineForm({ name: "", baseCapacity: String(baseCapacity || 1) });
  }

  function removeLine(lineId) {
    if (scenario.lines.length <= 1) {
      setError("至少保留一条产线。");
      return;
    }
    const relatedLocks = scenario.locks.filter((lock) => lock.lineId === lineId);
    if (relatedLocks.length > 0) {
      setError("该产线存在锁定片段，请先删除锁定后再移除产线。");
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.filter((line) => line.id !== lineId)
      }),
      "产线已移除。"
    );
  }

  function updateLineBaseCapacity(lineId, nextValue) {
    const baseCapacity = Math.max(0, toNumber(nextValue, 0));
    applyScenario((prev) => ({
      ...prev,
      lines: prev.lines.map((line) => {
        if (line.id !== lineId) {
          return line;
        }
        return {
          ...line,
          baseCapacity
        };
      })
    }));
  }

  function addOrder() {
    const orderNo = String(orderForm.orderNo || "").trim() || `PO-${Date.now().toString().slice(-6)}`;
    const workloadDays = Math.max(0.1, toNumber(orderForm.workloadDays, 1));
    const dueDate = orderForm.dueDate || addDays(scenario.horizonStart, 7);
    const releaseDate = orderForm.releaseDate || scenario.horizonStart;
    applyScenario(
      (prev) => ({
        ...prev,
        orders: [
          ...prev.orders,
          {
            id: makeId("order"),
            orderNo,
            workloadDays,
            completedDays: 0,
            dueDate,
            releaseDate,
            priority: orderForm.priority === "URGENT" ? "URGENT" : "NORMAL"
          }
        ]
      }),
      `已新增订单：${orderNo}`
    );
    setOrderForm((prev) => ({
      ...prev,
      orderNo: "",
      workloadDays: "1"
    }));
  }

  function removeOrder(orderId) {
    const hasLock = scenario.locks.some((lock) => lock.orderId === orderId);
    if (hasLock) {
      setError("该订单存在锁定片段，请先删除锁定后再删除订单。");
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        orders: prev.orders.filter((order) => order.id !== orderId)
      }),
      "订单已删除。"
    );
  }

  function applyDailyCapacity() {
    if (!capacityForm.lineId || !capacityForm.date) {
      setError("请先选择产线和日期。");
      return;
    }
    const cap = Math.max(0, toNumber(capacityForm.capacity, 0));
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.map((line) => {
          if (line.id !== capacityForm.lineId) {
            return line;
          }
          return {
            ...line,
            capacityOverrides: {
              ...line.capacityOverrides,
              [capacityForm.date]: cap
            }
          };
        })
      }),
      "已更新日产能。"
    );
  }

  function clearDailyCapacity() {
    if (!capacityForm.lineId || !capacityForm.date) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.map((line) => {
          if (line.id !== capacityForm.lineId) {
            return line;
          }
          const nextOverrides = { ...line.capacityOverrides };
          delete nextOverrides[capacityForm.date];
          return {
            ...line,
            capacityOverrides: nextOverrides
          };
        })
      }),
      "已清除当日产能覆盖值。"
    );
  }

  function addLock() {
    if (!lockForm.orderId || !lockForm.lineId) {
      setError("请先选择订单和产线。");
      return;
    }
    const startDate = lockForm.startDate || scenario.horizonStart;
    const endDate = lockForm.endDate || startDate;
    const workloadDays = Math.max(0.1, toNumber(lockForm.workloadDays, 0));
    applyScenario(
      (prev) => ({
        ...prev,
        locks: [
          ...prev.locks,
          {
            id: makeId("lock"),
            orderId: lockForm.orderId,
            lineId: lockForm.lineId,
            startDate,
            endDate,
            workloadDays,
            seq: prev.locks.length + 1
          }
        ]
      }),
      "已新增固定时段锁定，并完成自动重排。"
    );
  }

  function removeLock(lockId) {
    applyScenario(
      (prev) => ({
        ...prev,
        locks: prev.locks.filter((lock) => lock.id !== lockId)
      }),
      "锁定片段已删除并自动重排。"
    );
  }

  function advanceOneDay() {
    setError("");
    setMessage("");
    try {
      const result = advanceLiteScenarioOneDay(scenario);
      setScenario(result.nextScenario);
      setMessage(result.daySummary.message);
    } catch (e) {
      setError(e.message || "推进失败");
    }
  }

  function resetScenario() {
    const today = scenario.horizonStart;
    setScenario(createDefaultLiteScenario(today));
    setMessage("已重置为简版默认场景。");
    setError("");
  }

  function updateHorizonDays(days) {
    applyScenario((prev) => ({ ...prev, horizonDays: Math.max(1, Math.min(120, Math.round(days))) }));
  }

  function exportScenario() {
    downloadJson(`lite-schedule-${scenario.horizonStart}.json`, scenario);
    setMessage("当前场景已导出。");
  }

  async function importScenario(event) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setError("");
    setMessage("");
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      setScenario(normalizeLiteScenario(parsed));
      setMessage("场景导入成功。");
    } catch (e) {
      setError(e.message || "导入失败，请检查 JSON 格式。");
    } finally {
      event.target.value = "";
    }
  }

  const lineRows = plan.lineRows.map((row) => ({
    id: row.lineId,
    line_name: row.lineName,
    base_capacity: scenario.lines.find((line) => line.id === row.lineId)?.baseCapacity ?? 0,
    assigned_total: row.assignedTotal,
    capacity_total: row.capacityTotal,
    utilization: row.utilization
  }));

  const orderRows = plan.orderRows.map((row) => ({
    id: row.id,
    order_no: row.orderNo,
    workload_days: row.workloadDays,
    completed_days: row.completedDays,
    remaining_days: row.remainingDays,
    due_date: row.dueDate,
    release_date: row.releaseDate,
    completion_date: row.completionDate || "-",
    delay_days: row.delayDays ?? 0,
    reason: row.reason,
    priority: row.priority === "URGENT" ? "加急" : "常规"
  }));

  const lockRows = scenario.locks.map((lock) => {
    const order = scenario.orders.find((row) => row.id === lock.orderId);
    const line = scenario.lines.find((row) => row.id === lock.lineId);
    return {
      id: lock.id,
      order_no: order?.orderNo || lock.orderId,
      line_name: line?.name || lock.lineId,
      start_date: lock.startDate,
      end_date: lock.endDate,
      workload_days: lock.workloadDays
    };
  });

  const capacityOverrideRows = scenario.lines.flatMap((line) => {
    return Object.entries(line.capacityOverrides).map(([date, value]) => ({
      id: `${line.id}-${date}`,
      line_name: line.name,
      date,
      capacity: value
    }));
  });

  return (
    <section>
      <h2>简版排产</h2>
      <p className="hint">
        规则：手动锁定（产线+日期区间）优先，其余按交期最小延期优先自动重排；订单支持跨产线拆分。
      </p>

      <div className="toolbar">
        <label>
          排产起始日
          <input
            type="date"
            value={scenario.horizonStart}
            onChange={(e) => applyScenario((prev) => ({ ...prev, horizonStart: e.target.value }))}
          />
        </label>
        <label>
          周期(天)
          <input
            type="number"
            min="1"
            max="120"
            value={scenario.horizonDays}
            onChange={(e) => updateHorizonDays(toNumber(e.target.value, 30))}
          />
        </label>
        <button onClick={advanceOneDay}>推进1天</button>
        <button onClick={exportScenario}>导出场景</button>
        <label className="lite-file-label">
          导入场景
          <input type="file" accept="application/json" onChange={importScenario} />
        </label>
        <button onClick={resetScenario}>重置默认</button>
      </div>

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}
      {plan.warnings.length > 0 ? (
        <div className="panel lite-warning">
          <h3>重排提醒</h3>
          <ul className="lite-warning-list">
            {plan.warnings.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="card-grid">
        <article className="metric-card">
          <span>订单数</span>
          <strong>{plan.summary.totalOrders}</strong>
        </article>
        <article className="metric-card">
          <span>总分配 / 总产能</span>
          <strong>
            {formatNumber(plan.summary.totalAssigned)} / {formatNumber(plan.summary.totalCapacity)}
          </strong>
        </article>
        <article className="metric-card">
          <span>平均利用率</span>
          <strong>{formatPercent(plan.summary.utilization)}</strong>
        </article>
        <article className="metric-card">
          <span>延期订单 / 剩余工作量</span>
          <strong>
            {plan.summary.delayedOrders} / {formatNumber(plan.summary.totalRemaining)}
          </strong>
        </article>
      </div>

      <div className="panel">
        <h3>产线管理</h3>
        <div className="toolbar">
          <label>
            产线名称
            <input
              placeholder="例如：导管产线-2"
              value={lineForm.name}
              onChange={(e) => setLineForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            默认日产能(天)
            <input
              type="number"
              min="0"
              step="0.1"
              value={lineForm.baseCapacity}
              onChange={(e) => setLineForm((prev) => ({ ...prev, baseCapacity: e.target.value }))}
            />
          </label>
          <button onClick={addLine}>新增产线</button>
        </div>
        <SimpleTable
          columns={[
            { key: "line_name", title: "产线" },
            {
              key: "base_capacity",
              title: "默认日产能",
              render: (value, row) => (
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  defaultValue={String(value)}
                  onBlur={(e) => updateLineBaseCapacity(row.id, e.target.value)}
                />
              )
            },
            { key: "assigned_total", title: "周期内分配", render: (value) => formatNumber(value) },
            { key: "capacity_total", title: "周期内最大产能", render: (value) => formatNumber(value) },
            { key: "utilization", title: "利用率", render: (value) => formatPercent(value) },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => <button onClick={() => removeLine(row.id)}>删除</button>
            }
          ]}
          rows={lineRows}
        />
      </div>

      <div className="panel">
        <h3>日产能调整</h3>
        <div className="toolbar">
          <label>
            产线
            <select
              value={capacityForm.lineId}
              onChange={(e) => setCapacityForm((prev) => ({ ...prev, lineId: e.target.value }))}
            >
              {scenario.lines.map((line) => (
                <option key={line.id} value={line.id}>
                  {line.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            日期
            <input
              type="date"
              value={capacityForm.date}
              onChange={(e) => setCapacityForm((prev) => ({ ...prev, date: e.target.value }))}
            />
          </label>
          <label>
            产能(天)
            <input
              type="number"
              min="0"
              step="0.1"
              value={capacityForm.capacity}
              onChange={(e) => setCapacityForm((prev) => ({ ...prev, capacity: e.target.value }))}
            />
          </label>
          <button onClick={applyDailyCapacity}>保存覆盖</button>
          <button onClick={clearDailyCapacity}>清除覆盖</button>
        </div>
        <SimpleTable
          columns={[
            { key: "line_name", title: "产线" },
            { key: "date", title: "日期", render: (value) => String(value || "-") },
            { key: "capacity", title: "覆盖产能(天)", render: (value) => formatNumber(value) }
          ]}
          rows={capacityOverrideRows}
        />
      </div>

      <div className="panel">
        <h3>订单录入</h3>
        <div className="toolbar">
          <label>
            订单号
            <input
              placeholder="手动输入"
              value={orderForm.orderNo}
              onChange={(e) => setOrderForm((prev) => ({ ...prev, orderNo: e.target.value }))}
            />
          </label>
          <label>
            工作量(天)
            <input
              type="number"
              min="0.1"
              step="0.1"
              value={orderForm.workloadDays}
              onChange={(e) => setOrderForm((prev) => ({ ...prev, workloadDays: e.target.value }))}
            />
          </label>
          <label>
            最早开始
            <input
              type="date"
              value={orderForm.releaseDate}
              onChange={(e) => setOrderForm((prev) => ({ ...prev, releaseDate: e.target.value }))}
            />
          </label>
          <label>
            交期
            <input
              type="date"
              value={orderForm.dueDate}
              onChange={(e) => setOrderForm((prev) => ({ ...prev, dueDate: e.target.value }))}
            />
          </label>
          <label>
            优先级
            <select
              value={orderForm.priority}
              onChange={(e) => setOrderForm((prev) => ({ ...prev, priority: e.target.value }))}
            >
              <option value="NORMAL">常规</option>
              <option value="URGENT">加急</option>
            </select>
          </label>
          <button onClick={addOrder}>新增订单</button>
        </div>
        <SimpleTable
          columns={[
            { key: "order_no", title: "订单号" },
            { key: "priority", title: "优先级" },
            { key: "workload_days", title: "总工作量(天)", render: (value) => formatNumber(value) },
            { key: "completed_days", title: "已完工(天)", render: (value) => formatNumber(value) },
            { key: "remaining_days", title: "剩余(天)", render: (value) => formatNumber(value) },
            { key: "release_date", title: "最早开始", render: (value) => String(value || "-") },
            { key: "due_date", title: "交期", render: (value) => String(value || "-") },
            { key: "completion_date", title: "预计完成", render: (value) => String(value || "-") },
            { key: "delay_days", title: "预计延期(天)" },
            { key: "reason", title: "分配说明" },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => <button onClick={() => removeOrder(row.id)}>删除</button>
            }
          ]}
          rows={orderRows}
        />
      </div>

      <div className="panel">
        <h3>固定时段锁定并重排</h3>
        <div className="toolbar">
          <label>
            订单
            <select
              value={lockForm.orderId}
              onChange={(e) => setLockForm((prev) => ({ ...prev, orderId: e.target.value }))}
            >
              <option value="">请选择</option>
              {scenario.orders.map((order) => (
                <option key={order.id} value={order.id}>
                  {order.orderNo}
                </option>
              ))}
            </select>
          </label>
          <label>
            产线
            <select
              value={lockForm.lineId}
              onChange={(e) => setLockForm((prev) => ({ ...prev, lineId: e.target.value }))}
            >
              {scenario.lines.map((line) => (
                <option key={line.id} value={line.id}>
                  {line.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            开始
            <input
              type="date"
              value={lockForm.startDate}
              onChange={(e) => setLockForm((prev) => ({ ...prev, startDate: e.target.value }))}
            />
          </label>
          <label>
            结束
            <input
              type="date"
              value={lockForm.endDate}
              onChange={(e) => setLockForm((prev) => ({ ...prev, endDate: e.target.value }))}
            />
          </label>
          <label>
            锁定工作量(天)
            <input
              type="number"
              min="0.1"
              step="0.1"
              value={lockForm.workloadDays}
              onChange={(e) => setLockForm((prev) => ({ ...prev, workloadDays: e.target.value }))}
            />
          </label>
          <button onClick={addLock}>锁定并重排</button>
        </div>
        <SimpleTable
          columns={[
            { key: "order_no", title: "订单号" },
            { key: "line_name", title: "产线" },
            { key: "start_date", title: "开始", render: (value) => String(value || "-") },
            { key: "end_date", title: "结束", render: (value) => String(value || "-") },
            { key: "workload_days", title: "工作量(天)", render: (value) => formatNumber(value) },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => <button onClick={() => removeLock(row.id)}>删除</button>
            }
          ]}
          rows={lockRows}
        />
      </div>

      <div className="panel">
        <h3>每日工作安排（按产线）</h3>
        <p className="hint">
          瓶颈产线：{plan.summary.bottleneckLineName || "-"}。每个单元显示“已分配/最大产能”，下方是订单分配。
        </p>
        <div className="lite-timeline-wrap">
          <table className="lite-timeline-table">
            <thead>
              <tr>
                <th>产线</th>
                {plan.dates.map((date) => (
                  <th key={date}>{date.slice(5)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {plan.lineRows.map((line) => (
                <tr key={line.lineId}>
                  <th>
                    <div>{line.lineName}</div>
                    <div className="hint">
                      {formatNumber(line.assignedTotal)} / {formatNumber(line.capacityTotal)}
                    </div>
                  </th>
                  {plan.dates.map((date) => {
                    const cell = line.daily[date];
                    return (
                      <td key={`${line.lineId}-${date}`}>
                        <div className="lite-cell-cap">
                          {formatNumber(cell.assigned)} / {formatNumber(cell.capacity)}
                        </div>
                        <div className="lite-chip-list">
                          {cell.items.slice(0, 3).map((item) => {
                            const order = scenario.orders.find((row) => row.id === item.orderId);
                            const label = order?.orderNo || item.orderId;
                            return (
                              <span
                                key={item.id}
                                className={`lite-chip ${item.source === "LOCKED" ? "lite-chip-locked" : ""}`}
                                title={`${label} ${formatNumber(item.workloadDays)}天`}
                              >
                                {label}:{formatNumber(item.workloadDays)}
                              </span>
                            );
                          })}
                          {cell.items.length > 3 ? <span className="lite-chip">+{cell.items.length - 3}</span> : null}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
