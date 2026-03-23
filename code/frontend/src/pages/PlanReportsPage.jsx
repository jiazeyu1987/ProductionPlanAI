import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { downloadContractFile, loadList } from "../services/api";
import { formatDateTime, formatDateTimeByField } from "../utils/datetime";

const TAB_WEEKLY = "weekly";
const TAB_MONTHLY = "monthly";

const weeklyColumnDefs = [
  { key: "production_order_no", title: "生产订单号" },
  { key: "customer_remark", title: "客户备注" },
  { key: "product_name", title: "产品名称" },
  { key: "spec_model", title: "规格型号" },
  { key: "production_batch_no", title: "生产批号" },
  { key: "order_qty", title: "订单数量" },
  { key: "packaging_form", title: "包装形式" },
  { key: "sales_order_no", title: "销售单号" },
  { key: "workshop_outer_packaging_date", title: "车间外围包装日期" },
  { key: "process_schedule_remark", title: "备注(工序排产)" }
];

const monthlyColumnDefs = [
  { key: "order_date", title: "下单日期" },
  { key: "production_order_no", title: "生产订单号" },
  { key: "customer_remark", title: "客户备注" },
  { key: "product_name", title: "产品名称" },
  { key: "spec_model", title: "规格型号" },
  { key: "production_batch_no", title: "生产批号" },
  { key: "planned_finish_date_2", title: "计划完工日期2" },
  { key: "production_date_foreign_trade", title: "生产日期（外贸）" },
  { key: "order_qty", title: "订单数量" },
  { key: "packaging_form", title: "包装形式" },
  { key: "sales_order_no", title: "销售单号" },
  { key: "purchase_due_date", title: "采购交期" },
  { key: "injection_due_date", title: "注塑交期" },
  { key: "market_remark_info", title: "市场备注信息" },
  { key: "market_demand", title: "市场需求" },
  { key: "planned_finish_date_1", title: "计划完工日期1" },
  { key: "semi_finished_code", title: "半成品代码" },
  { key: "semi_finished_inventory", title: "半成品库存" },
  { key: "semi_finished_demand", title: "半成品需求" },
  { key: "semi_finished_wip", title: "半成品在制" },
  { key: "need_order_qty", title: "需下单" },
  { key: "pending_inbound_qty", title: "待入库" },
  { key: "weekly_monthly_process_plan", title: "周度/月度计划(工序排产)" },
  { key: "workshop_outer_packaging_date", title: "车间外围包装日期" },
  { key: "note", title: "备注" },
  { key: "workshop_completed_qty", title: "完工数量(车间)" },
  { key: "workshop_completed_time", title: "完工时间（车间）" },
  { key: "outer_completed_qty", title: "完工数量（外围）" },
  { key: "outer_completed_time", title: "完工时间（外围）" },
  { key: "match_status", title: "匹配" }
];

const monthlyGroups = [
  { title: "订单基本信息", span: 14 },
  { title: "市场需求", span: 2 },
  { title: "半成品信息", span: 6 },
  { title: "订单排产信息", span: 3 },
  { title: "订单进度", span: 4 },
  { title: "匹配", span: 1 }
];

const sharedFieldPairs = [
  ["customer_remark", "customer_remark", "客户备注"],
  ["product_name", "product_name", "产品名称"],
  ["spec_model", "spec_model", "规格型号"],
  ["production_batch_no", "production_batch_no", "生产批号"],
  ["order_qty", "order_qty", "订单数量"],
  ["packaging_form", "packaging_form", "包装形式"],
  ["sales_order_no", "sales_order_no", "销售单号"],
  ["workshop_outer_packaging_date", "workshop_outer_packaging_date", "车间外围包装日期"]
];

function keyByOrder(row) {
  return String(row?.production_order_no ?? "").trim();
}

function normalizeValue(value) {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "";
  }
  return String(value).trim();
}

function sortByOrderNo(rows) {
  return [...rows].sort((a, b) => keyByOrder(a).localeCompare(keyByOrder(b), "zh-Hans-CN"));
}

