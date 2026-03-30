import SimpleTable from "../../../components/SimpleTable";
import { STRATEGY_LABEL } from "../scheduleVersionsConstantsUtils";
import {
  maxAllocationExplainToText,
  normalizeStrategyCode,
  processExplainToText,
  reasonToText,
} from "../scheduleVersionsAlgorithmUtils";
import { formatPercent, formatQty, toNumber } from "../scheduleVersionsFormattersUtils";

export default function ScheduleVersionAlgorithmPanel({
  algorithmDetail,
  algorithmNarrative,
  processSummaryRows,
  selectedMaxAllocationRow,
  onSelectMaxAllocationRow,
}) {
  if (!algorithmDetail) {
    return null;
  }

  const normalizedStrategy = normalizeStrategyCode(
    algorithmDetail.strategy_code ||
      algorithmDetail?.metadata?.schedule_strategy_code ||
      algorithmDetail?.metadata?.scheduleStrategyCode,
  );

  return (
    <div className="panel">
      <h3>算法说明（{algorithmDetail.version_no}）</h3>
      <p className="hint">
        策略：
        {algorithmDetail.strategy_name_cn || STRATEGY_LABEL[normalizedStrategy]}
      </p>
      {algorithmNarrative.map((line, index) => (
        <p key={`${algorithmDetail.version_no}-line-${index}`} className={index === 0 ? "notice" : "hint"}>
          {line}
        </p>
      ))}

      <h4>优先级队列预览</h4>
      <SimpleTable
        columns={[
          { key: "order_no", title: "生产订单" },
          { key: "urgent_flag", title: "加急", render: (value) => (Number(value) === 1 ? "是" : "否") },
          { key: "due_date", title: "交期" },
          { key: "status_name_cn", title: "当前状态" },
        ]}
        rows={(algorithmDetail.priority_preview ?? []).slice(0, 8)}
      />

      <h4>工序分配概览</h4>
      <SimpleTable
        columns={[
          { key: "process_name_cn", title: "工序" },
          {
            key: "max_allocation_qty",
            title: "最大配量",
            render: (_, row) => {
              const qty = toNumber(row.max_allocation_qty);
              if (qty <= 1e-9) {
                return "-";
              }
              return (
                <button type="button" className="link-button" onClick={() => onSelectMaxAllocationRow(row)}>
                  {formatQty(qty)}
                </button>
              );
            },
          },
          { key: "target_qty", title: "目标量", render: (value) => formatQty(value) },
          { key: "scheduled_qty", title: "已分配量", render: (value) => formatQty(value) },
          { key: "unscheduled_qty", title: "未排量", render: (value) => formatQty(value) },
          { key: "schedule_rate", title: "完成率", render: (value) => formatPercent(value) },
          { key: "allocation_count", title: "排产条数", render: (value) => formatQty(value) },
          { key: "explain_cn", title: "分配说明", render: (_, row) => processExplainToText(row) },
        ]}
        rows={processSummaryRows.slice(0, 8)}
      />
      {selectedMaxAllocationRow ? (
        <>
          <h4>
            最大配量说明（{selectedMaxAllocationRow.process_name_cn || selectedMaxAllocationRow.process_code || "-"})
          </h4>
          <p className="notice">{maxAllocationExplainToText(selectedMaxAllocationRow)}</p>
          <p className="hint">
            峰值订单：{selectedMaxAllocationRow.max_allocation_order_no || "-"}；峰值班次：
            {selectedMaxAllocationRow.max_allocation_date || "-"}
            {selectedMaxAllocationRow.max_allocation_shift_name_cn ||
              selectedMaxAllocationRow.max_allocation_shift_code ||
              ""}
            ；工序峰值：{formatQty(selectedMaxAllocationRow.max_allocation_qty)}
          </p>
        </>
      ) : (
        <p className="hint">点击“最大配量”数值，可查看该工序为什么会分配到这个峰值。</p>
      )}

      <h4>未排产原因</h4>
      <SimpleTable
        columns={[
          { key: "reason_code", title: "原因编码" },
          { key: "reason_text", title: "原因说明", render: (_, row) => row.reason_name_cn || reasonToText(row.reason_code) },
          { key: "count", title: "任务数", render: (value) => formatQty(value) },
        ]}
        rows={(algorithmDetail.unscheduled_reason_summary ?? []).slice(0, 8)}
      />

      {(algorithmDetail.unscheduled_samples ?? []).length > 0 ? (
        <>
          <h4>未排产样例</h4>
          <SimpleTable
            columns={[
              { key: "order_no", title: "生产订单" },
              { key: "process_name_cn", title: "工序" },
              { key: "remaining_qty", title: "未排数量", render: (value) => formatQty(value) },
              {
                key: "reasons",
                title: "说明",
                render: (value) =>
                  Array.isArray(value) && value.length > 0 ? value.map((item) => reasonToText(item)).join("；") : "-",
              },
            ]}
            rows={(algorithmDetail.unscheduled_samples ?? []).slice(0, 8)}
          />
        </>
      ) : null}
    </div>
  );
}

