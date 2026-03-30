import { useEffect, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { listAuditLogs } from "../features/dispatch-alert/apiClient";

export default function AuditLogsPage() {
  const [rows, setRows] = useState([]);
  const [requestId, setRequestId] = useState("");

  async function refresh(id = requestId) {
    const data = await listAuditLogs(id);
    setRows(data.items);
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>审计追溯</h2>
      <div className="toolbar">
        <input
          value={requestId}
          onChange={(e) => setRequestId(e.target.value)}
          placeholder="按请求ID查询"
        />
        <button onClick={() => refresh().catch(() => {})}>查询</button>
      </div>
      <SimpleTable
        columns={[
          { key: "entity_type", title: "实体类型" },
          { key: "entity_id", title: "实体ID" },
          {
            key: "action",
            title: "动作",
            render: (value, row) => row.action_name_cn || value || "-"
          },
          { key: "operator", title: "操作人" },
          { key: "request_id", title: "请求ID" },
          { key: "operate_time", title: "时间" }
        ]}
        rows={rows}
      />
    </section>
  );
}
