import { SHOW_BATCH_SIMULATION, TAB_BATCH, TAB_MANUAL } from "../simulationUtils";

export function SimulationTabs({ activeTab, onChangeTab }) {
  return (
    <div className="report-tabs" role="tablist" aria-label="仿真页签">
      <button
        role="tab"
        aria-selected={activeTab === TAB_MANUAL}
        className={activeTab === TAB_MANUAL ? "active" : ""}
        onClick={() => onChangeTab(TAB_MANUAL)}
      >
        手动模拟
      </button>
      {SHOW_BATCH_SIMULATION ? (
        <button
          role="tab"
          aria-selected={activeTab === TAB_BATCH}
          className={activeTab === TAB_BATCH ? "active" : ""}
          onClick={() => onChangeTab(TAB_BATCH)}
        >
          批量仿真
        </button>
      ) : null}
    </div>
  );
}

