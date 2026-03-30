import { DashboardMetricCards } from "../features/dashboard/components/DashboardMetricCards";
import { DashboardMustHandlePanel } from "../features/dashboard/components/DashboardMustHandlePanel";
import { DashboardShiftLoadPanel } from "../features/dashboard/components/DashboardShiftLoadPanel";
import { useDashboardController } from "../features/dashboard/page/useDashboardController";

export default function DashboardPage() {
  const {
    stats,
    mustHandle,
    selectedDateKey,
    setSelectedDateKey,
    selectedShiftForEquipments,
    setSelectedShiftForEquipments,
    todayProcessMeta,
    shiftDemandRows,
    selectedShiftDemand,
    shiftEquipmentRows,
    selectedShiftRows,
    todayProcessRows
  } = useDashboardController();

  return (
    <section>
      <h2>经营看板</h2>
      <DashboardMetricCards stats={stats} />
      <DashboardMustHandlePanel rows={mustHandle} />
      <DashboardShiftLoadPanel
        selectedDateKey={selectedDateKey}
        onChangeDateKey={setSelectedDateKey}
        todayProcessMeta={todayProcessMeta}
        shiftDemandRows={shiftDemandRows}
        selectedShiftCode={selectedShiftForEquipments}
        onSelectShiftCode={setSelectedShiftForEquipments}
        selectedShiftDemand={selectedShiftDemand}
        shiftEquipmentRows={shiftEquipmentRows}
        selectedShiftRows={selectedShiftRows}
        todayProcessRows={todayProcessRows}
      />
    </section>
  );
}

