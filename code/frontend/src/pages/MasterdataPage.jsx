import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList } from "../services/api";

const ROUTE_TAB = "route";
const EQUIPMENT_TAB = "equipment";

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function routeGroupKey(row) {
  return [
    row.route_no || "",
    row.route_name_cn || "",
    row.product_code || "",
    row.product_name_cn || ""
  ].join("|");
}

export default function MasterdataPage() {
  const [routes, setRoutes] = useState([]);
  const [equipments, setEquipments] = useState([]);
  const [activeTab, setActiveTab] = useState(ROUTE_TAB);
  const [searchParams, setSearchParams] = useSearchParams();
  const processCodeFilter = (searchParams.get("process_code") || "").trim();
  const productCodeFilter = (searchParams.get("product_code") || "").trim();

  useEffect(() => {
    Promise.all([loadList("/v1/mes/process-routes"), loadList("/v1/mes/equipments")])
      .then(([routeRes, equipmentRes]) => {
        setRoutes(routeRes.items ?? []);
        setEquipments(equipmentRes.items ?? []);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (processCodeFilter || productCodeFilter) {
      setActiveTab(ROUTE_TAB);
    }
  }, [processCodeFilter, productCodeFilter]);

  const filteredRoutes = useMemo(() => {
    return routes.filter((row) => {
      const matchProcess = processCodeFilter
        ? String(row.process_code || "").toUpperCase() === processCodeFilter.toUpperCase()
        : true;
      const matchProduct = productCodeFilter
        ? String(row.product_code || "").toUpperCase() === productCodeFilter.toUpperCase()
        : true;
      return matchProcess && matchProduct;
    });
  }, [routes, processCodeFilter, productCodeFilter]);

  const processDetails = useMemo(() => {
    if (!processCodeFilter) {
      return null;
    }
    const rows = routes.filter((row) => String(row.process_code || "").toUpperCase() === processCodeFilter.toUpperCase());
    if (rows.length === 0) {
      return null;
    }
    return {
      processCode: rows[0].process_code,
      processName: rows[0].process_name_cn || rows[0].process_code || "-",
      routeCount: rows.length,
      products: unique(rows.map((row) => row.product_name_cn || row.product_code)).join("、") || "-",
      dependencies: unique(rows.map((row) => row.dependency_type_name_cn || row.dependency_type)).join("、") || "-"
    };
  }, [routes, processCodeFilter]);

  const productDetails = useMemo(() => {
    if (!productCodeFilter) {
      return null;
    }
    const rows = routes.filter((row) => String(row.product_code || "").toUpperCase() === productCodeFilter.toUpperCase());
    if (rows.length === 0) {
      return null;
    }
    const orderedRows = [...rows].sort((a, b) => Number(a.sequence_no || 0) - Number(b.sequence_no || 0));
    return {
      productCode: rows[0].product_code || "-",
      productName: rows[0].product_name_cn || rows[0].product_code || "-",
      routeNo: rows[0].route_no || "-",
      routeName: rows[0].route_name_cn || rows[0].route_no || "-",
      stepCount: orderedRows.length,
      steps: orderedRows.map((row) => row.process_name_cn || row.process_code || "-").join(" → "),
      dependencies: unique(orderedRows.map((row) => row.dependency_type_name_cn || row.dependency_type)).join("、") || "-"
    };
  }, [routes, productCodeFilter]);

  const groupedRouteRows = useMemo(() => {
    if (filteredRoutes.length === 0) {
      return [];
    }

    const result = [];
    let index = 0;

    while (index < filteredRoutes.length) {
      const start = filteredRoutes[index];
      const groupKey = routeGroupKey(start);
      let span = 1;

      while (index + span < filteredRoutes.length && routeGroupKey(filteredRoutes[index + span]) === groupKey) {
        span += 1;
      }

      for (let offset = 0; offset < span; offset += 1) {
        result.push({
          ...filteredRoutes[index + offset],
          _routeProductRowSpan: offset === 0 ? span : 0
        });
      }

      index += span;
    }

    return result;
  }, [filteredRoutes]);

  return (
    <section>
      <h2>主数据管理</h2>
      <div className="report-tabs" role="tablist" aria-label="主数据页签">
        <button
          role="tab"
          aria-selected={activeTab === ROUTE_TAB}
          className={activeTab === ROUTE_TAB ? "active" : ""}
          onClick={() => setActiveTab(ROUTE_TAB)}
        >
          工艺路线
        </button>
        <button
          role="tab"
          aria-selected={activeTab === EQUIPMENT_TAB}
          className={activeTab === EQUIPMENT_TAB ? "active" : ""}
          onClick={() => setActiveTab(EQUIPMENT_TAB)}
        >
          设备能力
        </button>
      </div>

      {activeTab === ROUTE_TAB ? (
        <>
          <div className="toolbar">
            {processCodeFilter || productCodeFilter ? (
              <>
                {processCodeFilter ? <span className="hint">当前按工序定位：{processCodeFilter}</span> : null}
                {productCodeFilter ? <span className="hint">当前按产品定位：{productCodeFilter}</span> : null}
                <button
                  onClick={() => {
                    const next = new URLSearchParams(searchParams);
                    next.delete("process_code");
                    next.delete("product_code");
                    setSearchParams(next);
                  }}
                >
                  清除定位
                </button>
              </>
            ) : null}
          </div>

          {processCodeFilter && !processDetails ? <p className="error">未找到该工序，请检查工序编码。</p> : null}
          {productCodeFilter && !productDetails ? <p className="error">未找到该产品，请检查产品编码。</p> : null}
          {processDetails ? (
            <div className="panel">
              <h3>工序详情</h3>
              <div className="table-wrap">
                <table>
                  <tbody>
                    <tr>
                      <th>工序编码</th>
                      <td>{processDetails.processCode}</td>
                      <th>工序名称</th>
                      <td>{processDetails.processName}</td>
                    </tr>
                    <tr>
                      <th>涉及产品</th>
                      <td>{processDetails.products}</td>
                      <th>依赖关系</th>
                      <td>{processDetails.dependencies}</td>
                    </tr>
                    <tr>
                      <th>工艺步骤数</th>
                      <td colSpan={3}>{processDetails.routeCount}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}
          {productDetails ? (
            <div className="panel">
              <h3>产品工艺路线详情</h3>
              <div className="table-wrap">
                <table>
                  <tbody>
                    <tr>
                      <th>产品编码</th>
                      <td>{productDetails.productCode}</td>
                      <th>产品名称</th>
                      <td>{productDetails.productName}</td>
                    </tr>
                    <tr>
                      <th>路线编码</th>
                      <td>{productDetails.routeNo}</td>
                      <th>路线名称</th>
                      <td>{productDetails.routeName}</td>
                    </tr>
                    <tr>
                      <th>工艺步骤数</th>
                      <td>{productDetails.stepCount}</td>
                      <th>依赖关系</th>
                      <td>{productDetails.dependencies}</td>
                    </tr>
                    <tr>
                      <th>工序列表</th>
                      <td colSpan={3}>{productDetails.steps}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          <div className="panel">
            <h3>工艺路线</h3>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>路线</th>
                    <th>产品</th>
                    <th>工序</th>
                    <th>顺序</th>
                  </tr>
                </thead>
                <tbody>
                  {groupedRouteRows.length === 0 ? (
                    <tr>
                      <td colSpan={4}>暂无数据</td>
                    </tr>
                  ) : (
                    groupedRouteRows.map((row, rowIndex) => (
                      <tr key={row.id || row.request_id || `${row.route_no || "route"}-${row.process_code || "process"}-${row.sequence_no || rowIndex}-${rowIndex}`}>
                        {row._routeProductRowSpan > 0 ? (
                          <td rowSpan={row._routeProductRowSpan}>{row.route_name_cn || row.route_no || "-"}</td>
                        ) : null}
                        {row._routeProductRowSpan > 0 ? (
                          <td rowSpan={row._routeProductRowSpan}>
                            {row.product_code ? (
                              <Link className="table-link" to={`/masterdata?product_code=${encodeURIComponent(row.product_code)}`}>
                                {row.product_name_cn || row.product_code}
                              </Link>
                            ) : (
                              row.product_name_cn || "-"
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
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : null}
      {activeTab === EQUIPMENT_TAB ? (
        <div className="panel">
          <h3>设备能力</h3>
          <SimpleTable
            columns={[
              { key: "equipment_code", title: "设备编码" },
              { key: "workshop_code", title: "车间" },
              { key: "line_code", title: "产线" },
              {
                key: "status",
                title: "状态",
                render: (value, row) => row.status_name_cn || value || "-"
              }
            ]}
            rows={equipments}
          />
        </div>
      ) : null}
    </section>
  );
}
