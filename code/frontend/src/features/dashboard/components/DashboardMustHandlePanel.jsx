import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { ALERT_TYPE_CN, SEVERITY_CN, STATUS_CN } from "../dashboardConstants";

export function DashboardMustHandlePanel({ rows }) {
  return (
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
        rows={rows || []}
      />
    </div>
  );
}

