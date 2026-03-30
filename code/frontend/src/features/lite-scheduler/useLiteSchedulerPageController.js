import { useMemo, useState } from "react";
import {
  addDays,
  buildLiteSchedule,
  compareDate,
  isoToday,
  makeLineOrderKey,
  normalizeLiteScenario,
  PLANNING_MODE,
  supportedCnHolidayYears,
} from "../../utils/liteSchedulerEngine";
import {
  buildCalendarWeeksByMonth,
  formatMonthText,
  monthTextFromDate,
  parseMonthText,
} from "./calendarUtils";
import { createOrderModalForm } from "./orderModalUtils";
import { formatSnapshotName, makeId } from "./snapshotUtils";
import {
  DATE_WORK_MODE,
  PLANNING_MODE_OPTIONS,
  WEEKEND_REST_MODE_OPTIONS,
} from "./liteSchedulerPageControllerConstants";
import { loadLiteScenario, loadLiteSnapshots, saveLiteSnapshots } from "./liteSchedulerPageControllerStorage";
import {
  buildCalendarPlan,
  buildHolidayYearHintText,
  buildLineDailyCapacityRows,
  buildLineNameMap,
  buildLineOrderColorClassMap,
  buildLineRows,
  buildModalTotals,
  buildOrderMetaMap,
  buildOrderRows,
  buildSnapshotRows,
  buildTotalDailyCapacity,
  ensureCalendarMonthFromHorizonStart,
} from "./liteSchedulerPageControllerViewModel";
import { useCalendarMonthNavigation } from "./useCalendarMonthNavigation";
import { useLiteSchedulerFormSync } from "./useLiteSchedulerFormSync";
import { useLiteSchedulerLineController } from "./useLiteSchedulerLineController";
import { useLiteSchedulerOrderController } from "./useLiteSchedulerOrderController";
import { useLiteSchedulerScenarioState } from "./useLiteSchedulerScenarioState";
import { useLiteSnapshotManager } from "./useLiteSnapshotManager";
import { useManualFinishModal } from "./useManualFinishModal";
import { createLiteSchedulerPageCommands } from "./liteSchedulerPageControllerCommands";

export { DATE_WORK_MODE, PLANNING_MODE_OPTIONS, WEEKEND_REST_MODE_OPTIONS };

