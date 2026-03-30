export function collectScheduledRows({
  allocations,
  compareDate,
  lineNameMap,
  orderMetaMap,
}) {
  return (allocations || [])
    .filter((item) => Number(item.workloadDays || 0) > 0)
    .slice()
    .sort((a, b) => {
      const dateCmp = compareDate(a.date, b.date);
      if (dateCmp !== 0) {
        return dateCmp;
      }
      const lineNameA = lineNameMap[a.lineId] || a.lineId;
      const lineNameB = lineNameMap[b.lineId] || b.lineId;
      const lineCmp = String(lineNameA).localeCompare(
        String(lineNameB),
        "zh-Hans-CN",
      );
      if (lineCmp !== 0) {
        return lineCmp;
      }
      const orderNoA = orderMetaMap[a.orderId]?.orderNo || a.orderId;
      const orderNoB = orderMetaMap[b.orderId]?.orderNo || b.orderId;
      return String(orderNoA).localeCompare(String(orderNoB), "zh-Hans-CN");
    });
}

export function buildScheduledOrdersExportPayload({
  scheduledRows,
  orderMetaMap,
  lineNameMap,
  manualFinishByLineOrder,
  makeLineOrderKey,
  isDurationMode,
  formatExportWorkload,
  stamp,
}) {
  if (scheduledRows.length === 0) {
    return { error: "当前没有可导出的已排产订单。" };
  }
  const sourceTextMap = {
    DURATION: "按天数排产",
    LOCK: "锁定片段",
    AUTO: "自动排产",
  };
  const unitLabel = isDurationMode ? "天" : "个";
  const headers = [
    "序号",
    "排产日期",
    "产线",
    "订单号",
    "产品名称",
    "规格",
    "批号",
    "排产量",
    "单位",
    "来源",
    "手动结束日期",
  ];
  const rows = scheduledRows.map((item, idx) => {
    const orderMeta = orderMetaMap[item.orderId] || {};
    const lineName = lineNameMap[item.lineId] || item.lineId;
    const manualFinishDate =
      manualFinishByLineOrder?.[makeLineOrderKey(item.lineId, item.orderId)] || "";
    return [
      String(idx + 1),
      String(item.date || ""),
      String(lineName || ""),
      String(orderMeta.orderNo || item.orderId || ""),
      String(orderMeta.productName || ""),
      String(orderMeta.spec || ""),
      String(orderMeta.batchNo || ""),
      formatExportWorkload(item.workloadDays, isDurationMode),
      unitLabel,
      sourceTextMap[item.source] || String(item.source || "-"),
      String(manualFinishDate || "-"),
    ];
  });
  return {
    headers,
    rows,
    fileName: `lite排产订单_${stamp}.xls`,
  };
}
