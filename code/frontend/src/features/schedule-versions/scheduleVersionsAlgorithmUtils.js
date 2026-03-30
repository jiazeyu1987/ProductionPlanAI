import { STRATEGY_LABEL } from "./scheduleVersionsConstantsUtils";
import { formatPercent, formatQty, toNumber } from "./scheduleVersionsFormattersUtils";

export function reasonToText(reasonCode) {
  if (reasonCode === "CAPACITY_LIMIT") {
    return "当前班次可用产能不足（人力/设备/物料或班次容量受限）";
  }
  if (reasonCode === "DEPENDENCY_LIMIT") {
    return "受前序工序约束，后序暂时不能继续排。";
  }
  if (!reasonCode) {
    return "未标记原因";
  }
  return reasonCode;
}

export function processExplainToText(row) {
  if (row?.explain_cn) {
    return row.explain_cn;
  }
  const processName = row?.process_name_cn || row?.process_code || "该工序";
  const targetQty = formatQty(row?.target_qty);
  const scheduledQty = formatQty(row?.scheduled_qty);
  const unscheduledQty = toNumber(row?.unscheduled_qty);
  const scheduleRate = formatPercent(row?.schedule_rate);
  if (unscheduledQty <= 1e-9) {
    return `${processName}目标量${targetQty}，已分配${scheduledQty}（${scheduleRate}），当前约束下已排满。`;
  }
  const reasonText = reasonToText(row?.top_block_reason_code);
  return (
    `${processName}目标量${targetQty}，已分配${scheduledQty}（${scheduleRate}），` +
    `仍有${formatQty(row?.unscheduled_qty)}未排，主要受“${reasonText}”影响。`
  );
}

export function maxAllocationExplainToText(row) {
  if (row?.max_allocation_explain_cn) {
    return row.max_allocation_explain_cn;
  }
  const processName = row?.process_name_cn || row?.process_code || "该工序";
  const qty = formatQty(row?.max_allocation_qty);
  const orderNo = row?.max_allocation_order_no || "-";
  const date = row?.max_allocation_date || "-";
  const shiftName = row?.max_allocation_shift_name_cn || row?.max_allocation_shift_code || "";
  return `${processName}在${date}${shiftName}对订单${orderNo}分配到峰值${qty}，这是在当班可行约束下形成的最大单次投放。`;
}

export function normalizeProcessSummaryRow(row) {
  const scheduledQty = toNumber(row?.scheduled_qty);
  const targetRaw = Number(row?.target_qty);
  const unscheduledRaw = Number(row?.unscheduled_qty);
  const maxAllocationRaw = Number(row?.max_allocation_qty);
  const rateRaw = Number(row?.schedule_rate);

  const targetQty = Number.isFinite(targetRaw) ? Math.max(0, targetRaw) : scheduledQty;
  const unscheduledQty = Number.isFinite(unscheduledRaw)
    ? Math.max(0, unscheduledRaw)
    : Math.max(0, targetQty - scheduledQty);
  const resolvedTargetQty = Math.max(targetQty, scheduledQty + unscheduledQty);
  const scheduleRate = Number.isFinite(rateRaw)
    ? Math.max(0, Math.min(100, rateRaw))
    : resolvedTargetQty > 1e-9
      ? Math.max(0, Math.min(100, (scheduledQty / resolvedTargetQty) * 100))
      : 100;
  const maxAllocationQty = Number.isFinite(maxAllocationRaw) ? Math.max(0, maxAllocationRaw) : scheduledQty;

  return {
    ...row,
    target_qty: resolvedTargetQty,
    max_allocation_qty: maxAllocationQty,
    unscheduled_qty: unscheduledQty,
    schedule_rate: scheduleRate,
  };
}

export function normalizeStrategyCode(value) {
  const raw = String(value || "").trim().toUpperCase();
  if (raw === "MAX_CAPACITY_FIRST") {
    return "MAX_CAPACITY_FIRST";
  }
  if (raw === "MIN_DELAY_FIRST") {
    return "MIN_DELAY_FIRST";
  }
  return "KEY_ORDER_FIRST";
}

export function strategyNarrativeLine(strategyCode) {
  const code = normalizeStrategyCode(strategyCode);
  if (code === "MAX_CAPACITY_FIRST") {
    return "本版采用“最大产能优先”，先让可一次性消化量更大的任务吃满当班能力，再补充其余订单。";
  }
  if (code === "MIN_DELAY_FIRST") {
    return "本版采用“交期最小延期优先”，先压缩最紧急交期任务的延期风险，再分配剩余能力。";
  }
  return "本版采用“关键订单优先”，先保障关键订单，再按交期与约束稳态扩展。";
}

export function buildAlgorithmNarrative(detail) {
  if (!detail) {
    return [];
  }
  const summary = detail.summary || {};
  const processSummary = Array.isArray(detail.process_summary) ? detail.process_summary : [];
  const reasonSummary = Array.isArray(detail.unscheduled_reason_summary) ? detail.unscheduled_reason_summary : [];
  const topProcess = processSummary[0];
  const topReason = reasonSummary[0];
  const taskCount = Math.round(toNumber(summary.task_count));
  const targetQty = formatQty(summary.target_qty);
  const scheduledQty = formatQty(summary.scheduled_qty);
  const completionRate = formatPercent(summary.schedule_completion_rate);
  const unscheduledTaskCount = Math.round(toNumber(summary.unscheduled_task_count));
  const strategyCode = normalizeStrategyCode(
    detail.strategy_code || detail?.metadata?.schedule_strategy_code || detail?.metadata?.scheduleStrategyCode,
  );
  const strategyLabel = STRATEGY_LABEL[strategyCode] || strategyCode;

  const lines = [];
  lines.push(`本版策略：${strategyLabel}。${strategyNarrativeLine(strategyCode)}`);
  lines.push(`本次共处理 ${taskCount} 个任务，目标量 ${targetQty}，已排 ${scheduledQty}，排产完成率 ${completionRate}。`);
  if (topProcess) {
    lines.push(
      `排产量最大的工序是“${topProcess.process_name_cn || topProcess.process_code || "-"}”，` +
        `该工序本版分配了 ${formatQty(topProcess.scheduled_qty)}。`,
    );
  }
  if (unscheduledTaskCount > 0) {
    const reasonText = reasonToText(topReason?.reason_code);
    lines.push(
      `仍有 ${unscheduledTaskCount} 个任务未排产，主要受“${reasonText}”影响` +
        `${topReason ? `（${topReason.count} 条）` : ""}。`,
    );
  } else {
    lines.push("当前没有未排产任务，说明在现有约束下已基本排满。");
  }
  return lines;
}

