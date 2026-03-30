export default function PlanReportsToolbar({
  loading,
  selectedVersionNo,
  versionRows,
  onRefresh,
  onVersionChange,
  onExportCurrent,
}) {
  return (
    <div className="toolbar">
      <button disabled={loading} onClick={onRefresh}>
        {loading ? "刷新中..." : "刷新"}
      </button>
      <select value={selectedVersionNo} onChange={(e) => onVersionChange(e.target.value)} disabled={loading}>
        <option value="">自动选择（已发布优先）</option>
        {(versionRows || []).map((row) => (
          <option key={row.version_no} value={row.version_no}>
            {row.version_no} / {row.status_name_cn || row.status || "-"}
          </option>
        ))}
      </select>
      <button disabled={loading} onClick={onExportCurrent}>
        导出当前页签
      </button>
    </div>
  );
}

