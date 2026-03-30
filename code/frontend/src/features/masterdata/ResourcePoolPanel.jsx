function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

export default function ResourcePoolPanel({
  selectedConfigDate,
  newResourceShift,
  onResourceShiftChange,
  newResourceProcess,
  onResourceProcessChange,
  processOptions,
  shiftOptions,
  onAddResourceRow,
  resourceRowsForDate,
  isRestMode,
  onUpdateResourceRow,
  onRemoveResourceRow,
}) {
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>班次资源（可编辑）</h3>
        <div className="toolbar">
          <span className="hint">日期</span>
          <span>{selectedConfigDate || "-"}</span>
          <span className="hint">班次</span>
          <select value={newResourceShift} onChange={(e) => onResourceShiftChange(e.target.value)}>
            {shiftOptions.map((option) => (
              <option key={option.code} value={option.code}>
                {option.name}
              </option>
            ))}
          </select>
          <span className="hint">工序</span>
          <select
            value={newResourceProcess}
            onChange={(e) => onResourceProcessChange(e.target.value)}
            disabled={processOptions.length === 0}
          >
            {processOptions.length === 0 ? (
              <option value="">暂无工序</option>
            ) : (
              processOptions.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.name}
                </option>
              ))
            )}
          </select>
          <button onClick={onAddResourceRow} disabled={!selectedConfigDate || processOptions.length === 0}>
            新增资源
          </button>
        </div>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>班次</th>
              <th>工序</th>
              <th>班次开放</th>
              <th>可用人力</th>
              <th>可用设备</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {resourceRowsForDate.length === 0 ? (
              <tr>
                <td colSpan={6}>{isRestMode ? "当前日期为休息模式，暂无可编辑班次。" : "暂无数据"}</td>
              </tr>
            ) : (
              resourceRowsForDate.map((row) => (
                <tr key={`${row.calendar_date}-${row.shift_code}-${row.process_code}`}>
                  <td>{row.shift_name_cn || row.shift_code || "-"}</td>
                  <td>{row.process_name_cn || row.process_code || "-"}</td>
                  <td>{toInt(row.open_flag, 1) === 1 ? "开放" : "关闭"}</td>
                  <td>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={row.workers_available ?? ""}
                      onChange={(e) => onUpdateResourceRow(row, "workers_available", e.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={row.machines_available ?? ""}
                      onChange={(e) => onUpdateResourceRow(row, "machines_available", e.target.value)}
                    />
                  </td>
                  <td>
                    <button onClick={() => onRemoveResourceRow(row)}>删除</button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
