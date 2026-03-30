import SimpleTable from "../../../components/SimpleTable";

export function SimulationEventsPanel({ events }) {
  return (
    <div className="panel">
      <h3>仿真事件</h3>
      <SimpleTable
        columns={[
          { key: "event_date", title: "日期" },
          {
            key: "event_type",
            title: "事件类型",
            render: (value, row) => row.event_type_name_cn || value || "-"
          },
          {
            key: "message",
            title: "说明",
            render: (value, row) => row.message_cn || value
          },
          { key: "sales_order_no", title: "销售单" },
          { key: "production_order_no", title: "生产单" },
          { key: "version_no", title: "版本" },
          {
            key: "process_code",
            title: "工序",
            render: (value, row) => row.process_name_cn || value || "-"
          }
        ]}
        rows={events || []}
      />
    </div>
  );
}

