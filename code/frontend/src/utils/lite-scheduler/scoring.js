export function buildScheduleSummary({
  scenario,
  dates,
  lineRows,
  orderRows,
  totalOrders,
  totalCapacity,
  totalAssigned,
  totalRemaining,
  epsilon = 1e-6,
}) {
  const delayedOrders = orderRows.filter((row) => Number(row.delayDays) > 0).length;
  const bottleneck = lineRows
    .slice()
    .sort((a, b) => b.utilization - a.utilization)[0];

  return {
    horizonStart: scenario.horizonStart,
    horizonEnd: dates[dates.length - 1],
    totalOrders,
    totalCapacity,
    totalAssigned,
    utilization: totalCapacity > epsilon ? totalAssigned / totalCapacity : 0,
    delayedOrders,
    totalRemaining,
    bottleneckLineId: bottleneck?.lineId || null,
    bottleneckLineName: bottleneck?.lineName || null,
  };
}
