import SimpleTable from "../../../components/SimpleTable";

export function ExecutionWipRecordsTable({ rows, deletingReportId, onDelete }) {
  return (
    <SimpleTable
      className="reporting-record-table"
      rowKey="report_id"
      columns={[
        { key: "fee_code", title: "费用编码" },
        { key: "fee_name", title: "费用名称" },
        { key: "filler_code", title: "填写人编码" },
        { key: "filler_name", title: "填写名称" },
        { key: "section_leader", title: "工段长" },
        {
          key: "qty_text",
          title: "个数",
          render: (value) => <span className="report-record-qty">{value}</span>
        },
        { key: "created_time_text", title: "创建时间" },
        { key: "filled_time_text", title: "费用填写时间" },
        {
          key: "operation",
          title: "操作",
          render: (_, row) => (
            <button
              type="button"
              className="table-op-delete"
              disabled={!row.report_id || Boolean(deletingReportId)}
              onClick={() => onDelete(row.report_id)}
            >
              {deletingReportId === row.report_id ? "删除中..." : "删除"}
            </button>
          )
        }
      ]}
      rows={rows || []}
    />
  );
}

