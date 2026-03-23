import { useEffect, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";
import { formatDateTime } from "../utils/datetime";

const TAB_BATCH = "batch";
const TAB_MANUAL = "manual";
const SHOW_BATCH_SIMULATION = false;

function toNumber(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

const scenarioOptions = [
  { value: "STABLE", label: "稳定" },
  { value: "TIGHT", label: "紧张" },
  { value: "BREAKDOWN", label: "故障" }
];

export default function SimulationPage() {
  const [activeTab, setActiveTab] = useState(SHOW_BATCH_SIMULATION ? TAB_BATCH : TAB_MANUAL);
  const [form, setForm] = useState({
    days: 7,
    daily_sales_order_count: 20,
    scenario: "STABLE",
    seed: 20260322
  });
  const [state, setState] = useState(null);
  const [events, setEvents] = useState([]);
  const [summary, setSummary] = useState(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function refresh() {
    const [stateRes, eventsRes] = await Promise.all([
      loadList("/internal/v1/internal/simulation/state"),
      loadList("/internal/v1/internal/simulation/events?page=1&page_size=100")
    ]);
    setState(stateRes);
    setEvents(eventsRes.items ?? []);
    if (stateRes.last_run_summary && Object.keys(stateRes.last_run_summary).length > 0) {
      setSummary(stateRes.last_run_summary);
    }
  }

  async function runBatch(daysOverride) {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const payload = {
        days: daysOverride ?? toNumber(form.days, 7),
        daily_sales_order_count: toNumber(form.daily_sales_order_count, 20),
        scenario: form.scenario,
        seed: toNumber(form.seed, 20260322)
      };
      const res = await postContract("/internal/v1/internal/simulation/run", payload);
      setSummary(res);
      setMessage("批量仿真执行完成。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function resetBatch() {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      await postContract("/internal/v1/internal/simulation/reset", {
        seed: toNumber(form.seed, 20260322),
        scenario: form.scenario,
        daily_sales_order_count: toNumber(form.daily_sales_order_count, 20)
      });
      setSummary(null);
      setMessage("批量仿真状态已重置。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function addManualProductionOrder() {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await postContract("/internal/v1/internal/simulation/manual/add-production-order", {});
      setMessage(`已新增生产订单：${res.production_order_no}`);
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function advanceManualOneDay() {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await postContract("/internal/v1/internal/simulation/manual/advance-day", {});
      setSummary(res);
      setMessage("手动模拟已推进1天，并完成当日排产与报工推进。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function resetManualSimulation() {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await postContract("/internal/v1/internal/simulation/manual/reset", {});
      setSummary(res.last_run_summary && Object.keys(res.last_run_summary).length > 0 ? res.last_run_summary : null);
      setMessage(res.message || "已恢复到手动模拟前的数据状态。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh().catch((e) => setError(e.message));
  }, []);

  return (
    <section>
      <h2>仿真平台</h2>

      <div className="report-tabs" role="tablist" aria-label="仿真页签">
        <button
          role="tab"
          aria-selected={activeTab === TAB_MANUAL}
          className={activeTab === TAB_MANUAL ? "active" : ""}
          onClick={() => setActiveTab(TAB_MANUAL)}
        >
          手动模拟
        </button>
      </div>

      {SHOW_BATCH_SIMULATION && activeTab === TAB_BATCH ? (
        <>
          <div className="toolbar">
            <label>
              天数
              <input
                type="number"
                min="1"
                max="30"
                value={form.days}
                onChange={(e) => setForm((prev) => ({ ...prev, days: e.target.value }))}
              />
            </label>
            <label>
              每日接单
              <input
                type="number"
                min="1"
                max="500"
                value={form.daily_sales_order_count}
                onChange={(e) => setForm((prev) => ({ ...prev, daily_sales_order_count: e.target.value }))}
              />
            </label>
            <label>
              场景
              <select value={form.scenario} onChange={(e) => setForm((prev) => ({ ...prev, scenario: e.target.value }))}>
                {scenarioOptions.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              随机种子
              <input
                type="number"
                value={form.seed}
                onChange={(e) => setForm((prev) => ({ ...prev, seed: e.target.value }))}
              />
            </label>
          </div>
          <div className="toolbar">
            <button disabled={loading} onClick={() => runBatch(1)}>
              {loading ? "执行中..." : "快进1天"}
            </button>
            <button disabled={loading} onClick={() => runBatch(7)}>
              {loading ? "执行中..." : "快进7天"}
            </button>
            <button disabled={loading} onClick={() => runBatch(30)}>
              {loading ? "执行中..." : "快进30天"}
            </button>
            <button disabled={loading} onClick={() => runBatch(undefined)}>
              {loading ? "执行中..." : "按参数运行"}
            </button>
            <button disabled={loading} onClick={() => resetBatch()}>
              {loading ? "执行中..." : "重置仿真"}
            </button>
            <button disabled={loading} onClick={() => refresh().catch((e) => setError(e.message))}>
              刷新
            </button>
          </div>
        </>
      ) : (
        <div className="toolbar">
          <button disabled={loading} onClick={() => addManualProductionOrder()}>
            {loading ? "执行中..." : "虚增生产订单"}
          </button>
          <button disabled={loading} onClick={() => advanceManualOneDay()}>
            {loading ? "执行中..." : "推进一天"}
          </button>
          <button disabled={loading} onClick={() => resetManualSimulation()}>
            {loading ? "执行中..." : "重置模拟"}
          </button>
          <button disabled={loading} onClick={() => refresh().catch((e) => setError(e.message))}>
            刷新
          </button>
        </div>
      )}

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      <div className="card-grid">
        <article className="metric-card">
          <span>当前仿真日</span>
          <strong>{formatDateTime(state?.current_sim_date) ?? "-"}</strong>
        </article>
        <article className="metric-card">
          <span>销售订单总数</span>
          <strong>{state?.sales_order_total ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>生产订单总数</span>
          <strong>{state?.production_order_total ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>最新版本</span>
          <strong>{state?.latest_version_no ?? "-"}</strong>
        </article>
      </div>

      <div className="panel">
        <h3>最近一次运行摘要</h3>
        <SimpleTable
          columns={[
            { key: "new_sales_orders", title: "新增销售单" },
            { key: "new_production_orders", title: "新增生产单" },
            { key: "generated_versions", title: "新增版本" },
            { key: "reporting_count", title: "报工条数" },
            { key: "delayed_orders", title: "延期订单" },
            { key: "avg_capacity_factor", title: "平均产能系数" }
          ]}
          rows={summary ? [summary] : []}
        />
      </div>

      <div className="panel">
        <h3>仿真事件</h3>
        <SimpleTable
          columns={[
            { key: "event_date", title: "日期" },
            {
              key: "event_type",
              title: "事件类型",
              render: (value, row) => row.event_type_name_cn || value || "-"
            },
            {
              key: "message",
              title: "说明",
              render: (value, row) => row.message_cn || value
            },
            { key: "sales_order_no", title: "销售单" },
            { key: "production_order_no", title: "生产单" },
            { key: "version_no", title: "版本" },
            {
              key: "process_code",
              title: "工序",
              render: (value, row) => row.process_name_cn || value || "-"
            }
          ]}
          rows={events}
        />
      </div>
    </section>
  );
}

