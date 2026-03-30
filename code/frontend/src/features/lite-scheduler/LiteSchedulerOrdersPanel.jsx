import SimpleTable from "../../components/SimpleTable";
import { formatNumber } from "./numberFormatUtils";

export function LiteSchedulerOrdersPanel({
  active,
  isDurationMode,
  orderRows,
  openOrderModal,
  openInsertModal,
  openEditOrderModal,
  removeOrder,
}) {
  if (!active) {
    return null;
  }

  return (
    <div className="panel">
      <h3>订单录入</h3>
      <div className="toolbar">
        <button data-testid="open-order-modal" onClick={openOrderModal}>
          新增订单
        </button>
        <span className="hint">
          {isDurationMode
            ? "弹框里每条产线可填写计划天数与计划个数（个数仅展示，不参与排产计算），可在日历手动报结束。"
            : "弹框里每条产线填写的是该订单在该产线的总工作量，不是日工作量。"}
        </span>
      </div>

      <SimpleTable
        columns={[
          { key: "order_no", title: "订单号" },
          { key: "product_name", title: "产品名称" },
          { key: "spec", title: "规格" },
          { key: "batch_no", title: "批号" },
          {
            key: "workload_qty",
            title: isDurationMode ? "计划天数(天)" : "总工作量(个)",
            render: (value) => formatNumber(value),
          },
          ...(isDurationMode
            ? [
                {
                  key: "planned_qty",
                  title: "计划个数(个)",
                  render: (value) => formatNumber(value),
                },
              ]
            : []),
          ...(isDurationMode
            ? []
            : [
                {
                  key: "completed_qty",
                  title: "已完成（个）",
                  render: (value) => formatNumber(value),
                },
                {
                  key: "remaining_qty",
                  title: "未排量（个）",
                  render: (value) => formatNumber(value),
                },
              ]),
          ...(isDurationMode
            ? []
            : [
                {
                  key: "remaining_plan_days",
                  title: "约需天数",
                  render: (value) => formatNumber(value),
                },
              ]),
          ...(isDurationMode
            ? [
                { key: "finish_status", title: "结束状态" },
                {
                  key: "actual_finish_date",
                  title: "实际结束时间",
                  render: (value) => String(value || "-"),
                },
              ]
            : [
                {
                  key: "completion_date",
                  title: "预计完成",
                  render: (value) => String(value || "-"),
                },
              ]),
          {
            key: "actions",
            title: "操作",
            render: (_, row) => (
              <div className="row-actions">
                <button onClick={() => openInsertModal(row.id)}>插入</button>
                <button onClick={() => openEditOrderModal(row.id)}>编辑</button>
                <button
                  className="btn-danger-text"
                  onClick={() => removeOrder(row.id)}
                >
                  删除
                </button>
              </div>
            ),
          },
        ]}
        rows={orderRows}
      />
    </div>
  );
}
