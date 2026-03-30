import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { formatQty } from "../scheduleBoardUtils";

export default function ScheduleBoardTasksPanel({ tasks, affectedTaskCount }) {
  return (
    <div className="panel">
      <h3>任务明细</h3>
      <p className="hint">受影响任务：{affectedTaskCount} 条。</p>
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
          {
            key: "process_code",
            title: "工序",
            render: (value, row) => {
              const processCode = value || "";
              const processName = row.process_name_cn || processCode || "-";
              if (!processCode) {
                return processName;
              }
              return (
                <Link
                  className="table-link"
                  to={`/masterdata?process_code=${encodeURIComponent(processCode)}`}
                >
                  {processName}
                </Link>
              );
            },
          },
          { key: "calendar_date", title: "日期" },
          {
            key: "shift_code",
            title: "班次",
            render: (value, row) => row.shift_name_cn || value || "-",
          },
          { key: "plan_qty", title: "计划量", render: (value) => formatQty(value) },
        ]}
        rows={tasks}
      />
    </div>
  );
}