function pickReportVersion(versions) {
  const rows = Array.isArray(versions) ? versions : [];
  if (rows.length === 0) {
    return "";
  }
  const published = rows.filter((item) => item.status === "PUBLISHED");
  if (published.length > 0) {
    return published[published.length - 1].version_no || "";
  }
  return rows[rows.length - 1].version_no || "";
}

function alignWeeklyRows(weeklyRows, monthlyRows) {
  const monthlyMap = new Map(monthlyRows.map((row) => [keyByOrder(row), row]));
  const visited = new Set();
  const aligned = [];

  for (const row of weeklyRows) {
    const orderNo = keyByOrder(row);
    const monthly = monthlyMap.get(orderNo);
    visited.add(orderNo);
    if (!monthly) {
      aligned.push(row);
      continue;
    }
    aligned.push({
      ...row,
      customer_remark: monthly.customer_remark,
      product_name: monthly.product_name,
      spec_model: monthly.spec_model,
      production_batch_no: monthly.production_batch_no,
      order_qty: monthly.order_qty,
      packaging_form: monthly.packaging_form,
      sales_order_no: monthly.sales_order_no,
      workshop_outer_packaging_date: monthly.workshop_outer_packaging_date,
      process_schedule_remark: row.process_schedule_remark ?? monthly.weekly_monthly_process_plan
    });
  }

  for (const row of monthlyRows) {
    const orderNo = keyByOrder(row);
    if (!visited.has(orderNo)) {
      aligned.push({
        production_order_no: row.production_order_no,
        customer_remark: row.customer_remark,
        product_name: row.product_name,
        spec_model: row.spec_model,
        production_batch_no: row.production_batch_no,
        order_qty: row.order_qty,
        packaging_form: row.packaging_form,
        sales_order_no: row.sales_order_no,
        workshop_outer_packaging_date: row.workshop_outer_packaging_date,
        process_schedule_remark: row.weekly_monthly_process_plan
      });
    }
  }
  return sortByOrderNo(aligned);
}

function validateConsistency(weeklyRows, monthlyRows) {
  const weeklyMap = new Map(weeklyRows.map((row) => [keyByOrder(row), row]));
  const monthlyMap = new Map(monthlyRows.map((row) => [keyByOrder(row), row]));
  const allOrderNos = new Set([...weeklyMap.keys(), ...monthlyMap.keys()]);
  const issues = [];

  for (const orderNo of allOrderNos) {
    const weekly = weeklyMap.get(orderNo);
    const monthly = monthlyMap.get(orderNo);
    if (!weekly || !monthly) {
      issues.push({
        production_order_no: orderNo,
        field: "订单存在性",
        weekly: weekly ? "存在" : "缺失",
        monthly: monthly ? "存在" : "缺失"
      });
      continue;
    }
    for (const [wKey, mKey, label] of sharedFieldPairs) {
      const w = normalizeValue(weekly[wKey]);
      const m = normalizeValue(monthly[mKey]);
      if (w !== m) {
        issues.push({
          production_order_no: orderNo,
          field: label,
          weekly: w || "-",
          monthly: m || "-"
        });
      }
    }
  }
  return {
    consistent: issues.length === 0,
    issueCount: issues.length,
    issues: issues.slice(0, 20)
  };
}

