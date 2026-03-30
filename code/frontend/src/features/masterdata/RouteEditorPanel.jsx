export default function RouteEditorPanel({
  routeEditorOpen,
  routeEditorMode,
  routeSaving,
  routeSourceProductCode,
  routeTargetProductCode,
  setRouteTargetProductCode,
  submitRouteEditor,
  closeRouteEditor,
  routeStepsDraft,
  updateRouteStep,
  processOptions,
  normalizeDependencyType,
  removeRouteStep,
  addRouteStep,
  routeEditorCreateMode,
  routeEditorEditMode,
}) {
  if (!routeEditorOpen) {
    return null;
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>
          {routeEditorMode === routeEditorCreateMode
            ? "新增工艺路线"
            : routeEditorMode === routeEditorEditMode
              ? "修改工艺路线"
              : "复制工艺路线"}
        </h3>
        <div className="toolbar">
          <button disabled={routeSaving} onClick={() => submitRouteEditor().catch(() => {})}>
            {routeSaving ? "保存中..." : "保存路线"}
          </button>
          <button disabled={routeSaving} onClick={closeRouteEditor}>
            取消
          </button>
        </div>
      </div>
      <div className="table-wrap">
        <table>
          <tbody>
            {routeEditorMode !== routeEditorCreateMode ? (
              <tr>
                <th>来源产品</th>
                <td>
                  <input value={routeSourceProductCode} disabled />
                </td>
              </tr>
            ) : null}
            <tr>
              <th>目标产品</th>
              <td>
                <input
                  value={routeTargetProductCode}
                  disabled={routeEditorMode === routeEditorEditMode}
                  placeholder="例如 PROD_CATH_NEW"
                  onChange={(e) => setRouteTargetProductCode(String(e.target.value || "").trim().toUpperCase())}
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>顺序</th>
              <th>工序</th>
              <th>依赖类型</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {routeStepsDraft.map((row, index) => (
              <tr key={`route-step-${index}`}>
                <td>{index + 1}</td>
                <td>
                  <select
                    value={row.process_code || ""}
                    onChange={(e) => updateRouteStep(index, "process_code", e.target.value)}
                  >
                    <option value="">请选择工序</option>
                    {processOptions.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <select
                    value={normalizeDependencyType(row.dependency_type)}
                    onChange={(e) => updateRouteStep(index, "dependency_type", normalizeDependencyType(e.target.value))}
                  >
                    <option value="FS">顺序（前序完成后开始）</option>
                    <option value="SS">并行（前序开始后即可开始）</option>
                  </select>
                </td>
                <td>
                  <button disabled={routeSaving} onClick={() => removeRouteStep(index)}>
                    删除步骤
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="toolbar">
        <button disabled={routeSaving} onClick={addRouteStep}>
          新增步骤
        </button>
      </div>
    </div>
  );
}

