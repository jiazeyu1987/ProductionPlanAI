import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";

const STATUS_CN = {
  OPEN: "待处理",
  ACKED: "已确认",
  CLOSED: "已关闭"
};

const ALERT_TYPE_CN = {
  PROGRESS_GAP: "进度偏差",
  EQUIPMENT_DOWN: "设备故障"
};

const SEVERITY_CN = {
  CRITICAL: "严重",
  WARN: "警告",
  INFO: "提示"
};

const statusOptions = [
  { value: "", label: "全部状态" },
  { value: "OPEN", label: "待处理" },
  { value: "ACKED", label: "已确认" },
  { value: "CLOSED", label: "已关闭" }
];

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function formatQty(value) {
  const n = toNumber(value);
  if (n === null) {
    return "-";
  }
  return Number.isInteger(n) ? `${n}` : n.toFixed(2);
}

function formatPercent(value) {
  const n = toNumber(value);
  return n === null ? "-" : `${n}%`;
}

function isProgressGap(row) {
  return row.alert_type === "PROGRESS_GAP";
}

export default function AlertsPage() {
  const [rows, setRows] = useState([]);
  const [status, setStatus] = useState("");

  async function refresh(nextStatus = status) {
    const qs = nextStatus ? `?status=${encodeURIComponent(nextStatus)}` : "";
    const data = await loadList(`/internal/v1/internal/alerts${qs}`);
    const items = data.items ?? [];
    const progressRows = items.filter((row) => isProgressGap(row));
    if (progressRows.length === 0) {
      setRows(items);
      return;
    }

    const versions = [...new Set(progressRows.map((row) => row.version_no).filter(Boolean))];
    const taskResponses = await Promise.all(
      versions.map((versionNo) =>
        loadList(`/internal/v1/internal/schedule-versions/${encodeURIComponent(versionNo)}/tasks`).catch(() => ({ items: [] }))
      )
    );
    const reportingRes = await loadList("/v1/mes/reportings").catch(() => ({ items: [] }));

    const plannedMap = new Map();
    versions.forEach((versionNo, index) => {
      const tasks = taskResponses[index]?.items ?? [];
      tasks.forEach((task) => {
        const key = `${versionNo}|${task.order_no}|${task.process_code}`;
        const qty = toNumber(task.plan_qty ?? task.scheduled_qty ?? 0) ?? 0;
        plannedMap.set(key, (plannedMap.get(key) ?? 0) + qty);
      });
    });

    const reportedMap = new Map();
    (reportingRes.items ?? []).forEach((reporting) => {
      const key = `${reporting.order_no}|${reporting.process_code}`;
      reportedMap.set(key, (reportedMap.get(key) ?? 0) + (toNumber(reporting.report_qty) ?? 0));
    });

    const enriched = items.map((row) => {
      if (!isProgressGap(row)) {
        return row;
      }
      const targetExisting = toNumber(row.target_value);
      const actualExisting = toNumber(row.actual_value);
      const deviationExisting = toNumber(row.deviation_percent);
      const thresholdExisting = toNumber(row.alert_threshold_percent ?? row.threshold_value);
      if (targetExisting !== null && actualExisting !== null && deviationExisting !== null && thresholdExisting !== null) {
        return row;
      }

      const target = plannedMap.get(`${row.version_no}|${row.order_no}|${row.process_code}`) ?? targetExisting ?? 0;
      const actual = reportedMap.get(`${row.order_no}|${row.process_code}`) ?? actualExisting ?? 0;
      const deviation = target > 0 ? Math.round((Math.abs(target - actual) / target) * 10000) / 100 : null;
      const threshold = thresholdExisting ?? 10;
      return {
        ...row,
        target_value: targetExisting ?? target,
        actual_value: actualExisting ?? actual,
        deviation_percent: deviationExisting ?? deviation,
        alert_threshold_percent: thresholdExisting ?? threshold
      };
    });

    setRows(enriched);
  }

  async function action(alertId, kind) {
    await postContract(`/internal/v1/internal/alerts/${alertId}/${kind}`, {
      operator: "operator01",
      reason: "控制台处理"
    });
    await refresh();
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>预警中心</h2>
      <div className="toolbar">
        <select
          value={status}
          onChange={(e) => {
            const value = e.target.value;
            setStatus(value);
            refresh(value).catch(() => {});
          }}
        >
          {statusOptions.map((item) => (
            <option key={item.value || "ALL"} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </div>
      <SimpleTable
        columns={[
          { key: "alert_id", title: "预警ID" },
          {
            key: "alert_type",
            title: "类型",
            render: (value, row) => row.alert_type_name_cn || ALERT_TYPE_CN[value] || value || "-"
          },
          {
            key: "severity",
            title: "严重度",
            render: (value, row) => row.severity_name_cn || SEVERITY_CN[value] || value || "-"
          },
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
            key: "target_value",
            title: "目标值",
            render: (value, row) => (isProgressGap(row) ? formatQty(value) : "-")
          },
          {
            key: "actual_value",
            title: "实际值",
            render: (value, row) => (isProgressGap(row) ? formatQty(value) : "-")
          },
          {
            key: "deviation_percent",
            title: "偏差",
            render: (value, row) => (isProgressGap(row) ? formatPercent(value ?? row.trigger_value) : "-")
          },
          {
            key: "alert_threshold_percent",
            title: "预警阈值",
            render: (value, row) => (isProgressGap(row) ? formatPercent(value ?? row.threshold_value) : "-")
          },
          { key: "created_at", title: "预警时间" },
          {
            key: "status",
            title: "状态",
            render: (value, row) => row.status_name_cn || STATUS_CN[value] || value || "-"
          },
          {
            key: "actions",
            title: "操作",
            render: (_, row) => (
              <div className="row-actions">
                <button
                  disabled={row.status === "CLOSED"}
                  onClick={() => action(row.alert_id, "ack").catch(() => {})}
                >
                  确认
                </button>
                <button
                  disabled={row.status === "CLOSED"}
                  onClick={() => action(row.alert_id, "close").catch(() => {})}
                >
                  关闭
                </button>
              </div>
            )
          }
        ]}
        rows={rows}
      />
    </section>
  );
}

