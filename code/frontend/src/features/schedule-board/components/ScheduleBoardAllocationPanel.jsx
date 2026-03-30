import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { formatPercent, formatQty } from "../scheduleBoardUtils";

function BottleneckTag({ value }) {
  return <span>{value || "-"}</span>;
}

export default function ScheduleBoardAllocationPanel({
  detailLoading,
  allocationExplainSummary,
  orderAllocationRows,
}) {
  return (
    <div className="panel">
      <h3>分配列表</h3>
      {detailLoading ? <p className="hint">正在切换草稿并刷新分配解释...</p> : null}
      {allocationExplainSummary ? (
        <p className="hint">
          版本 {allocationExplainSummary.version_no}：任务{" "}
          {formatQty(allocationExplainSummary.task_count)} 个，已排{" "}
          {formatQty(allocationExplainSummary.scheduled_qty)}，完成率{" "}
          {formatPercent(allocationExplainSummary.schedule_completion_rate)}。
        </p>
      ) : null}
      {allocationExplainSummary ? (
        <p className="hint">当前策略：{allocationExplainSummary.strategyText}</p>
      ) : null}
      <SimpleTable
        columns={[
          {
            key: "order_no",
            title: "订单",
            render: (value) =>
              value ? (
                <Link
                  className="table-link"
                  to={`/orders/pool?order_no=${encodeURIComponent(value)}`}
                >
                  {value}
                </Link>
              ) : (
                "-"
              ),
          },
          { key: "priority_label", title: "优先级" },
          { key: "lock_label", title: "是否锁单" },
          { key: "scheduled_qty", title: "已分配量", render: (value) => formatQty(value) },
          { key: "resource_share", title: "资源占比", render: (value) => formatPercent(value) },
          { key: "qty_share", title: "计划量占比", render: (value) => formatPercent(value) },
          { key: "unscheduled_qty", title: "未排量", render: (value) => formatQty(value) },
          {
            key: "bottleneck_reason_text",
            title: "主要瓶颈",
            render: (value) => <BottleneckTag value={value} />,
          },
          {
            key: "bottleneck_dimension_text",
            title: "瓶颈维度",
            render: (value) => <BottleneckTag value={value} />,
          },
          {
            key: "explain_cn",
            title: "为什么这么分配",
            render: (value) => <span className="cell-wrap">{value || "-"}</span>,
          },
        ]}
        rows={orderAllocationRows}
      />
    </div>
  );
}

