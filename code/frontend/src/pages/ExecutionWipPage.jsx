import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postLegacy } from "../services/api";

function toIntText(value) {
  const n = Number(value);
  return Number.isFinite(n) ? String(Math.round(n)) : "-";
}

export default function ExecutionWipPage() {
  const [reportings, setReportings] = useState([]);
  const [orderNo, setOrderNo] = useState("MO-CATH-001");
  const [qty, setQty] = useState(100);
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");

  function toIsoTime(value) {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }

  function buildReportingsPath(nextStartTime = startTime, nextEndTime = endTime) {
    const params = new URLSearchParams();
    const startIso = toIsoTime(nextStartTime);
    const endIso = toIsoTime(nextEndTime);
    if (startIso) {
      params.set("start_time", startIso);
    }
    if (endIso) {
      params.set("end_time", endIso);
    }
    const query = params.toString();
    return query ? `/v1/mes/reportings?${query}` : "/v1/mes/reportings";
  }

  async function refresh(nextStartTime = startTime, nextEndTime = endTime) {
    const list = await loadList(buildReportingsPath(nextStartTime, nextEndTime));
    setReportings(list.items ?? []);
  }

  async function report() {
    await postLegacy("/api/reportings", {
      order_no: orderNo,
      product_code: "PROD_CATH",
      process_code: "PROC_TUBE",
      report_qty: Number(qty)
    });
    await refresh();
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>报工列表</h2>
      <div className="toolbar">
        <input value={orderNo} onChange={(e) => setOrderNo(e.target.value)} />
        <input
          type="number"
          step="1"
          min="1"
          value={qty}
          onChange={(e) => setQty(Math.max(1, Math.round(Number(e.target.value) || 0)))}
        />
        <button onClick={() => report().catch(() => {})}>提交报工</button>
      </div>
      <div className="toolbar">
        <input
          type="datetime-local"
          value={startTime}
          onChange={(e) => setStartTime(e.target.value)}
          aria-label="start-time-filter"
        />
        <input
          type="datetime-local"
          value={endTime}
          onChange={(e) => setEndTime(e.target.value)}
          aria-label="end-time-filter"
        />
        <button onClick={() => refresh().catch(() => {})}>按时间过滤</button>
        <button
          onClick={() => {
            setStartTime("");
            setEndTime("");
            refresh("", "").catch(() => {});
          }}
        >
          清空时间
        </button>
      </div>
      <SimpleTable
        columns={[
          { key: "report_id", title: "报工ID" },
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
          { key: "report_qty", title: "报工量", render: (value) => toIntText(value) },
          { key: "report_time", title: "报工时间" }
        ]}
        rows={reportings}
      />
    </section>
  );
}

