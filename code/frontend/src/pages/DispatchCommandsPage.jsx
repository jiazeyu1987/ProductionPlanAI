import { useEffect, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";

const commandOptions = [
  { value: "INSERT", label: "插单" },
  { value: "LOCK", label: "锁单" },
  { value: "UNLOCK", label: "解锁" },
  { value: "PRIORITY", label: "提优先级" },
  { value: "UNPRIORITY", label: "解除优先级" },
  { value: "FREEZE", label: "冻结" },
  { value: "UNFREEZE", label: "解冻" }
];

export default function DispatchCommandsPage() {
  const [rows, setRows] = useState([]);
  const [orderNo, setOrderNo] = useState("");
  const [commandType, setCommandType] = useState("INSERT");

  async function refresh() {
    const data = await loadList("/internal/v1/internal/dispatch-commands");
    setRows(data.items ?? []);
  }

  async function create() {
    await postContract("/internal/v1/internal/dispatch-commands", {
      command_type: commandType,
      target_order_no: orderNo,
      target_order_type: "production",
      effective_time: new Date().toISOString(),
      reason: "控制台手工指令",
      created_by: "dispatcher01"
    });
    setOrderNo("");
    await refresh();
  }

  async function approve(commandId, decision) {
    await postContract(`/internal/v1/internal/dispatch-commands/${commandId}/approvals`, {
      approver: "manager01",
      decision,
      decision_reason: "控制台审批",
      decision_time: new Date().toISOString()
    });
    await refresh();
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>指令与审批</h2>
      <div className="toolbar">
        <input value={orderNo} placeholder="目标订单号" onChange={(e) => setOrderNo(e.target.value)} />
        <select value={commandType} onChange={(e) => setCommandType(e.target.value)}>
          {commandOptions.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
        <button onClick={() => create().catch(() => {})}>创建指令</button>
      </div>
      <SimpleTable
        columns={[
          { key: "command_id", title: "指令ID" },
          { key: "command_type", title: "类型" },
          { key: "target_order_no", title: "订单" },
          { key: "approved_flag", title: "审批通过" },
          { key: "created_by", title: "创建人" }
        ]}
        rows={rows}
      />
      <div className="chip-list">
        {rows.map((row) => (
          <div className="chip-row" key={row.command_id}>
            <span>{row.command_id}</span>
            <button onClick={() => approve(row.command_id, "APPROVED").catch(() => {})}>批准</button>
            <button onClick={() => approve(row.command_id, "REJECTED").catch(() => {})}>驳回</button>
          </div>
        ))}
      </div>
    </section>
  );
}
