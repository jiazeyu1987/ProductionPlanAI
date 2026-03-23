import { useEffect, useState } from "react";
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

export default function DashboardPage() {
  const [stats, setStats] = useState({
    orderPool: 0,
    openAlerts: 0,
    versions: 0,
    outboxFailed: 0
  });
  const [mustHandle, setMustHandle] = useState([]);

  useEffect(() => {
    let active = true;
    async function load() {
      const [orders, alerts, versions, outbox] = await Promise.all([
        loadList("/internal/v1/internal/order-pool"),
        loadList("/internal/v1/internal/alerts?status=OPEN"),
        loadList("/internal/v1/internal/schedule-versions"),
        loadList("/internal/v1/internal/integration/outbox")
      ]);
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
    </section>
  );
}

