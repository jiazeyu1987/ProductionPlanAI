import {
  isFinalProcess,
  isOrderCompleted,
  normalizeOrderNoKey,
  toNumber,
} from "./formatters";

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
    dependency_type: item.dependency_type || "-",
  }));
}

function filterOrdersPoolRows(allRows, showUnfinishedOnly, orderNoFilter, productKeyword) {
  let rows = allRows || [];
  if (showUnfinishedOnly) {
    rows = rows.filter((row) => !isOrderCompleted(row));
  }
  if (orderNoFilter) {
    const orderKeyword = String(orderNoFilter || "").trim().toUpperCase();
    rows = rows.filter((row) =>
      String(row.order_no || "")
        .toUpperCase()
        .includes(orderKeyword),
    );
  }
  const keyword = String(productKeyword || "").trim().toUpperCase();
  if (!keyword) {
    return rows;
  }
  return rows.filter((row) => {
    const productCode = String(row.product_code || "").toUpperCase();
    const productName = String(row.product_name_cn || "").toUpperCase();
    return productCode.includes(keyword) || productName.includes(keyword);
  });
}

function findSelectedOrderByOrderNoFilter(allRows, orderNoFilter) {
  if (!orderNoFilter) {
    return null;
  }
  const filterKey = normalizeOrderNoKey(orderNoFilter);
  return (
    (allRows || []).find(
      (row) =>
        normalizeOrderNoKey(row.order_no) === filterKey,
    ) ?? null
  );
}

function buildSelectedOrderReportings(reportings, selectedOrderNo) {
  if (!selectedOrderNo) {
    return [];
  }
  return [...(reportings || [])]
    .filter((item) => String(item.order_no || "") === String(selectedOrderNo))
    .sort((a, b) =>
      String(b.report_time || "").localeCompare(String(a.report_time || "")),
    );
}

function buildFinishedSummary(selectedOrder, selectedOrderReportings) {
  if (!selectedOrder) {
    return {
      finishedQty: 0,
      finishedDate: null,
      batchNo: "-",
      totalQty: 0,
    };
  }
  const finalReports = (selectedOrderReportings || []).filter((row) =>
    isFinalProcess(row),
  );
  const finishedQty = finalReports.reduce(
    (sum, row) => sum + toNumber(row.report_qty),
    0,
  );
  const finishedDate =
    finalReports.length > 0 ? finalReports[0].report_time : null;
  return {
    finishedQty,
    finishedDate,
    batchNo: selectedOrder.production_batch_no || "-",
    totalQty: selectedOrder.order_qty || 0,
  };
}

export {
  buildFinishedSummary,
  buildSelectedOrderReportings,
  filterOrdersPoolRows,
  findSelectedOrderByOrderNoFilter,
  normalizeProcessContexts,
};
