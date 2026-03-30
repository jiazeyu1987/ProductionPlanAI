function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

export default function DeviceTopologyPanel({
  saving,
  topologyWorkshopGroups,
  selectedTopologyWorkshop,
  selectedTopologyLine,
  selectedTopologyLineProcessRows,
  processOptions,
  processNameByCode,
  onAddTopologyWorkshop,
  onSaveTopology,
  onSelectTopologyWorkshop,
  onRemoveTopologyWorkshop,
  onAddTopologyLine,
  onSelectTopologyLine,
  onRemoveTopologyLine,
  onUpdateTopologyLine,
  onSetTopologyLineProcess,
  onUpdateLineTopologyRow,
  onRemoveLineTopologyRow,
}) {
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>工艺参数</h3>
        <div className="toolbar">
          <button onClick={onAddTopologyWorkshop}>新增车间</button>
          <button disabled={saving} onClick={() => onSaveTopology().catch(() => {})}>
            {saving ? "保存中..." : "保存拓扑变更"}
          </button>
        </div>
      </div>
      <div className="topology-friendly-layout">
        <aside className="topology-workshop-list">
          <div className="topology-workshop-list-head">
            <strong>车间列表</strong>
            <span className="hint">{topologyWorkshopGroups.length} 个</span>
          </div>
          {topologyWorkshopGroups.length === 0 ? (
            <p className="hint">暂无车间数据，点击上方“新增车间”即可开始配置。</p>
          ) : (
            topologyWorkshopGroups.map((workshop) => {
              const isActive = workshop.key === selectedTopologyWorkshop?.key;
              return (
                <button
                  key={workshop.key}
                  type="button"
                  className={`topology-workshop-btn ${isActive ? "topology-workshop-btn-active" : ""}`}
                  onClick={() => onSelectTopologyWorkshop(workshop.key)}
                >
                  <span>{workshop.workshopCode}</span>
                  <small className="topology-workshop-meta">
                    {workshop.lines.length} 条产线 / {workshop.lines.reduce((sum, line) => sum + line.processCodes.length, 0)} 道工序
                  </small>
                </button>
              );
            })
          )}
        </aside>

        <section className="topology-friendly-detail">
          {selectedTopologyWorkshop ? (
            <>
              <article className="topology-friendly-card">
                <div className="panel-head">
                  <h3>车间信息</h3>
                  <button className="btn-danger-text" onClick={() => onRemoveTopologyWorkshop(selectedTopologyWorkshop)}>
                    删除车间
                  </button>
                </div>
              </article>

              <article className="topology-friendly-card">
                <div className="panel-head">
                  <h3>产线与工序</h3>
                  <button onClick={() => onAddTopologyLine(selectedTopologyWorkshop)}>新增产线</button>
                </div>
                {selectedTopologyWorkshop.lines.length === 0 ? (
                  <p className="hint">当前车间暂无产线，点击“新增产线”开始配置。</p>
                ) : (
                  <>
                    <div className="topology-line-tabs" role="tablist" aria-label={`${selectedTopologyWorkshop.workshopCode}产线`}>
                      {selectedTopologyWorkshop.lines.map((line) => {
                        const isActive =
                          String(line.lineCode || "").trim().toUpperCase()
                          === String(selectedTopologyLine?.lineCode || "").trim().toUpperCase();
                        return (
                          <button
                            key={line.key}
                            type="button"
                            role="tab"
                            aria-selected={isActive}
                            className={`topology-line-tab ${isActive ? "topology-line-tab-active" : ""}`}
                            onClick={() => onSelectTopologyLine(String(line.lineCode || "").trim().toUpperCase())}
                          >
                            <span className="topology-line-tab-title">{line.lineName || line.lineCode || "-"}</span>
                            <small className="topology-line-tab-meta">{line.lineCode || "-"}</small>
                          </button>
                        );
                      })}
                    </div>

                    {selectedTopologyLine ? (
                      <article className="topology-line-card topology-line-card-active">
                        <div className="topology-line-head">
                          <strong>{selectedTopologyLine.lineName || selectedTopologyLine.lineCode || "-"}</strong>
                          <button className="btn-danger-text" onClick={() => onRemoveTopologyLine(selectedTopologyLine)}>
                            删除产线
                          </button>
                        </div>
                        <div className="topology-friendly-form topology-line-form">
                          <label>
                            产线编码
                            <input
                              value={selectedTopologyLine.lineCode || ""}
                              onChange={(e) => onUpdateTopologyLine(selectedTopologyLine, "line_code", e.target.value)}
                            />
                          </label>
                          <label>
                            产线名称
                            <input
                              value={selectedTopologyLine.lineName || ""}
                              onChange={(e) => onUpdateTopologyLine(selectedTopologyLine, "line_name", e.target.value)}
                            />
                          </label>
                        </div>
                        <div className="topology-process-grid">
                          {processOptions.length === 0 ? (
                            <p className="hint">暂无可选工序，请先在工艺路线中维护工序。</p>
                          ) : (
                            processOptions.map((option) => {
                              const checked = selectedTopologyLine.processCodes.includes(option.code);
                              return (
                                <label
                                  key={`${selectedTopologyLine.key}-${option.code}`}
                                  className={`topology-process-pill ${checked ? "topology-process-pill-active" : ""}`}
                                >
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    onChange={(e) => onSetTopologyLineProcess(selectedTopologyLine, option.code, e.target.checked)}
                                  />
                                  <span>{option.name}</span>
                                  <code>{option.code}</code>
                                </label>
                              );
                            })
                          )}
                        </div>
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>工序</th>
                                <th>工序编码</th>
                                <th>每班产能</th>
                                <th>需设备数</th>
                                <th>需人数</th>
                                <th>启用</th>
                                <th>操作</th>
                              </tr>
                            </thead>
                            <tbody>
                              {selectedTopologyLineProcessRows.length === 0 ? (
                                <tr>
                                  <td colSpan={7}>暂无已绑定工序，请先勾选上方工序。</td>
                                </tr>
                              ) : (
                                selectedTopologyLineProcessRows.map((row) => (
                                  <tr key={`${row.line_code}-${row.process_code}`}>
                                    <td>{processNameByCode.get(row.process_code) || row.process_name_cn || row.process_code}</td>
                                    <td>{row.process_code}</td>
                                    <td>
                                      <input
                                        type="number"
                                        min="0"
                                        step="0.01"
                                        value={row.capacity_per_shift ?? ""}
                                        onChange={(e) => onUpdateLineTopologyRow(row, "capacity_per_shift", e.target.value)}
                                      />
                                    </td>
                                    <td>
                                      <input
                                        type="number"
                                        min="0"
                                        step="1"
                                        value={row.required_machines ?? ""}
                                        onChange={(e) => onUpdateLineTopologyRow(row, "required_machines", e.target.value)}
                                      />
                                    </td>
                                    <td>
                                      <input
                                        type="number"
                                        min="0"
                                        step="1"
                                        value={row.required_workers ?? ""}
                                        onChange={(e) => onUpdateLineTopologyRow(row, "required_workers", e.target.value)}
                                      />
                                    </td>
                                    <td>
                                      <select value={toInt(row.enabled_flag, 1)} onChange={(e) => onUpdateLineTopologyRow(row, "enabled_flag", e.target.value)}>
                                        <option value={1}>启用</option>
                                        <option value={0}>停用</option>
                                      </select>
                                    </td>
                                    <td>
                                      <button onClick={() => onRemoveLineTopologyRow(row)}>删除</button>
                                    </td>
                                  </tr>
                                ))
                              )}
                            </tbody>
                          </table>
                        </div>
                      </article>
                    ) : (
                      <p className="hint">请选择一个产线。</p>
                    )}
                  </>
                )}
              </article>
            </>
          ) : (
            <article className="topology-friendly-card">
              <p className="hint">暂无可编辑车间，点击上方“新增车间”开始配置。</p>
            </article>
          )}
        </section>
      </div>
    </div>
  );
}
