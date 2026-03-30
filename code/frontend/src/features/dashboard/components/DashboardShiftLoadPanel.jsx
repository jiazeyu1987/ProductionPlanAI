import { Link } from "react-router-dom";
import { formatQty } from "../dashboardFormatters";
import { DashboardDateToolbar } from "./DashboardDateToolbar";
import { DashboardProcessSummaryTable } from "./DashboardProcessSummaryTable";
import { DashboardShiftDemandTable } from "./DashboardShiftDemandTable";
import { DashboardShiftEquipmentTable } from "./DashboardShiftEquipmentTable";
import { DashboardShiftProcessLoadTable } from "./DashboardShiftProcessLoadTable";

export function DashboardShiftLoadPanel({
  selectedDateKey,
  onChangeDateKey,
  todayProcessMeta,
  shiftDemandRows,
  selectedShiftCode,
  onSelectShiftCode,
  selectedShiftDemand,
  shiftEquipmentRows,
  selectedShiftRows,
  todayProcessRows
}) {
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>工作安排与班次负荷</h3>
        <Link to="/schedule/board">前往调度台</Link>
      </div>
      <DashboardDateToolbar selectedDateKey={selectedDateKey} onChangeDateKey={onChangeDateKey} />
      <p className="hint">
        统计日期：{selectedDateKey}
        {todayProcessMeta?.versionNo
          ? `；版本：${todayProcessMeta.versionNo}${
              todayProcessMeta.versionStatus ? ` / ${todayProcessMeta.versionStatus}` : ""
            }`
          : "；暂无可用排产版本"}
        {todayProcessMeta?.versionNo ? "；最大产能按班次人力/设备约束计算。" : ""}
      </p>
      <p className="hint">班次总需求：按已安排量折算总需人力、总需设备。</p>

      <DashboardShiftDemandTable
        rows={shiftDemandRows}
        selectedShiftCode={selectedShiftCode}
        onSelectShiftCode={onSelectShiftCode}
      />

      {selectedShiftDemand ? (
        <>
          <p className="hint">
            设备清单：{selectedDateKey} {selectedShiftDemand.shift_name_cn}
            （总需设备 {formatQty(selectedShiftDemand.required_machines_total)} 台）。
          </p>
          <DashboardShiftEquipmentTable rows={shiftEquipmentRows} />
        </>
      ) : null}

      <p className="hint">班次明细：按班次与工序展示负荷。</p>
      <DashboardShiftProcessLoadTable rows={selectedShiftRows} />
      <p className="hint">日期汇总：按工序合并（跨班次）。</p>
      <DashboardProcessSummaryTable rows={todayProcessRows} />
    </div>
  );
}

