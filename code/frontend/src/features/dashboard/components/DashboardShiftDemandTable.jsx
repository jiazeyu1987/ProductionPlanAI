import SimpleTable from "../../../components/SimpleTable";
import { formatQty } from "../dashboardFormatters";

export function DashboardShiftDemandTable({
  rows,
  selectedShiftCode,
  onSelectShiftCode
}) {
  return (
    <SimpleTable
      columns={[
        {
          key: "shift_name_cn",
          title: "班次",
          render: (_, row) => row.shift_name_cn || row.shift_code || "-"
        },
        {
          key: "scheduled_qty_total",
          title: "已安排总量",
          render: (value) => formatQty(value)
        },
        {
          key: "required_workers_total",
          title: "总需人力",
          render: (value) => formatQty(value)
        },
        {
          key: "required_machines_total",
          title: "总需设备",
          render: (value) => formatQty(value)
        },
        {
          key: "id",
          title: "设备列表",
          render: (_, row) => (
            <button type="button" onClick={() => onSelectShiftCode(row.shift_code)}>
              {selectedShiftCode === row.shift_code ? "当前查看" : "查看设备"}
            </button>
          )
        }
      ]}
      rows={rows || []}
    />
  );
}

