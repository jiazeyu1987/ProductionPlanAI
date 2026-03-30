import { scenarioOptions, toNumber } from "../simulationUtils";

export function SimulationBatchPanel({
  loading,
  form,
  onChangeForm,
  onRunBatch,
  onResetBatch,
  onRefresh
}) {
  return (
    <>
      <div className="toolbar">
        <label>
          天数
          <input
            type="number"
            min="1"
            max="30"
            value={form.days}
            onChange={(e) => onChangeForm((prev) => ({ ...prev, days: e.target.value }))}
          />
        </label>
        <label>
          每日接单
          <input
            type="number"
            min="1"
            max="500"
            value={form.daily_sales_order_count}
            onChange={(e) => onChangeForm((prev) => ({ ...prev, daily_sales_order_count: e.target.value }))}
          />
        </label>
        <label>
          场景
          <select
            value={form.scenario}
            onChange={(e) => onChangeForm((prev) => ({ ...prev, scenario: e.target.value }))}
          >
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
            onChange={(e) => onChangeForm((prev) => ({ ...prev, seed: e.target.value }))}
          />
        </label>
      </div>
      <div className="toolbar">
        <button disabled={loading} onClick={() => onRunBatch(1)}>
          {loading ? "执行中..." : "快进1天"}
        </button>
        <button disabled={loading} onClick={() => onRunBatch(7)}>
          {loading ? "执行中..." : "快进7天"}
        </button>
        <button disabled={loading} onClick={() => onRunBatch(30)}>
          {loading ? "执行中..." : "快进30天"}
        </button>
        <button disabled={loading} onClick={() => onRunBatch(undefined)}>
          {loading ? "执行中..." : "按参数运行"}
        </button>
        <button disabled={loading} onClick={onResetBatch}>
          {loading ? "执行中..." : "重置仿真"}
        </button>
        <button disabled={loading} onClick={onRefresh}>
          刷新
        </button>
      </div>
      <p className="hint">
        当前 seed：{toNumber(form.seed, 20260322)}；场景：{form.scenario}；每日接单：
        {toNumber(form.daily_sales_order_count, 20)}。
      </p>
    </>
  );
}

