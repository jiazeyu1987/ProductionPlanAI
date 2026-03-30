import { ExecutionWipImportPanel } from "../features/execution-wip/components/ExecutionWipImportPanel";
import { ExecutionWipQuickReportToolbar } from "../features/execution-wip/components/ExecutionWipQuickReportToolbar";
import { ExecutionWipRecordsTable } from "../features/execution-wip/components/ExecutionWipRecordsTable";
import { ExecutionWipTimeFilterBar } from "../features/execution-wip/components/ExecutionWipTimeFilterBar";
import { useExecutionWipController } from "../features/execution-wip/page/useExecutionWipController";

export default function ExecutionWipPage() {
  const {
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
    report,
    deleteReporting,
    onPickImportFile,
    importReportings,
    applyTimeFilters,
    clearTimeFilters
  } = useExecutionWipController();

  return (
    <section>
      <h2>报工记录</h2>
      {error ? <p className="error">{error}</p> : null}
      {message ? <p className="notice">{message}</p> : null}

      <ExecutionWipQuickReportToolbar
        orderNo={orderNo}
        onChangeOrderNo={setOrderNo}
        qty={qty}
        onChangeQty={setQtySafe}
        submitting={submitting}
        onSubmit={() => report().catch(() => {})}
      />

      <ExecutionWipImportPanel
        submitting={submitting}
        importFileName={importFileName}
        importRows={importRows}
        importResults={importResults}
        onPickImportFile={(e) => onPickImportFile(e).catch(() => {})}
        onImport={() => importReportings().catch(() => {})}
      />

      <ExecutionWipTimeFilterBar
        startTime={startTime}
        endTime={endTime}
        onChangeStartTime={setStartTime}
        onChangeEndTime={setEndTime}
        onApply={applyTimeFilters}
        onClear={clearTimeFilters}
      />

      <ExecutionWipRecordsTable
        rows={reportRecordRows}
        deletingReportId={deletingReportId}
        onDelete={(id) => deleteReporting(id).catch(() => {})}
      />
    </section>
  );
}

