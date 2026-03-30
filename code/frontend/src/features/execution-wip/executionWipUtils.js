function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : NaN;
}

export function toNumberText(value) {
  const n = toNumber(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return String(Math.round(n * 100) / 100)
    .replace(/\.0+$/, "")
    .replace(/(\.\d*?)0+$/, "$1");
}

export function toDisplayTime(value) {
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

export function parseImportText(text) {
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
  const hasHeader = ["order_no", "product_code", "process_code", "report_qty"].every((name) =>
    firstHeaders.includes(name)
  );

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

export async function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(new Error("读取文件失败"));
    reader.readAsText(file);
  });
}

function toIsoTime(value) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function buildReportingsPath(nextStartTime, nextEndTime) {
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

export function buildReportRecordRows(reportings) {
  return (reportings || [])
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
}