function PlanTable({ title, columns, rows, groupHeaders }) {
  return (
    <div className="plan-table-wrap">
      <table className="plan-table">
        <thead>
          <tr className="plan-title-row">
            <th colSpan={columns.length}>{title}</th>
          </tr>
          {groupHeaders ? (
            <tr>
              {groupHeaders.map((group) => (
                <th key={group.title} colSpan={group.span}>
                  {group.title}
                </th>
              ))}
            </tr>
          ) : null}
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.title}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>暂无数据</td>
            </tr>
          ) : (
            rows.map((row, index) => (
              <tr key={`${keyByOrder(row)}-${index}`}>
                {columns.map((column) => (
                  <td key={column.key}>
                    {column.render
                      ? column.render(row[column.key], row)
                      : formatDateTimeByField(row[column.key], column.key) ?? "-"}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default function PlanReportsPage() {
  const [activeTab, setActiveTab] = useState(TAB_MONTHLY);
  const [weeklyRawRows, setWeeklyRawRows] = useState([]);
  const [monthlyRows, setMonthlyRows] = useState([]);
  const [orderProductCodeMap, setOrderProductCodeMap] = useState({});
  const [versionRows, setVersionRows] = useState([]);
  const [selectedVersionNo, setSelectedVersionNo] = useState("");
  const [effectiveVersionNo, setEffectiveVersionNo] = useState("");
  const [effectiveVersionStatusName, setEffectiveVersionStatusName] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const weeklyRows = useMemo(
    () => alignWeeklyRows(sortByOrderNo(weeklyRawRows), sortByOrderNo(monthlyRows)),
    [weeklyRawRows, monthlyRows]
  );

  const consistency = useMemo(
    () => validateConsistency(sortByOrderNo(weeklyRawRows), sortByOrderNo(monthlyRows)),
    [weeklyRawRows, monthlyRows]
  );

  const monthlyColumns = useMemo(
    () =>
      monthlyColumnDefs.map((column) => {
        if (column.key !== "production_order_no") {
          return column;
        }
        return {
          ...column,
          render: (value) => {
            const orderNo = String(value || "").trim();
            if (!orderNo) {
              return "-";
            }
            return (
              <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(orderNo)}`}>
                {orderNo}
              </Link>
            );
          }
        };
      }),
    []
  );

  const weeklyColumns = useMemo(
    () =>
      weeklyColumnDefs.map((column) => {
        if (column.key === "production_order_no") {
          return {
            ...column,
            render: (value) => {
              const orderNo = String(value || "").trim();
              if (!orderNo) {
                return "-";
              }
              return (
                <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(orderNo)}`}>
                  {orderNo}
                </Link>
              );
            }
          };
        }
        if (column.key === "product_name") {
          return {
            ...column,
            render: (value, row) => {
              const orderNo = String(row.production_order_no || "").trim();
              const productCode = String(orderProductCodeMap[orderNo] || "").trim();
              const productName = String(value || "").trim() || "-";
              if (!productCode) {
                return productName;
              }
              return (
                <Link className="table-link" to={`/masterdata?product_code=${encodeURIComponent(productCode)}`}>
                  {productName}
                </Link>
              );
            }
          };
        }
        return column;
      }),
    [orderProductCodeMap]
  );

  async function refresh(nextVersionNo = selectedVersionNo) {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const versionsRes = await loadList("/internal/v1/internal/schedule-versions");
      const versions = versionsRes.items ?? [];
      setVersionRows(versions);

      let resolvedVersionNo = String(nextVersionNo || "").trim();
      if (resolvedVersionNo && !versions.some((row) => row.version_no === resolvedVersionNo)) {
        resolvedVersionNo = "";
      }
      if (!resolvedVersionNo) {
        resolvedVersionNo = pickReportVersion(versions);
      }
      setSelectedVersionNo(resolvedVersionNo);
      setEffectiveVersionNo(resolvedVersionNo);

      const resolvedVersion = versions.find((row) => row.version_no === resolvedVersionNo) || null;
      setEffectiveVersionStatusName(resolvedVersion?.status_name_cn || resolvedVersion?.status || (resolvedVersionNo ? "-" : "实时状态"));

      const query = resolvedVersionNo ? `?version_no=${encodeURIComponent(resolvedVersionNo)}` : "";
      const [weeklyRes, monthlyRes, orderPoolRes] = await Promise.all([
        loadList(`/v1/reports/workshop-weekly-plan${query}`),
        loadList(`/v1/reports/workshop-monthly-plan${query}`),
        loadList("/internal/v1/internal/order-pool")
      ]);
      setWeeklyRawRows(weeklyRes.items ?? []);
      setMonthlyRows(monthlyRes.items ?? []);
      const mapping = {};
      for (const row of orderPoolRes.items ?? []) {
        const orderNo = String(row.order_no || "").trim();
        const productCode = String(row.product_code || "").trim();
        if (orderNo && productCode) {
          mapping[orderNo] = productCode;
        }
      }
      setOrderProductCodeMap(mapping);
      setMessage(`周计划与月计划数据已刷新（排产版本：${resolvedVersionNo || "实时状态"}）。`);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function exportCurrent() {
    setError("");
    try {
      const query = selectedVersionNo ? `?version_no=${encodeURIComponent(selectedVersionNo)}` : "";
      if (activeTab === TAB_WEEKLY) {
        await downloadContractFile(`/v1/reports/workshop-weekly-plan/export${query}`, "workshop-weekly-plan.xlsx");
      } else {
        await downloadContractFile(`/v1/reports/workshop-monthly-plan/export${query}`, "workshop-monthly-plan.xls");
      }
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => {
    refresh("").catch((e) => setError(e.message));
  }, []);

  return (
    <section>
      <h2>计划报表</h2>
      <div className="toolbar">
        <button disabled={loading} onClick={() => refresh(selectedVersionNo).catch((e) => setError(e.message))}>
          {loading ? "刷新中..." : "刷新"}
        </button>
        <select
          value={selectedVersionNo}
          onChange={(e) => refresh(e.target.value).catch((err) => setError(err.message))}
          disabled={loading}
        >
          <option value="">自动选择（已发布优先）</option>
          {versionRows.map((row) => (
            <option key={row.version_no} value={row.version_no}>
              {row.version_no} / {row.status_name_cn || row.status || "-"}
            </option>
          ))}
        </select>
        <button disabled={loading} onClick={() => exportCurrent().catch((e) => setError(e.message))}>
          导出当前页签
        </button>
      </div>

      <div className="report-tabs" role="tablist" aria-label="计划报表页签">
        <button
          role="tab"
          aria-selected={activeTab === TAB_MONTHLY}
          className={activeTab === TAB_MONTHLY ? "active" : ""}
          onClick={() => setActiveTab(TAB_MONTHLY)}
        >
          月计划
        </button>
        <button
          role="tab"
          aria-selected={activeTab === TAB_WEEKLY}
          className={activeTab === TAB_WEEKLY ? "active" : ""}
          onClick={() => setActiveTab(TAB_WEEKLY)}
        >
          周计划
        </button>
      </div>

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}
      <p className="hint">
        当前计划对应排产版本：{effectiveVersionNo || "实时状态"}
        {effectiveVersionStatusName ? `（${effectiveVersionStatusName}）` : ""}
      </p>

      {consistency.consistent ? (
        <p className="notice">数据一致性校验通过：周计划与月计划共享字段一致（{monthlyRows.length} 条）。</p>
      ) : (
        <div className="panel">
          <h3>数据一致性告警</h3>
          <p className="error">发现 {consistency.issueCount} 处不一致，页面已按月计划字段对齐周计划显示。</p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>生产订单号</th>
                  <th>字段</th>
                  <th>周计划</th>
                  <th>月计划</th>
                </tr>
              </thead>
              <tbody>
                {consistency.issues.map((issue, index) => (
                  <tr key={`${issue.production_order_no}-${issue.field}-${index}`}>
                    <td>{issue.production_order_no || "-"}</td>
                    <td>{issue.field}</td>
                    <td>{formatDateTime(issue.weekly)}</td>
                    <td>{formatDateTime(issue.monthly)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="panel">
        {activeTab === TAB_MONTHLY ? (
          <PlanTable
            title="2月订单明细表"
            columns={monthlyColumns}
            rows={sortByOrderNo(monthlyRows)}
            groupHeaders={monthlyGroups}
          />
        ) : (
          <PlanTable
            title="周计划（3.16-3.22）"
            columns={weeklyColumns}
            rows={weeklyRows}
          />
        )}
      </div>
    </section>
  );
}
