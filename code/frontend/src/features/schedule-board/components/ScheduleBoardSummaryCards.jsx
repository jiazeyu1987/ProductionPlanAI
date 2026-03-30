import { formatQty, reasonToText } from "../scheduleBoardUtils";

export default function ScheduleBoardSummaryCards({ boardSummary }) {
  if (!boardSummary) {
    return null;
  }
  return (
    <div className="card-grid">
      <article className="metric-card">
        <span>参与分配订单</span>
        <strong>{boardSummary.orderCount}</strong>
      </article>
      <article className="metric-card">
        <span>已分配总量</span>
        <strong>{formatQty(boardSummary.totalScheduledQty)}</strong>
      </article>
      <article className="metric-card">
        <span>未排总量</span>
        <strong>{formatQty(boardSummary.totalUnscheduledQty)}</strong>
      </article>
      <article className="metric-card">
        <span>首要瓶颈</span>
        <strong>{reasonToText(boardSummary.topReasonCode)}</strong>
      </article>
    </div>
  );
}

