import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";
import { formatDateTime } from "../utils/datetime";

function pickReferenceVersion(versions) {
  if (!versions || versions.length === 0) {
    return null;
  }
  const published = versions.filter((item) => item.status === "PUBLISHED");
  if (published.length > 0) {
    return published[published.length - 1];
  }
  return versions[versions.length - 1];
}

function toIntText(value) {
  const n = Number(value);
  return Number.isFinite(n) ? String(Math.round(n)) : "-";
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function isInProgressStatus(status) {
  return String(status || "").toUpperCase() === "IN_PROGRESS";
}

function commandLabel(commandType) {
  if (commandType === "LOCK") {
    return "锁单";
  }
  if (commandType === "UNLOCK") {
    return "解锁";
  }
  if (commandType === "PRIORITY") {
    return "提优先级";
  }
  if (commandType === "UNPRIORITY") {
    return "解除优先级";
  }
  return commandType;
}

function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  if (Math.abs(n - Math.round(n)) < 1e-9) {
    return `${Math.round(n)}%`;
  }
  return `${n.toFixed(2)}%`;
}

function progressText(row) {
  const total = toNumber(row?.order_qty);
  const completed = toNumber(row?.completed_qty);
  const rateRaw = Number(row?.progress_rate);
  const rate = Number.isFinite(rateRaw) ? Math.max(0, Math.min(100, rateRaw)) : (total > 1e-9 ? (completed / total) * 100 : 0);
  return `${toIntText(completed)} / ${toIntText(total)}（${formatPercent(rate)}）`;
}

function isFinalProcess(reporting) {
  const code = String(reporting?.process_code || reporting?.processCode || "").toUpperCase();
  if (code === "PROC_STERILE" || code.includes("STERILE")) {
    return true;
  }
  const name = String(reporting?.process_name_cn || reporting?.process_name || "");
  return name.includes("灭菌");
}

function ProductCell({ code, name }) {
  const productCode = String(code || "").trim();
  const productName = name || productCode || "-";
  if (!productCode) {
    return productName;
  }
  return (
    <Link className="table-link" to={`/masterdata?product_code=${encodeURIComponent(productCode)}`}>
      {productName}
    </Link>
  );
}

function normalizeProcessContexts(row) {
  if (!row || !Array.isArray(row.process_contexts)) {
    return [];
  }
  return row.process_contexts.map((item, index) => ({
    id: `${row.order_no || "order"}-ctx-${index}`,
    sequence_no: item.sequence_no ?? index + 1,
    process_code: item.process_code || "-",
    process_name_cn: item.process_name_cn || item.process_code || "-",
    workshop_code: item.workshop_code || "-",
    line_code: item.line_code || "-",
    dependency_type: item.dependency_type || "-"
  }));
}

function processContextSummary(row) {
  const contexts = normalizeProcessContexts(row);
  if (contexts.length > 0) {
    return contexts
      .map((item) => `${item.process_name_cn}(${item.workshop_code}/${item.line_code})`)
      .join(" -> ");
  }
  return row?.process_route_summary || "-";
}

