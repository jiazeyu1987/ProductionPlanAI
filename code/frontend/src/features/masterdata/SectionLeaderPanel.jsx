function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

function normalizeCompanyCode(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized || "COMPANY-MAIN";
}

export default function SectionLeaderPanel({
  sectionLeaderRows,
  lineOptions,
  onAdd,
  onUpdate,
  onRemove,
}) {
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>工段长-多产线绑定（可编辑）</h3>
        <button onClick={onAdd}>新增绑定</button>
      </div>
      <p className="hint">同一工段长可绑定多条产线，用于反映真实组织分工。</p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>工段长ID</th>
              <th>工段长姓名</th>
              <th>公司</th>
              <th>车间</th>
              <th>产线</th>
              <th>启用</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {sectionLeaderRows.length === 0 ? (
              <tr>
                <td colSpan={7}>暂无数据</td>
              </tr>
            ) : (
              sectionLeaderRows.map((row) => (
                <tr key={`${row.leader_id}-${row.line_code}`}>
                  <td>
                    <input value={row.leader_id || ""} onChange={(e) => onUpdate(row, "leader_id", e.target.value)} />
                  </td>
                  <td>
                    <input value={row.leader_name || ""} onChange={(e) => onUpdate(row, "leader_name", e.target.value)} />
                  </td>
                  <td>{normalizeCompanyCode(row.company_code) || "-"}</td>
                  <td>{row.workshop_code || "-"}</td>
                  <td>
                    <select
                      value={String(row.line_code || "").trim().toUpperCase()}
                      onChange={(e) => onUpdate(row, "line_code", e.target.value)}
                    >
                      {lineOptions.map((line) => (
                        <option key={line.lineCode} value={line.lineCode}>
                          {line.lineCode}（{line.lineName}）
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <select value={toInt(row.active_flag, 1)} onChange={(e) => onUpdate(row, "active_flag", e.target.value)}>
                      <option value={1}>启用</option>
                      <option value={0}>停用</option>
                    </select>
                  </td>
                  <td>
                    <button onClick={() => onRemove(row)}>删除</button>
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
