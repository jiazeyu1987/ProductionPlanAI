import { TAB_MONTHLY } from "../planReportsUtils";
import PlanTable from "./PlanTable";

export default function PlanReportsTablePanel({
  activeTab,
  monthlyColumns,
  monthlyRows,
  monthlyGroups,
  weeklyColumns,
  weeklyRows,
}) {
  return (
    <div className="panel">
      {activeTab === TAB_MONTHLY ? (
        <PlanTable
          title="月计划明细表"
          columns={monthlyColumns}
          rows={monthlyRows}
          groupHeaders={monthlyGroups}
        />
      ) : (
        <PlanTable title="周计划明细表" columns={weeklyColumns} rows={weeklyRows} />
      )}
    </div>
  );
}

