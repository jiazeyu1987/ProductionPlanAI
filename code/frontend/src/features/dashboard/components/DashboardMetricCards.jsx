import { Link } from "react-router-dom";

export function DashboardMetricCards({ stats }) {
  return (
    <div className="card-grid">
      <Link className="metric-card metric-card-link" to="/orders/pool">
        <span>待排生产订单</span>
        <strong>{stats?.orderPool ?? 0}</strong>
      </Link>
      <Link className="metric-card metric-card-link" to="/alerts">
        <span>待处理预警</span>
        <strong>{stats?.openAlerts ?? 0}</strong>
      </Link>
      <Link className="metric-card metric-card-link" to="/schedule/versions">
        <span>版本数</span>
        <strong>{stats?.versions ?? 0}</strong>
      </Link>
      <Link className="metric-card metric-card-link" to="/ops/integration">
        <span>异常投递</span>
        <strong>{stats?.outboxFailed ?? 0}</strong>
      </Link>
    </div>
  );
}

