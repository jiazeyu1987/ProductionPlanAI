import { TAB_MONTHLY, TAB_WEEKLY } from "../planReportsUtils";

export default function PlanReportsTabs({ activeTab, onActiveTabChange }) {
  return (
    <div className="report-tabs" role="tablist" aria-label="计划报表页签">
      <button
        role="tab"
        aria-selected={activeTab === TAB_MONTHLY}
        className={activeTab === TAB_MONTHLY ? "active" : ""}
        onClick={() => onActiveTabChange(TAB_MONTHLY)}
      >
        月计划
      </button>
      <button
        role="tab"
        aria-selected={activeTab === TAB_WEEKLY}
        className={activeTab === TAB_WEEKLY ? "active" : ""}
        onClick={() => onActiveTabChange(TAB_WEEKLY)}
      >
        周计划
      </button>
    </div>
  );
}

