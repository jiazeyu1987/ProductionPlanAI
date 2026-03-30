import { usePlanReportsController } from "../features/plan-reports/page/usePlanReportsController";
import PlanReportsConsistencyPanel from "../features/plan-reports/components/PlanReportsConsistencyPanel";
import PlanReportsTablePanel from "../features/plan-reports/components/PlanReportsTablePanel";
import PlanReportsTabs from "../features/plan-reports/components/PlanReportsTabs";
import PlanReportsToolbar from "../features/plan-reports/components/PlanReportsToolbar";

export default function PlanReportsPage() {
  const controller = usePlanReportsController();

  return (
    <section>
      <h2>计划报表</h2>
      <PlanReportsToolbar
        loading={controller.loading}
        selectedVersionNo={controller.selectedVersionNo}
        versionRows={controller.versionRows}
        onRefresh={() => controller.refresh(controller.selectedVersionNo).catch(() => {})}
        onVersionChange={(value) => controller.refresh(value).catch(() => {})}
        onExportCurrent={() => controller.exportCurrent().catch(() => {})}
      />

      <PlanReportsTabs activeTab={controller.activeTab} onActiveTabChange={controller.setActiveTab} />

      {controller.message ? <p className="notice">{controller.message}</p> : null}
      {controller.error ? <p className="error">{controller.error}</p> : null}
      <p className="hint">
        当前计划对应排产版本：{controller.effectiveVersionNo || "实时状态"}
        {controller.effectiveVersionStatusName ? `（${controller.effectiveVersionStatusName}）` : ""}
      </p>

      <PlanReportsConsistencyPanel
        consistency={controller.consistency}
        totalMonthlyRowCount={controller.monthlyRows.length}
      />

      <PlanReportsTablePanel
        activeTab={controller.activeTab}
        monthlyColumns={controller.monthlyColumns}
        monthlyRows={controller.monthlySortedRows}
        monthlyGroups={controller.monthlyGroups}
        weeklyColumns={controller.weeklyColumns}
        weeklyRows={controller.weeklyRows}
      />
    </section>
  );
}

