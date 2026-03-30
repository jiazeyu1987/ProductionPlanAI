import { SimulationBatchPanel } from "../features/simulation/components/SimulationBatchPanel";
import { SimulationEventsPanel } from "../features/simulation/components/SimulationEventsPanel";
import { SimulationLastRunSummaryPanel } from "../features/simulation/components/SimulationLastRunSummaryPanel";
import { SimulationManualToolbar } from "../features/simulation/components/SimulationManualToolbar";
import { SimulationSummaryCards } from "../features/simulation/components/SimulationSummaryCards";
import { SimulationTabs } from "../features/simulation/components/SimulationTabs";
import { useSimulationController } from "../features/simulation/page/useSimulationController";
import { SHOW_BATCH_SIMULATION, TAB_BATCH } from "../features/simulation/simulationUtils";

export default function SimulationPage() {
  const {
    activeTab,
    setActiveTab,
    form,
    setForm,
    state,
    events,
    summary,
    message,
    error,
    loading,
    refresh,
    runBatch,
    resetBatch,
    addManualProductionOrder,
    advanceManualOneDay,
    resetManualSimulation
  } = useSimulationController();

  return (
    <section>
      <h2>仿真平台</h2>

      <SimulationTabs activeTab={activeTab} onChangeTab={setActiveTab} />

      {SHOW_BATCH_SIMULATION && activeTab === TAB_BATCH ? (
        <SimulationBatchPanel
          loading={loading}
          form={form}
          onChangeForm={setForm}
          onRunBatch={(days) => runBatch(days)}
          onResetBatch={() => resetBatch()}
          onRefresh={() => refresh()}
        />
      ) : (
        <SimulationManualToolbar
          loading={loading}
          onAddProductionOrder={() => addManualProductionOrder()}
          onAdvanceOneDay={() => advanceManualOneDay()}
          onReset={() => resetManualSimulation()}
          onRefresh={() => refresh()}
        />
      )}

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      <SimulationSummaryCards state={state} />
      <SimulationLastRunSummaryPanel summary={summary} />
      <SimulationEventsPanel events={events} />
    </section>
  );
}

