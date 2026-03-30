import { Link } from "react-router-dom";

export default function RouteListPanel({
  groupedRouteRows,
  routeSaving,
  onCreateRoute,
  onEditRoute,
  onCopyRoute,
  onDeleteRoute,
  resolveRouteListDisplayCode,
  resolveRouteListProductName,
  dependencyTypeLabel,
}) {
  return (
    <div className="panel route-list-panel">
      <div className="panel-head">
        <h3>工艺路线</h3>
        <button
          className="route-add-icon-btn"
          disabled={routeSaving}
          onClick={onCreateRoute}
          aria-label="新增路线"
          title="新增路线"
        >
          +
        </button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>产品</th>
              <th>工序</th>
              <th>顺序</th>
              <th>依赖</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {groupedRouteRows.length === 0 ? (
              <tr>
                <td colSpan={6}>暂无数据</td>
              </tr>
            ) : (
              groupedRouteRows.map((row, rowIndex) => (
                <tr
                  className={row._routeProductGroupIndex % 2 === 1 ? "route-product-stripe-alt" : ""}
                  key={row.id || row.request_id || `${row.route_no || "route"}-${row.process_code || "process"}-${row.sequence_no || rowIndex}-${rowIndex}`}
                >
                  {row._routeProductRowSpan > 0 ? <td rowSpan={row._routeProductRowSpan}>{resolveRouteListDisplayCode(row)}</td> : null}
                  {row._routeProductRowSpan > 0 ? (
                    <td rowSpan={row._routeProductRowSpan}>
                      {row.product_code ? (
                        <Link className="table-link" to={`/masterdata?product_code=${encodeURIComponent(row.product_code)}`}>
                          {resolveRouteListProductName(row)}
                        </Link>
                      ) : (
                        resolveRouteListProductName(row)
                      )}
                    </td>
                  ) : null}
                  <td>
                    {row.process_code ? (
                      <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                        {row.process_name_cn || row.process_code}
                      </Link>
                    ) : (
                      row.process_name_cn || "-"
                    )}
                  </td>
                  <td>{row.sequence_no ?? "-"}</td>
                  <td>{dependencyTypeLabel(row.dependency_type)}</td>
                  {row._routeProductRowSpan > 0 ? (
                    <td rowSpan={row._routeProductRowSpan}>
                      <div className="toolbar">
                        <button disabled={routeSaving} onClick={() => onEditRoute(row.product_code)}>
                          修改
                        </button>
                        <button disabled={routeSaving} onClick={() => onCopyRoute(row.product_code)}>
                          复制
                        </button>
                        <button disabled={routeSaving} onClick={() => onDeleteRoute(row.product_code).catch(() => {})}>
                          删除
                        </button>
                      </div>
                    </td>
                  ) : null}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
