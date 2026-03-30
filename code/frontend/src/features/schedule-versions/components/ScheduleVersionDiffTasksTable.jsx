import SimpleTable from "../../../components/SimpleTable";
import { changeTypeClass } from "../scheduleVersionsDiffUtils";
import { formatQty, formatSignedQty, toNumber } from "../scheduleVersionsFormattersUtils";

export default function ScheduleVersionDiffTasksTable({ rows }) {
  return (
    <SimpleTable
      columns={[
        { key: "order_no", title: "生产订单" },
        { key: "process_name", title: "工序" },
        { key: "calendar_date", title: "日期" },
        { key: "shift_name", title: "班次" },
        { key: "base_qty", title: "基线计划量", render: (value) => formatQty(value) },
        { key: "current_qty", title: "当前计划量", render: (value) => formatQty(value) },
        {
          key: "delta_qty",
          title: "变化值",
          render: (value) => (
            <span className={toNumber(value) >= 0 ? "diff-value-up" : "diff-value-down"}>{formatSignedQty(value)}</span>
          ),
        },
        {
          key: "change_type",
          title: "变化类型",
          render: (value) => <span className={changeTypeClass(value)}>{value}</span>,
        },
      ]}
      rows={rows}
    />
  );
}