export default function OrdersPoolPage() {
  const [allRows, setAllRows] = useState([]);
  const [scheduledOrderNos, setScheduledOrderNos] = useState([]);
  const [reportings, setReportings] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [expectedStartDraft, setExpectedStartDraft] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();
  const orderNoFilter = (searchParams.get("order_no") || "").trim();

  async function refresh() {
    const [poolRes, versionsRes, reportingsRes] = await Promise.all([
      loadList("/internal/v1/internal/order-pool"),
      loadList("/internal/v1/internal/schedule-versions"),
      loadList("/v1/mes/reportings")
    ]);

    setAllRows(poolRes.items ?? []);
    setReportings(reportingsRes.items ?? []);

    const versions = versionsRes.items ?? [];
    const referenceVersion = pickReferenceVersion(versions);
    if (!referenceVersion?.version_no) {
      setScheduledOrderNos([]);
      return;
    }
    const tasksRes = await loadList(`/internal/v1/internal/schedule-versions/${referenceVersion.version_no}/tasks`);
    const orderNos = [...new Set((tasksRes.items ?? []).map((item) => String(item.order_no || "").trim()).filter(Boolean))];
    setScheduledOrderNos(orderNos);
  }

  async function createCommand(orderNo, commandType) {
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const created = await postContract("/internal/v1/internal/dispatch-commands", {
        command_type: commandType,
        target_order_no: orderNo,
        target_order_type: "production",
        effective_time: new Date().toISOString(),
        reason: `快速操作：${commandType}`,
        created_by: "dispatcher01"
      });
      const commandId = String(created?.command_id || "").trim();
      if (!commandId) {
        throw new Error("创建调度指令失败：缺少指令编号。");
      }
      await postContract(`/internal/v1/internal/dispatch-commands/${commandId}/approvals`, {
        approver: "dispatcher01",
        decision: "APPROVED",
        decision_reason: `快速操作自动审批：${commandType}`,
        decision_time: new Date().toISOString()
      });
      setNotice(`已执行${commandLabel(commandType)}：${orderNo}`);
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    refresh().catch((e) => setError(e.message));
  }, []);

  const scheduledSet = useMemo(() => new Set(scheduledOrderNos), [scheduledOrderNos]);

  const filteredRows = useMemo(() => {
    if (!orderNoFilter) {
      return allRows;
    }
    const keyword = orderNoFilter.toUpperCase();
    return allRows.filter((row) => String(row.order_no || "").toUpperCase().includes(keyword));
  }, [allRows, orderNoFilter]);

  const selectedOrder = useMemo(() => {
    if (!orderNoFilter) {
      return null;
    }
    return allRows.find((row) => String(row.order_no || "").toUpperCase() === orderNoFilter.toUpperCase()) ?? null;
  }, [allRows, orderNoFilter]);

  const selectedOrderReportings = useMemo(() => {
    if (!selectedOrder?.order_no) {
      return [];
    }
    const orderNo = String(selectedOrder.order_no);
    return [...reportings]
      .filter((item) => String(item.order_no || "") === orderNo)
      .sort((a, b) => String(b.report_time || "").localeCompare(String(a.report_time || "")));
  }, [reportings, selectedOrder]);

  const finishedSummary = useMemo(() => {
    if (!selectedOrder) {
      return {
        finishedQty: 0,
        finishedDate: null,
        batchNo: "-",
        totalQty: 0
      };
    }
    const finalReports = selectedOrderReportings.filter((row) => isFinalProcess(row));
    const finishedQty = finalReports.reduce((sum, row) => sum + Number(row.report_qty || 0), 0);
    const finishedDate = finalReports.length > 0 ? finalReports[0].report_time : null;
    return {
      finishedQty,
      finishedDate,
      batchNo: selectedOrder.production_batch_no || "-",
      totalQty: selectedOrder.order_qty || 0
    };
  }, [selectedOrder, selectedOrderReportings]);

  const selectedOrderIsScheduled = selectedOrder ? scheduledSet.has(String(selectedOrder.order_no || "")) : false;
  const selectedOrderInProgress = selectedOrder ? isInProgressStatus(selectedOrder.status) : false;
  const selectedOrderProcessContexts = useMemo(() => normalizeProcessContexts(selectedOrder), [selectedOrder]);

  useEffect(() => {
    if (!selectedOrder) {
      setExpectedStartDraft("");
      return;
    }
    const expectedStart = String(
      selectedOrder.expected_start_date
      || selectedOrder.expected_start_time
      || ""
    ).trim();
    setExpectedStartDraft(expectedStart ? expectedStart.slice(0, 10) : "");
  }, [selectedOrder]);

  async function saveExpectedStartTime() {
    if (!selectedOrder?.order_no) {
      return;
    }
    if (!expectedStartDraft) {
      setError("请先选择预计开始日期。");
      return;
    }
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      await postContract(`/internal/v1/internal/order-pool/${encodeURIComponent(selectedOrder.order_no)}/patch`, {
        expected_start_date: expectedStartDraft
      });
      await refresh();
      setNotice(`已更新预计开始时间：${selectedOrder.order_no} -> ${expectedStartDraft}`);
    } catch (e) {
      setError(e.message || "保存预计开始时间失败。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h2>生产订单</h2>
      <div className="toolbar">
        <button disabled={submitting} onClick={() => refresh().catch((e) => setError(e.message))}>
          刷新
        </button>
        {orderNoFilter ? (
          <button
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              next.delete("order_no");
              setSearchParams(next);
            }}
          >
            清除生产订单定位
          </button>
        ) : null}
      </div>
      {orderNoFilter ? <p className="hint">当前按生产订单定位：{orderNoFilter}</p> : null}
      {error ? <p className="error">{error}</p> : null}
      {notice ? <p className="notice">{notice}</p> : null}

      {selectedOrder ? (
        <div className="panel">
          <h3>生产订单详情</h3>
          {selectedOrderIsScheduled ? <p className="notice">该生产订单已进入排产。</p> : null}
          <div className="table-wrap">
            <table>
              <tbody>
                <tr>
                  <th>生产订单号</th>
                  <td>{selectedOrder.order_no}</td>
                  <th>产品</th>
                  <td>
                    <ProductCell
                      code={selectedOrder.product_code}
                      name={selectedOrder.product_name_cn || selectedOrder.product_code}
                    />
                  </td>
                </tr>
                <tr>
                  <th>数量</th>
                  <td>{selectedOrder.order_qty ?? "-"}</td>
                  <th>承诺交期</th>
                  <td>{formatDateTime(selectedOrder.promised_due_date) ?? "-"}</td>
                </tr>
                <tr>
                  <th>预计开始时间</th>
                  <td>{formatDateTime(selectedOrder.expected_start_time) ?? "-"}</td>
                  <th>预计完成时间</th>
                  <td>{formatDateTime(selectedOrder.expected_finish_time) ?? "-"}</td>
                </tr>
                <tr>
                  <th>调整预计开始</th>
                  <td colSpan={3}>
                    <div className="toolbar">
                      <input
                        type="date"
                        value={expectedStartDraft}
                        onChange={(e) => setExpectedStartDraft(e.target.value)}
                        disabled={submitting}
                      />
                      <button disabled={submitting || !expectedStartDraft} onClick={() => saveExpectedStartTime().catch(() => {})}>
                        保存并重算预计完成
                      </button>
                    </div>
                  </td>
                </tr>
                <tr>
                  <th>加急</th>
                  <td>{Number(selectedOrder.urgent_flag) === 1 ? "是" : "否"}</td>
                  <th>冻结</th>
                  <td>{Number(selectedOrder.frozen_flag) === 1 ? "是" : "否"}</td>
                </tr>
                <tr>
                  <th>状态</th>
                  <td colSpan={3}>{selectedOrder.status_name_cn || selectedOrder.status || "-"}</td>
                </tr>
                <tr>
                  <th>进行中进度</th>
                  <td colSpan={3}>{selectedOrderInProgress ? progressText(selectedOrder) : "-"}</td>
                </tr>
                <tr>
                  <th>成品完成数</th>
                  <td>{toIntText(finishedSummary.finishedQty)}</td>
                  <th>批次</th>
                  <td>{finishedSummary.batchNo}</td>
                </tr>
                <tr>
                  <th>成品完成日期</th>
                  <td>{formatDateTime(finishedSummary.finishedDate) ?? "-"}</td>
                  <th>总量</th>
                  <td>{toIntText(finishedSummary.totalQty)}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <h3>历史报工数据</h3>
          <SimpleTable
            columns={[
              { key: "report_id", title: "报工ID" },
              {
                key: "process_code",
                title: "工序",
                render: (value, row) => row.process_name_cn || value || "-"
              },
              { key: "report_qty", title: "报工量", render: (value) => toIntText(value) },
              { key: "report_time", title: "报工时间" }
            ]}
            rows={selectedOrderReportings}
          />

          <h3>工序-车间-产线对应</h3>
          <SimpleTable
            columns={[
              { key: "sequence_no", title: "顺序" },
              { key: "process_name_cn", title: "工序" },
              { key: "workshop_code", title: "车间" },
              { key: "line_code", title: "产线" },
              { key: "dependency_type", title: "依赖" }
            ]}
            rows={selectedOrderProcessContexts}
          />
        </div>
      ) : null}
      {orderNoFilter && !selectedOrder ? <p className="error">未找到该生产订单，请检查生产订单号。</p> : null}

      <SimpleTable
        columns={[
          {
            key: "order_no",
            title: "生产订单号",
            render: (value) =>
              value ? (
                <Link className="table-link" to={`/orders/pool?order_no=${encodeURIComponent(value)}`}>
                  {value}
                </Link>
              ) : (
                "-"
              )
          },
          {
            key: "product_code",
            title: "产品",
            render: (value, row) => <ProductCell code={value} name={row.product_name_cn || value} />
          },
          { key: "order_qty", title: "数量" },
          {
            key: "process_route_summary",
            title: "工序/车间/产线",
            render: (_, row) => processContextSummary(row)
          },
          {
            key: "expected_start_time",
            title: "预计开始",
            render: (value) => formatDateTime(value) ?? "-"
          },
          {
            key: "expected_finish_time",
            title: "预计完成",
            render: (value) => formatDateTime(value) ?? "-"
          },
          { key: "promised_due_date", title: "承诺交期" },
          { key: "urgent_flag", title: "加急", render: (value) => (Number(value) === 1 ? "是" : "否") },
          { key: "lock_flag", title: "锁单", render: (value) => (Number(value) === 1 ? "是" : "否") },
          { key: "frozen_flag", title: "冻结", render: (value) => (Number(value) === 1 ? "是" : "否") },
          {
            key: "status",
            title: "状态",
            render: (value, row) => row.status_name_cn || value || "-"
          },
          {
            key: "progress",
            title: "进行中进度",
            render: (_, row) => (isInProgressStatus(row.status) ? progressText(row) : "-")
          },
          {
            key: "actions",
            title: "操作",
            render: (_, row) => {
              const isFrozen = Number(row.frozen_flag) === 1;
              const isLocked = Number(row.lock_flag) === 1;
              const isUrgent = Number(row.urgent_flag) === 1;
              return (
                <div className="row-actions">
                  <button disabled={submitting || isFrozen || isLocked} onClick={() => createCommand(row.order_no, "LOCK")}>
                    锁单
                  </button>
                  <button disabled={submitting || isFrozen || !isLocked} onClick={() => createCommand(row.order_no, "UNLOCK")}>
                    解锁
                  </button>
                  <button disabled={submitting || isFrozen || isUrgent} onClick={() => createCommand(row.order_no, "PRIORITY")}>
                    提优先级
                  </button>
                  <button disabled={submitting || isFrozen || !isUrgent} onClick={() => createCommand(row.order_no, "UNPRIORITY")}>
                    解除优先级
                  </button>
                </div>
              );
            }
          }
        ]}
        rows={filteredRows}
      />
    </section>
  );
}

