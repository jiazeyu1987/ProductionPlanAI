import { formatDateTime } from "../../../utils/datetime";

export default function PlanReportsConsistencyPanel({ consistency, totalMonthlyRowCount }) {
  if (consistency.consistent) {
    return (
      <p className="notice">
        数据一致性校验通过：周计划与月计划共享字段一致（{totalMonthlyRowCount} 条）。
      </p>
    );
  }

  return (
    <div className="panel">
      <h3>数据一致性告警</h3>
      <p className="error">
        发现 {consistency.issueCount} 处不一致，页面已按月计划字段对齐周计划显示。
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>生产订单号</th>
              <th>字段</th>
              <th>周计划</th>
              <th>月计划</th>
            </tr>
          </thead>
          <tbody>
            {consistency.issues.map((issue, index) => (
              <tr key={`${issue.production_order_no}-${issue.field}-${index}`}>
                <td>{issue.production_order_no || "-"}</td>
                <td>{issue.field}</td>
                <td>{formatDateTime(issue.weekly)}</td>
                <td>{formatDateTime(issue.monthly)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

