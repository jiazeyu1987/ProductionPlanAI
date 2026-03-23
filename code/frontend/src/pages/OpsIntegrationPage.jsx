import { useEffect, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";

export default function OpsIntegrationPage() {
  const [inbox, setInbox] = useState([]);
  const [outbox, setOutbox] = useState([]);

  async function refresh() {
    const [inboxRes, outboxRes] = await Promise.all([
      loadList("/internal/v1/internal/integration/inbox"),
      loadList("/internal/v1/internal/integration/outbox")
    ]);
    setInbox(inboxRes.items);
    setOutbox(outboxRes.items);
  }

  async function retry(messageId) {
    await postContract(`/internal/v1/internal/integration/outbox/${messageId}/retry`, {
      operator: "admin01"
    });
    await refresh();
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>同步监控</h2>
      <div className="panel">
        <h3>入站消息</h3>
        <SimpleTable
          columns={[
            { key: "message_id", title: "消息ID" },
            { key: "sync_flow_cn", title: "读写映射" },
            {
              key: "topic",
              title: "主题",
              render: (value, row) => row.topic_name_cn || value || "-"
            },
            {
              key: "status",
              title: "状态",
              render: (value, row) => row.status_name_cn || value || "-"
            },
            { key: "retry_count", title: "重试次数" },
            { key: "updated_at", title: "更新时间" }
          ]}
          rows={inbox}
        />
      </div>
      <div className="panel">
        <h3>出站消息</h3>
        <SimpleTable
          columns={[
            { key: "message_id", title: "消息ID" },
            { key: "sync_flow_cn", title: "读写映射" },
            {
              key: "topic",
              title: "主题",
              render: (value, row) => row.topic_name_cn || value || "-"
            },
            {
              key: "status",
              title: "状态",
              render: (value, row) => row.status_name_cn || value || "-"
            },
            { key: "retry_count", title: "重试次数" },
            { key: "updated_at", title: "更新时间" }
          ]}
          rows={outbox}
        />
      </div>
      <div className="chip-list">
        {outbox
          .filter((row) => row.status !== "SUCCESS")
          .map((row) => (
            <div className="chip-row" key={row.message_id}>
              <span>{row.message_id}</span>
              <button onClick={() => retry(row.message_id).catch(() => {})}>重试投递</button>
            </div>
          ))}
      </div>
    </section>
  );
}
