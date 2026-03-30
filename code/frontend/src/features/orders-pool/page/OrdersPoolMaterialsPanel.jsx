import SimpleTable from "../../../components/SimpleTable";
import { materialSupplyTypeName, normalizeMaterialCode } from "..";

export default function OrdersPoolMaterialsPanel({
  selectedOrderNo,
  materialsLoading,
  materialsRefreshing,
  materialsError,
  materialsRefreshWarning,
  materialTreeRows,
  onRefreshMaterialsFromErp,
  onToggleMaterialNode,
}) {
  return (
    <>
      <div className="panel-head">
        <h3>生产用料清单（子物料编码）</h3>
        <button
          disabled={!selectedOrderNo || materialsLoading || materialsRefreshing}
          onClick={onRefreshMaterialsFromErp}
        >
          刷新ERP并更新缓存
        </button>
      </div>
      {materialsLoading ? <p className="hint">子物料编码加载中...</p> : null}
      {materialsError ? <p className="error">{materialsError}</p> : null}
      {materialsRefreshWarning ? <p className="error">{materialsRefreshWarning}</p> : null}
      <SimpleTable
        columns={[
          {
            key: "child_material_code",
            title: "子物料编码",
            render: (value, row) => {
              const code = normalizeMaterialCode(value) || "-";
              const depth = Number(row?._treeDepth || 0);
              const indentWidth = `${Math.max(0, depth) * 18}px`;
              const expandable = Boolean(row?._treeExpandable);
              const expanded = Boolean(row?._treeExpanded);
              const loading = Boolean(row?._treeLoading);
              const nodeError = String(row?._treeError || "").trim();
              const toggleLabel = expanded ? `收起子物料 ${code}` : `展开子物料 ${code}`;
              return (
                <div className="material-tree-cell">
                  <span
                    className="material-tree-indent"
                    style={{ width: indentWidth }}
                    aria-hidden="true"
                  />
                  {expandable ? (
                    <button
                      type="button"
                      className="material-tree-toggle"
                      aria-label={toggleLabel}
                      disabled={loading}
                      onClick={() => onToggleMaterialNode(row)}
                    >
                      {loading ? "…" : expanded ? "-" : "+"}
                    </button>
                  ) : (
                    <span className="material-tree-placeholder" aria-hidden="true" />
                  )}
                  <span>{code}</span>
                  {loading ? (
                    <span className="hint material-tree-state">加载中...</span>
                  ) : null}
                  {nodeError ? (
                    <span className="error material-tree-state">{nodeError}</span>
                  ) : null}
                </div>
              );
            },
          },
          {
            key: "child_material_supply_type_name_cn",
            title: "物料属性",
            render: (_, row) => materialSupplyTypeName(row),
          },
          { key: "child_material_name_cn", title: "子物料名称" },
          { key: "required_qty", title: "需求数量" },
          { key: "source_bill_no", title: "来源单据" },
          { key: "issue_date", title: "领料日期" },
        ]}
        rows={materialTreeRows}
      />
      {!materialsLoading && !materialsError && materialTreeRows.length === 0 ? (
        <p className="hint">暂无可展示的子物料编码。</p>
      ) : null}
    </>
  );
}

