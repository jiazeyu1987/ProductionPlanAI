import { useEffect, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import { apiRequest, loadList, postLegacy } from "../services/api";

function toNumberText(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return String(Math.round(n * 100) / 100).replace(/\.0+$/, "").replace(/(\.\d*?)0+$/, "$1");
}

function toDisplayTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (!Number.isNaN(date.getTime())) {
    return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`;
  }
  const text = String(value);
  return text.replace("T", " ").replace("Z", "");
}

function normalizeHeader(raw) {
  const text = String(raw || "").trim().toLowerCase();
  if (!text) {
    return "";
  }
  if (text === "order_no" || text === "orderno" || text === "订单号" || text === "生产订单号") {
    return "order_no";
  }
  if (text === "product_code" || text === "productcode" || text === "产品编码") {
    return "product_code";
  }
  if (text === "process_code" || text === "processcode" || text === "工序编码" || text === "工序") {
    return "process_code";
  }
  if (text === "report_qty" || text === "reportqty" || text === "qty" || text === "报工量" || text === "数量") {
    return "report_qty";
  }
  return text;
}

function parseImportText(text) {
  const lines = String(text || "")
    .replace(/\uFEFF/g, "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length === 0) {
    return [];
  }

  const delimiter = lines[0].includes("\t") ? "\t" : ",";
  const firstCells = lines[0].split(delimiter).map((cell) => cell.trim());
  const firstHeaders = firstCells.map(normalizeHeader);
  const hasHeader = ["order_no", "product_code", "process_code", "report_qty"].every((name) => firstHeaders.includes(name));

  const headers = hasHeader ? firstHeaders : ["order_no", "product_code", "process_code", "report_qty"];
  const startIndex = hasHeader ? 1 : 0;
  const rows = [];

  for (let i = startIndex; i < lines.length; i += 1) {
    const cells = lines[i].split(delimiter).map((cell) => cell.trim());
    const row = {
      line_no: i + 1,
      order_no: "",
      product_code: "",
      process_code: "",
      report_qty: ""
    };

    for (let c = 0; c < headers.length; c += 1) {
      const key = headers[c];
      if (!key || !(key in row)) {
        continue;
      }
      row[key] = cells[c] ?? "";
    }

    if (row.order_no || row.product_code || row.process_code || row.report_qty) {
      rows.push(row);
    }
  }

  return rows;
}

async function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(new Error("读取文件失败"));
    reader.readAsText(file);
  });
}

export default function ExecutionWipPage() {
  const [reportings, setReportings] = useState([]);
  const [orderNo, setOrderNo] = useState("MO-CATH-001");
  const [qty, setQty] = useState(100);
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [deletingReportId, setDeletingReportId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [importFileName, setImportFileName] = useState("");
  const [importRows, setImportRows] = useState([]);
  const [importResults, setImportResults] = useState([]);

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
    setError("");
    setMessage("");
    setSubmitting(true);
    try {
      await postLegacy("/api/reportings", {
        order_no: orderNo,
        product_code: "PROD_CATH",
        process_code: "PROC_TUBE",
        report_qty: Number(qty)
      });
      await refresh();
      setMessage("报工已提交。");
    } catch (e) {
      setError(e.message || "提交报工失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function deleteReporting(reportId) {
    if (!reportId) {
      return;
    }
    const confirmed = window.confirm(`确认删除报工记录 ${reportId} 吗？`);
    if (!confirmed) {
      return;
    }
    setError("");
    setMessage("");
    setDeletingReportId(reportId);
    try {
      await apiRequest(`/api/reportings/${encodeURIComponent(reportId)}`, {
        method: "DELETE",
        contract: false,
        body: {}
      });
      await refresh();
      setMessage(`报工记录 ${reportId} 已删除。`);
    } catch (e) {
      setError(e.message || "删除报工失败");
    } finally {
      setDeletingReportId("");
    }
  }

  async function onPickImportFile(event) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setError("");
    setMessage("");
    setImportResults([]);
    try {
      const text = await readFileAsText(file);
      const rows = parseImportText(text);
      if (rows.length === 0) {
        throw new Error("文件中未解析到有效数据，请检查格式。");
      }
      setImportFileName(file.name);
      setImportRows(rows);
      setMessage(`已读取 ${rows.length} 行导入数据。`);
    } catch (e) {
      setImportFileName(file.name || "");
      setImportRows([]);
      setError(e.message || "导入文件解析失败");
    } finally {
      event.target.value = "";
    }
  }

  async function importReportings() {
    if (importRows.length === 0) {
      setError("请先选择导入文件。");
      return;
    }
    setError("");
    setMessage("");
    setSubmitting(true);

    const results = [];
    let successCount = 0;

    try {
      for (const row of importRows) {
        const payload = {
          order_no: String(row.order_no || "").trim(),
          product_code: String(row.product_code || "").trim(),
          process_code: String(row.process_code || "").trim(),
          report_qty: Number(row.report_qty || 0)
        };

        if (!payload.order_no || !payload.process_code || !Number.isFinite(payload.report_qty) || payload.report_qty <= 0) {
          results.push({
            ...row,
            status: "FAILED",
            error: "字段不完整或报工量不合法（需 > 0）"
          });
          continue;
        }

        try {
          await postLegacy("/api/reportings", payload);
          results.push({
            ...row,
            status: "SUCCESS",
            error: ""
          });
          successCount += 1;
        } catch (e) {
          results.push({
            ...row,
            status: "FAILED",
            error: e.message || "导入失败"
          });
        }
      }

      setImportResults(results);
      await refresh();
      setMessage(`导入完成：成功 ${successCount} 行，失败 ${results.length - successCount} 行。`);
    } finally {
      setSubmitting(false);
    }
  }

  const reportRecordRows = reportings
    .map((row) => {
      const reportId = row.report_id || row.reporting_id || row.reportingId || "";
      const reportTimeRaw = row.report_time || "";
      const updatedTimeRaw = row.last_update_time || reportTimeRaw;
      const sortTime = new Date(reportTimeRaw).getTime();
      return {
        ...row,
        report_id: reportId,
        fee_code: row.fee_code || row.process_code || "-",
        fee_name: row.fee_name || row.process_name_cn || row.process_code || "-",
        filler_code: row.filler_code || row.operator_code || "OP-A",
        filler_name: row.filler_name || row.operator_name_cn || "系统填报",
        section_leader: row.section_leader || row.section_leader_name || row.team_code || "默认工段长",
        qty_text: toNumberText(row.report_qty),
        created_time_text: toDisplayTime(updatedTimeRaw),
        filled_time_text: toDisplayTime(reportTimeRaw),
        sort_time: Number.isNaN(sortTime) ? 0 : sortTime
      };
    })
    .sort((a, b) => b.sort_time - a.sort_time);

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  return (
    <section>
      <h2>报工记录</h2>
      {error ? <p className="error">{error}</p> : null}
      {message ? <p className="notice">{message}</p> : null}

      <div className="toolbar">
        <input value={orderNo} onChange={(e) => setOrderNo(e.target.value)} />
        <input
          type="number"
          step="1"
          min="1"
          value={qty}
          onChange={(e) => setQty(Math.max(1, Math.round(Number(e.target.value) || 0)))}
        />
        <button disabled={submitting} onClick={() => report().catch(() => {})}>
          {submitting ? "提交中..." : "提交报工"}
        </button>
      </div>

      <div className="panel">
        <h3>导入报工</h3>
        <p className="hint">支持 CSV/TSV（可从 Excel 另存为 CSV）。字段：order_no, product_code, process_code, report_qty。</p>
        <div className="toolbar">
          <input
            type="file"
            accept=".csv,.txt,.tsv"
            onChange={(e) => onPickImportFile(e).catch(() => {})}
            disabled={submitting}
          />
          <button disabled={submitting || importRows.length === 0} onClick={() => importReportings().catch(() => {})}>
            {submitting ? "导入中..." : "开始导入"}
          </button>
        </div>
        {importFileName ? <p className="hint">已选择文件：{importFileName}（{importRows.length} 行）</p> : null}

        {importRows.length > 0 ? (
          <SimpleTable
            columns={[
              { key: "line_no", title: "行号" },
              { key: "order_no", title: "订单号" },
              { key: "product_code", title: "产品编码" },
              { key: "process_code", title: "工序编码" },
              { key: "report_qty", title: "报工量" }
            ]}
            rows={importRows}
          />
        ) : null}

        {importResults.length > 0 ? (
          <SimpleTable
            columns={[
              { key: "line_no", title: "行号" },
              { key: "order_no", title: "订单号" },
              { key: "process_code", title: "工序编码" },
              {
                key: "status",
                title: "结果",
                render: (value) => (value === "SUCCESS" ? "成功" : "失败")
              },
              { key: "error", title: "原因" }
            ]}
            rows={importResults}
          />
        ) : null}
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
                onClick={() => deleteReporting(row.report_id).catch(() => {})}
              >
                {deletingReportId === row.report_id ? "删除中..." : "删除"}
              </button>
            )
          }
        ]}
        rows={reportRecordRows}
      />
    </section>
  );
}
