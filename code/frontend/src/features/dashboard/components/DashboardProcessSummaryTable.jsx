import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { formatPercent, formatQty } from "../dashboardFormatters";

export function DashboardProcessSummaryTable({ rows }) {
  return (
    <SimpleTable
      columns={[
        {
          key: "process_name",
          title: "工序",
          render: (_, row) =>
            row.process_code ? (
              <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                {row.process_name}
              </Link>
            ) : (
              row.process_name || "-"
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
          render: (value) => formatPercent(value)
        }
      ]}
      rows={rows || []}
    />
  );
}

