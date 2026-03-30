import SimpleTable from "../../../components/SimpleTable";

export function ExecutionWipImportPanel({
  submitting,
  importFileName,
  importRows,
  importResults,
  onPickImportFile,
  onImport
}) {
  return (
    <div className="panel">
      <h3>导入报工</h3>
      <p className="hint">
        支持 CSV/TSV（可从 Excel 另存为 CSV）。字段：order_no, product_code, process_code, report_qty。
      </p>
      <div className="toolbar">
        <input
          type="file"
          accept=".csv,.txt,.tsv"
          onChange={onPickImportFile}
          disabled={submitting}
        />
        <button disabled={submitting || importRows.length === 0} onClick={onImport}>
          {submitting ? "导入中..." : "开始导入"}
        </button>
      </div>
      {importFileName ? (
        <p className="hint">
          已选择文件：{importFileName}（{importRows.length} 行）
        </p>
      ) : null}

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
  );
}

