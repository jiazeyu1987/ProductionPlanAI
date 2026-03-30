import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import ProductCell from "../ProductCell";
import {
  isInProgressStatus,
  isOrderCompleted,
  orderStatusText,
  progressText,
} from "..";
import { formatDateTime } from "../../../utils/datetime";

export default function OrdersPoolOrdersTable({
  rows,
  submitting,
  onCreateCommand,
  onDeleteOrder,
  onMarkCompleted,
}) {
  return (
    <SimpleTable
      columns={[
        {
          key: "order_no",
          title: "生产订单号",
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
        {
          key: "product_name_cn",
          title: "产品",
          render: (value, row) => (
            <ProductCell
              code={row.product_code}
              name={row.product_name || row.product_name_cn || row.product_code}
            />
          ),
        },
        {
          key: "product_code",
          title: "物料编码",
          render: (value) => value || "-",
        },
        { key: "order_qty", title: "数量" },
        {
          key: "expected_start_time",
          title: "预计开始",
          render: (value) => formatDateTime(value) ?? "-",
        },
        {
          key: "expected_finish_time",
          title: "预计完成",
          render: (value) => formatDateTime(value) ?? "-",
        },
        { key: "promised_due_date", title: "承诺交期" },
        {
          key: "urgent_flag",
          title: "加急",
          render: (value) => (Number(value) === 1 ? "是" : "否"),
        },
        {
          key: "lock_flag",
          title: "锁单",
          render: (value) => (Number(value) === 1 ? "是" : "否"),
        },
        {
          key: "frozen_flag",
          title: "冻结",
          render: (value) => (Number(value) === 1 ? "是" : "否"),
        },
        {
          key: "status",
          title: "状态",
          render: (_, row) => orderStatusText(row),
        },
        {
          key: "progress",
          title: "进行中进度",
          render: (_, row) =>
            !isOrderCompleted(row) && isInProgressStatus(row.status)
              ? progressText(row)
              : "-",
        },
        {
          key: "actions",
          title: "操作",
          render: (_, row) => {
            const isFrozen = Number(row.frozen_flag) === 1;
            const isLocked = Number(row.lock_flag) === 1;
            const isUrgent = Number(row.urgent_flag) === 1;
            const isCompleted = isOrderCompleted(row);
            return (
              <div className="row-actions">
                <button
                  className={`order-action-btn ${isLocked ? "order-action-unlock" : "order-action-lock"}`}
                  disabled={submitting || isCompleted || isFrozen}
                  onClick={() =>
                    onCreateCommand(row.order_no, isLocked ? "UNLOCK" : "LOCK")
                  }
                >
                  {isLocked ? "解锁" : "锁定"}
                </button>
                <button
                  className={`order-action-btn ${isUrgent ? "order-action-unpriority" : "order-action-priority"}`}
                  disabled={submitting || isCompleted || isFrozen}
                  onClick={() =>
                    onCreateCommand(
                      row.order_no,
                      isUrgent ? "UNPRIORITY" : "PRIORITY"
                    )
                  }
                >
                  {isUrgent ? "取消" : "提优"}
                </button>
                <button
                  className="order-action-btn order-action-complete"
                  disabled={submitting || isCompleted}
                  onClick={() => onMarkCompleted(row.order_no, row)}
                >
                  完成
                </button>
                <button
                  className="order-action-btn order-action-delete"
                  disabled={submitting}
                  onClick={() => onDeleteOrder(row.order_no)}
                >
                  删除
                </button>
              </div>
            );
          },
        },
      ]}
      rows={rows}
    />
  );
}
