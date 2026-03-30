function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

function formatCompactQty(value) {
  const n = toNumber(value, 0);
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return String(Math.round(n));
  }
  return n.toFixed(2);
}

function materialDataStatusLabel(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "OK") {
    return "正常";
  }
  if (normalized === "MISSING_BOM") {
    return "缺少BOM";
  }
  if (normalized === "MISSING_STOCK") {
    return "缺少库存";
  }
  return value || "-";
}

export default function OrderMaterialPanel({
  orderMaterialRows,
  orderMaterialLoading,
  orderMaterialRefreshing,
  orderMaterialError,
  onRefresh,
}) {
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>物料可用量（订单子项并集）</h3>
        <div className="toolbar">
          <button onClick={onRefresh} disabled={orderMaterialLoading || orderMaterialRefreshing}>
            {orderMaterialRefreshing ? "刷新中..." : "刷新ERP并更新缓存"}
          </button>
        </div>
      </div>
      {orderMaterialLoading ? <p className="hint">物料可用量加载中...</p> : null}
      {orderMaterialError ? <p className="error">{orderMaterialError}</p> : null}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>子项物料编码</th>
              <th>子项物料名称</th>
              <th>涉及订单数</th>
              <th>涉及订单号</th>
              <th>需求总量</th>
              <th>库存量</th>
              <th>短缺量</th>
              <th>数据状态</th>
            </tr>
          </thead>
          <tbody>
            {orderMaterialRows.length === 0 ? (
              <tr>
                <td colSpan={8}>暂无数据</td>
              </tr>
            ) : (
              orderMaterialRows.map((row, idx) => (
                <tr key={`${row.child_material_code || "-"}-${idx}`}>
                  <td>{row.child_material_code || "-"}</td>
                  <td>{row.child_material_name_cn || "-"}</td>
                  <td>{toInt(row.related_order_count, 0)}</td>
                  <td>{row.related_order_nos || "-"}</td>
                  <td>{formatCompactQty(row.demand_qty)}</td>
                  <td>{formatCompactQty(row.stock_qty)}</td>
                  <td>{formatCompactQty(row.shortage_qty)}</td>
                  <td>{materialDataStatusLabel(row.data_status)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
