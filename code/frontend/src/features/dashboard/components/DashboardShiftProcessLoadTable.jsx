import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { formatPercent, formatQty, loadRateStyle } from "../dashboardFormatters";

export function DashboardShiftProcessLoadTable({ rows }) {
  return (
    <SimpleTable
      columns={[
        {
          key: "shift_code",
          title: "班次",
          render: (value, row) => row.shift_name_cn || value || "-"
        },
        {
          key: "process_name_cn",
          title: "工序",
          render: (_, row) =>
            row.process_code ? (
              <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                {row.process_name_cn || row.process_name || row.process_code}
              </Link>
            ) : (
              row.process_name_cn || row.process_name || "-"
            )
        },
        {
          key: "scheduled_qty",
          title: "已安排量",
          render: (value) => formatQty(value)
        },
        {
          key: "max_capacity_qty",
          title: "最大产能",
          render: (value) => formatQty(value)
        },
        {
          key: "load_rate",
          title: "负荷度",
          render: (value) => {
            const style = loadRateStyle(value);
            const text = formatPercent(value);
            return style ? <span style={style}>{text}</span> : text;
          }
        },
        {
          key: "capacity_per_shift",
          title: "每组产能/班",
          render: (value) => formatQty(value)
        },
        {
          key: "required_workers",
          title: "每组需人",
          render: (value) => formatQty(value)
        },
        {
          key: "required_machines",
          title: "每组需机",
          render: (value) => formatQty(value)
        },
        {
          key: "available_workers",
          title: "可用人力",
          render: (value) => formatQty(value)
        },
        {
          key: "available_machines",
          title: "可用设备",
          render: (value) => formatQty(value)
        }
      ]}
      rows={rows || []}
    />
  );
}

