import ScheduleVersionDiffTasksTable from "./ScheduleVersionDiffTasksTable";
import { formatDiffForView, formatSignedQty } from "../scheduleVersionsFormattersUtils";

export default function ScheduleVersionDiffPanel({ diff }) {
  if (!diff) {
    return null;
  }

  return (
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
        <ScheduleVersionDiffTasksTable rows={diff.rows} />
      </div>

      <details className="json-box">
        <summary>查看原始数据（高级）</summary>
        <pre>{JSON.stringify(formatDiffForView(diff.summary), null, 2)}</pre>
      </details>
    </>
  );
}