export function useLiteSchedulerPageController() {
  const {
    scenario,
    setScenario,
    message,
    setMessage,
    error,
    setError,
    applyScenario,
  } = useLiteSchedulerScenarioState();

  const [calendarMonth, setCalendarMonth] = useState(() => {
    const loaded = loadLiteScenario();
    return ensureCalendarMonthFromHorizonStart({
      calendarMonth: monthTextFromDate(isoToday()) || "2026-01",
      horizonStart: loaded.horizonStart,
    });
  });

  const [, setLockForm] = useState({
    orderId: "",
    lineId: "",
    startDate: "",
    endDate: "",
    workloadDays: "1",
  });

  const [activeTab, setActiveTab] = useState("orders");

  const supportedHolidayYears = useMemo(() => supportedCnHolidayYears(), []);
  const holidayYearHintText = useMemo(
    () => buildHolidayYearHintText(scenario.horizonStart, supportedHolidayYears),
    [scenario.horizonStart, supportedHolidayYears],
  );

  const plan = useMemo(() => buildLiteSchedule(scenario), [scenario]);
  const isDurationMode =
    scenario.planningMode === PLANNING_MODE.DURATION_MANUAL_FINISH;

  const lineNameMap = useMemo(
    () => buildLineNameMap(scenario.lines),
    [scenario.lines],
  );
  const orderMetaMap = useMemo(
    () => buildOrderMetaMap(scenario.orders),
    [scenario.orders],
  );

  const calendarPlan = useMemo(
    () => buildCalendarPlan({ calendarMonth, plan, scenario }),
    [calendarMonth, plan, scenario],
  );
  const lineOrderColorClassMap = useMemo(
    () => buildLineOrderColorClassMap(calendarPlan),
    [calendarPlan.dates, calendarPlan.lineRows],
  );
  const scheduleDateSet = useMemo(
    () => new Set(calendarPlan.dates),
    [calendarPlan.dates],
  );
  const calendarWeeks = useMemo(
    () => buildCalendarWeeksByMonth(calendarMonth),
    [calendarMonth],
  );

  const lineController = useLiteSchedulerLineController({
    scenario,
    applyScenario,
    setError,
    setMessage,
  });

  const orderController = useLiteSchedulerOrderController({
    scenario,
    isDurationMode,
    lineNameMap,
    applyScenario,
    setError,
    setMessage,
  });

  useLiteSchedulerFormSync({
    scenario,
    setCapacityForm: lineController.setCapacityForm,
    setLockForm,
    setOrderModalForm: orderController.setOrderModalForm,
    setInsertForm: orderController.setInsertForm,
  });

  const finishModal = useManualFinishModal({
    isDurationMode,
    horizonStart: scenario.horizonStart,
    manualFinishByLineOrder: scenario.manualFinishByLineOrder,
    compareDate,
    makeLineOrderKey,
    applyScenario,
    setError,
    setMessage,
  });

  function applyLoadedScenario(nextScenario, successMessage = "") {
    setScenario(nextScenario);
    orderController.setOrderModalForm(createOrderModalForm(nextScenario));
    orderController.setShowOrderModal(false);
    orderController.setEditingOrderId(null);
    orderController.setShowInsertModal(false);
    finishModal.closeFinishModal();
    orderController.setInsertForm({ orderId: "", date: nextScenario.horizonStart });
    if (successMessage) {
      setMessage(successMessage);
    }
    setError("");
  }

  const snapshotManager = useLiteSnapshotManager({
    scenario,
    normalizeScenario: normalizeLiteScenario,
    makeId,
    formatSnapshotName,
    loadSnapshots: loadLiteSnapshots,
    saveSnapshots: saveLiteSnapshots,
    onLoadSnapshot: (target) => {
      const nextScenario = normalizeLiteScenario(target.scenario);
      applyLoadedScenario(nextScenario, `已读取场景：${target.name}`);
      const month = monthTextFromDate(nextScenario.horizonStart);
      if (month) {
        setCalendarMonth(month);
      }
    },
    setMessage,
    setError,
  });

  const { selectCalendarMonth, moveCalendarMonth } = useCalendarMonthNavigation({
    calendarMonth,
    setCalendarMonth,
    scenarioHorizonStart: scenario.horizonStart,
    parseMonthText,
    formatMonthText,
    monthTextFromDate,
    addDays,
    isoToday,
  });

  const commands = createLiteSchedulerPageCommands({
    scenario,
    setScenario,
    setError,
    setMessage,
    applyScenario,
    calendarMonth,
    setCalendarMonth,
    orderController,
    finishModal,
    calendarPlan,
    lineNameMap,
    orderMetaMap,
    isDurationMode,
  });

  const lineRows = useMemo(
    () => buildLineRows(plan.lineRows, scenario.lines),
    [plan.lineRows, scenario.lines],
  );
  const totalDailyCapacity = useMemo(
    () => buildTotalDailyCapacity(scenario.lines, isDurationMode),
    [scenario.lines, isDurationMode],
  );
  const orderRows = useMemo(
    () =>
      buildOrderRows({
        planOrderRows: plan.orderRows,
        lineNameMap,
        isDurationMode,
        totalDailyCapacity,
      }),
    [plan.orderRows, lineNameMap, isDurationMode, totalDailyCapacity],
  );
  const lineDailyCapacityRows = useMemo(
    () => buildLineDailyCapacityRows(scenario.lines),
    [scenario.lines],
  );
  const snapshotRows = useMemo(
    () => buildSnapshotRows(snapshotManager.snapshots),
    [snapshotManager.snapshots],
  );
  const { modalTotalWorkload, modalTotalPlanQty } = useMemo(
    () => buildModalTotals({ orderModalForm: orderController.orderModalForm, isDurationMode }),
    [orderController.orderModalForm, isDurationMode],
  );

  return {
    lineRows,
    lineDailyCapacityRows,
    orderRows,
    snapshotRows,
    modalTotalWorkload,
    modalTotalPlanQty,
    scenario,
    calendarMonth,
    message,
    error,
    activeTab,
    setActiveTab,
    isDurationMode,
    holidayYearHintText,
    plan,
    calendarPlan,
    calendarWeeks,
    scheduleDateSet,
    orderMetaMap,
    lineOrderColorClassMap,
    ...lineController,
    ...orderController,
    ...snapshotManager,
    ...finishModal,
    ...commands,
    selectCalendarMonth,
    moveCalendarMonth,
  };
}
