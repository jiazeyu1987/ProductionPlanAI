import { formatDateTime } from "../../../utils/datetime";

export function SimulationSummaryCards({ state }) {
  return (
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
  );
}

