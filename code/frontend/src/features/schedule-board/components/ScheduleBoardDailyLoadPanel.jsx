import SimpleTable from "../../../components/SimpleTable";
import { formatPercent, formatQty } from "../scheduleBoardUtils";

export default function ScheduleBoardDailyLoadPanel({ dailyProcessLoadRows }) {
  return (
    <div className="panel">
      <h3>每天工序工作量与最大产能</h3>
      <p className="hint">按“日期 × 工序”展示当日已安排量、最大产能和负荷率。</p>
      <SimpleTable
        columns={[
          { key: "calendar_date", title: "日期" },
          { key: "process_name_cn", title: "工序" },
          { key: "scheduled_qty", title: "已安排量", render: (value) => formatQty(value) },
          { key: "max_capacity_qty", title: "最大产能", render: (value) => formatQty(value) },
          { key: "load_rate", title: "负荷率", render: (value) => formatPercent(value) },
          { key: "open_shift_count", title: "开放班次数", render: (value) => formatQty(value) },
        ]}
        rows={dailyProcessLoadRows}
      />
    </div>
  );
}

