import SimpleTable from "../../../components/SimpleTable";
import { normalizeMaterialCode } from "..";

function normalizeSpecModel(value) {
  const text = String(value || "").trim();
  if (!text) {
    return "-";
  }

  const bracketMatch = text.match(/Value=([^\]}]+)(?=[\]}]|$)/);
  if (bracketMatch?.[1]) {
    return bracketMatch[1].trim();
  }

  const jsonLikeMatch = text.match(/"Value"\s*:\s*"([^"]+)"/);
  if (jsonLikeMatch?.[1]) {
    return jsonLikeMatch[1].trim();
  }

  return text;
}

function normalizeIssueQty(value) {
  if (value === null || value === undefined || value === "") {
    return "0";
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }
  const text = String(value).trim();
  return text || "0";
}

const TEXT = {
  title: "\u751f\u4ea7\u7528\u6599\u6e05\u5355\uff08\u5b50\u7269\u6599\u7f16\u7801\uff09",
  refreshMaterials: "\u5237\u65b0\u5b50\u7269\u6599",
  refreshSelfMade: "\u5237\u65b0\u81ea\u5236",
  refreshInventory: "\u5237\u65b0\u5e93\u5b58",
  loading: "\u5b50\u7269\u6599\u7f16\u7801\u52a0\u8f7d\u4e2d...",
  orderNo: "\u751f\u4ea7\u8ba2\u5355\u53f7",
  childCode: "\u5b50\u9879\u7269\u6599\u7f16\u7801",
  childName: "\u5b50\u9879\u7269\u6599\u540d\u79f0",
  specModel: "\u89c4\u683c\u578b\u53f7",
  issueQty: "\u5e94\u53d1\u6570\u91cf",
  expand: "\u5c55\u5f00\u5b50\u7269\u6599",
  collapse: "\u6536\u8d77\u5b50\u7269\u6599",
  rowLoading: "\u52a0\u8f7d\u4e2d...",
  empty: "\u6682\u65e0\u53ef\u5c55\u793a\u7684\u5b50\u7269\u6599\u7f16\u7801\u3002",
};

export default function OrdersPoolMaterialsPanel({
  selectedOrderNo,
  materialsLoading,
  materialsRefreshing,
  selfMadeRefreshing,
  inventoryRefreshing,
  materialsError,
  materialsRefreshWarning,
  materialTreeRows,
  onRefreshMaterialsFromErp,
  onRefreshSelfMadeFromErp,
  onRefreshInventoryFromErp,
  onToggleMaterialNode,
}) {
  const disabled = !selectedOrderNo || materialsLoading || materialsRefreshing || selfMadeRefreshing || inventoryRefreshing;
  return (
    <>
      <div className="panel-head">
        <h3>{TEXT.title}</h3>
        <div className="toolbar">
          <button disabled={disabled} onClick={onRefreshSelfMadeFromErp}>
            {TEXT.refreshSelfMade}
          </button>
          <button disabled={disabled} onClick={onRefreshInventoryFromErp}>
            {TEXT.refreshInventory}
          </button>
          <button disabled={disabled} onClick={onRefreshMaterialsFromErp}>
            {TEXT.refreshMaterials}
          </button>
        </div>
      </div>
      {materialsLoading ? <p className="hint">{TEXT.loading}</p> : null}
      {materialsError ? <p className="error">{materialsError}</p> : null}
      {materialsRefreshWarning ? <p className="error">{materialsRefreshWarning}</p> : null}
      <SimpleTable
        className="orders-pool-materials-table"
        columns={[
          {
            key: "order_no",
            title: TEXT.orderNo,
            render: (value) => String(value || selectedOrderNo || "-"),
          },
          {
            key: "child_material_code",
            title: TEXT.childCode,
            render: (value, row) => {
              const code = normalizeMaterialCode(value) || "-";
              const depth = Number(row?._treeDepth || 0);
              const indentWidth = `${Math.max(0, depth) * 18}px`;
              const expandable = Boolean(row?._treeExpandable);
              const expanded = Boolean(row?._treeExpanded);
              const loading = Boolean(row?._treeLoading);
              const nodeError = String(row?._treeError || "").trim();
              const toggleLabel = expanded ? `${TEXT.collapse} ${code}` : `${TEXT.expand} ${code}`;
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
                      {loading ? "..." : expanded ? "-" : "+"}
                    </button>
                  ) : (
                    <span className="material-tree-placeholder" aria-hidden="true" />
                  )}
                  <span>{code}</span>
                  {loading ? (
                    <span className="hint material-tree-state">{TEXT.rowLoading}</span>
                  ) : null}
                  {nodeError ? (
                    <span className="error material-tree-state">{nodeError}</span>
                  ) : null}
                </div>
              );
            },
          },
          { key: "child_material_name_cn", title: TEXT.childName },
          {
            key: "spec_model",
            title: TEXT.specModel,
            render: (value) => (
              <span className="orders-pool-material-spec">{normalizeSpecModel(value)}</span>
            ),
          },
          {
            key: "issue_qty",
            title: TEXT.issueQty,
            render: (value) => normalizeIssueQty(value),
          },
        ]}
        rows={materialTreeRows}
      />
      {!materialsLoading && !materialsError && materialTreeRows.length === 0 ? (
        <p className="hint">{TEXT.empty}</p>
      ) : null}
    </>
  );
}
