import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createLegacyReporting,
  deleteLegacyReporting,
  listMesReportingsByPath
} from "../../order-execution/executionWipClient";
import {
  buildReportRecordRows,
  buildReportingsPath,
  parseImportText,
  readFileAsText
} from "../executionWipUtils";

export function useExecutionWipController() {
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

  const refresh = useCallback(
    async (nextStartTime = startTime, nextEndTime = endTime) => {
      const list = await listMesReportingsByPath(buildReportingsPath(nextStartTime, nextEndTime));
      setReportings(list.items ?? []);
    },
    [endTime, startTime]
  );

  const report = useCallback(async () => {
    setError("");
    setMessage("");
    setSubmitting(true);
    try {
      await createLegacyReporting({
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
  }, [orderNo, qty, refresh]);

  const deleteReporting = useCallback(
    async (reportId) => {
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
        await deleteLegacyReporting(reportId);
        await refresh();
        setMessage(`报工记录 ${reportId} 已删除。`);
      } catch (e) {
        setError(e.message || "删除报工失败");
      } finally {
        setDeletingReportId("");
      }
    },
    [refresh]
  );

  const onPickImportFile = useCallback(async (event) => {
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
  }, []);

  const importReportings = useCallback(async () => {
    if (importRows.length === 0) {
      setError("请先选择导入文件。");
      setMessage("");
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

        if (
          !payload.order_no ||
          !payload.process_code ||
          !Number.isFinite(payload.report_qty) ||
          payload.report_qty <= 0
        ) {
          results.push({
            ...row,
            status: "FAILED",
            error: "字段不完整或报工量不合法（需 > 0）"
          });
          continue;
        }

        try {
          await createLegacyReporting(payload);
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
  }, [importRows, refresh]);

  const reportRecordRows = useMemo(() => buildReportRecordRows(reportings), [reportings]);

  useEffect(() => {
    refresh().catch(() => {});
  }, [refresh]);

  const clearTimeFilters = useCallback(() => {
    setStartTime("");
    setEndTime("");
    refresh("", "").catch(() => {});
  }, [refresh]);

  const applyTimeFilters = useCallback(() => {
    refresh().catch(() => {});
  }, [refresh]);

  const setQtySafe = useCallback((value) => {
    setQty(Math.max(1, Math.round(Number(value) || 0)));
  }, []);

  return {
    reportings,
    reportRecordRows,
    orderNo,
    setOrderNo,
    qty,
    setQtySafe,
    startTime,
    setStartTime,
    endTime,
    setEndTime,
    submitting,
    deletingReportId,
    message,
    error,
    importFileName,
    importRows,
    importResults,

    refresh,
    report,
    deleteReporting,
    onPickImportFile,
    importReportings,
    clearTimeFilters,
    applyTimeFilters
  };
}

