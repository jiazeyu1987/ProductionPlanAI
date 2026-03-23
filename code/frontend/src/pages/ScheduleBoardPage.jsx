import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postLegacy } from "../services/api";

export default function ScheduleBoardPage() {
  const [versions, setVersions] = useState([]);
  const [selected, setSelected] = useState("");
  const [tasks, setTasks] = useState([]);
  const [message, setMessage] = useState("");

  async function loadVersions() {
    const data = await loadList("/internal/v1/internal/schedule-versions");
    setVersions(data.items);
    if (!selected && data.items.length > 0) {
      setSelected(data.items[data.items.length - 1].version_no);
    }
  }

  async function loadTasks(versionNo) {
    if (!versionNo) {
      setTasks([]);
      return;
    }
    const data = await loadList(`/internal/v1/internal/schedule-versions/${versionNo}/tasks`);
    setTasks(data.items);
  }

  async function generate() {
    setMessage("");
    const schedule = await postLegacy("/api/schedules/generate", {
      base_version_no: selected || null,
      autoReplan: false
    });
    setMessage(`生成完成：${schedule.version_no || schedule.versionNo}`);
    await loadVersions();
  }

  useEffect(() => {
    loadVersions().catch(() => {});
  }, []);

  useEffect(() => {
    loadTasks(selected).catch(() => {});
  }, [selected]);

  return (
    <section>
      <h2>调度台（甘特/列表）</h2>
      <div className="toolbar">
        <button onClick={() => generate().catch((e) => setMessage(e.message))}>生成草稿版本</button>
        <select value={selected} onChange={(e) => setSelected(e.target.value)}>
          <option value="">请选择版本</option>
          {versions.map((item) => (
            <option key={item.version_no} value={item.version_no}>
              {item.version_no} / {item.status_name_cn || item.status || "-"}
            </option>
          ))}
        </select>
      </div>
      {message ? <p className="notice">{message}</p> : null}
      <p className="hint">批量调整前影响预览：受影响任务 {tasks.length} 条。</p>
      <SimpleTable
        columns={[
          {
            key: "order_no",
            title: "订单",
            render: (value) =>
              value ? (
                <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(value)}`}>
                  {value}
                </Link>
              ) : (
                "-"
              )
          },
          {
            key: "process_code",
            title: "工序",
            render: (value, row) => {
              const processCode = value || "";
              const processName = row.process_name_cn || processCode || "-";
              if (!processCode) {
                return processName;
              }
              return (
                <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(processCode)}`}>
                  {processName}
                </Link>
              );
            }
          },
          { key: "calendar_date", title: "日期" },
          {
            key: "shift_code",
            title: "班次",
            render: (value, row) => row.shift_name_cn || value || "-"
          },
          { key: "plan_qty", title: "计划量" }
        ]}
        rows={tasks}
      />
    </section>
  );
}
