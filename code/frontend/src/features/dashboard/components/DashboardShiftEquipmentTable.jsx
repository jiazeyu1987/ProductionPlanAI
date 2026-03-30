import { Link } from "react-router-dom";
import SimpleTable from "../../../components/SimpleTable";
import { formatQty } from "../dashboardFormatters";

export function DashboardShiftEquipmentTable({ rows }) {
  return (
    <SimpleTable
      columns={[
        {
          key: "process_name_cn",
          title: "工序",
          render: (_, row) =>
            row.process_code ? (
              <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                {row.process_name_cn || row.process_code}
              </Link>
            ) : (
              row.process_name_cn || "-"
            )
        },
        {
          key: "needed_machines",
          title: "需设备数",
          render: (value) => formatQty(value)
        },
        {
          key: "available_machines",
          title: "可选设备数",
          render: (value) => formatQty(value)
        },
        {
          key: "equipment_group_summary",
          title: "车间/产线分组",
          render: (value) => <span className="cell-wrap">{value || "-"}</span>
        },
        {
          key: "equipment_list",
          title: "建议设备列表",
          render: (value) => <span className="cell-wrap">{value || "-"}</span>
        },
        {
          key: "shortage",
          title: "缺口",
          render: (value) => formatQty(value)
        }
      ]}
      rows={rows || []}
    />
  );
}

