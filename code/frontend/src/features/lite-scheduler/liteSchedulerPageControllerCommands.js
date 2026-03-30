import {
  compareDate,
  advanceLiteScenarioOneDay,
  createDefaultLiteScenario,
  isoToday,
  makeLineOrderKey,
  PLANNING_MODE,
} from "../../utils/liteSchedulerEngine";
import { monthTextFromDate } from "./calendarUtils";
import { buildExcelTableHtml, downloadTextFile } from "./fileExportUtils";
import {
  buildScheduledOrdersExportPayload,
  collectScheduledRows,
} from "./liteSchedulerExportService";
import { formatExportWorkload, formatNumber } from "./numberFormatUtils";
import { createOrderModalForm } from "./orderModalUtils";
import { DATE_WORK_MODE } from "./liteSchedulerPageControllerConstants";
import { confirmAction } from "./uiUtils";

export function createLiteSchedulerPageCommands({
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
}) {
  function advanceOneDay() {
    setError("");
    setMessage("");
    try {
      const result = advanceLiteScenarioOneDay(scenario);
      setScenario(result.nextScenario);
      setMessage(
        `已推进到 ${result.nextScenario.horizonStart}，当日完成：${formatNumber(result.daySummary.completedWorkload)}`,
      );
    } catch (e) {
      setError(e.message || "推进失败");
    }
  }

  function replanFromToday() {
    const today = isoToday();
    applyScenario(
      (prev) => ({ ...prev, horizonStart: today }),
      `已从今天 ${today} 开始重排。`,
    );
    const month = monthTextFromDate(today);
    if (month) {
      setCalendarMonth(month);
    }
  }

  function resetScenario() {
    if (!confirmAction("确认重置当前场景吗？")) {
      return;
    }
    const nextScenario = createDefaultLiteScenario(scenario.horizonStart);
    setScenario(nextScenario);
    orderController.setOrderModalForm(createOrderModalForm(nextScenario));
    orderController.setShowOrderModal(false);
    orderController.setEditingOrderId(null);
    orderController.setShowInsertModal(false);
    finishModal.closeFinishModal();
    orderController.setInsertForm({ orderId: "", date: nextScenario.horizonStart });
    setCalendarMonth(monthTextFromDate(nextScenario.horizonStart) || calendarMonth);
    setMessage("场景已重置。");
    setError("");
  }

  function exportScheduledOrdersExcel() {
    const scheduledRows = collectScheduledRows({
      allocations: calendarPlan.allocations,
      compareDate,
      lineNameMap,
      orderMetaMap,
    });
    const payload = buildScheduledOrdersExportPayload({
      scheduledRows,
      orderMetaMap,
      lineNameMap,
      manualFinishByLineOrder: scenario.manualFinishByLineOrder,
      makeLineOrderKey,
      isDurationMode,
      formatExportWorkload,
      stamp: isoToday(),
    });
    if (payload.error) {
      setError(payload.error);
      setMessage("");
      return;
    }
    const html = buildExcelTableHtml(payload.headers, payload.rows);
    downloadTextFile(html, payload.fileName, "application/vnd.ms-excel");
    setError("");
    setMessage(`已导出排产订单：${scheduledRows.length} 条`);
  }

  function setDateWorkMode(dateText, mode) {
    if (!dateText) {
      return;
    }
    applyScenario(
      (prev) => {
        const nextMap = { ...(prev.dateWorkModeByDate || {}) };
        if (!mode) {
          delete nextMap[dateText];
        } else {
          nextMap[dateText] = mode;
        }
        return {
          ...prev,
          dateWorkModeByDate: nextMap,
        };
      },
      mode === DATE_WORK_MODE.REST
        ? `${dateText} 已设为休息。`
        : mode === DATE_WORK_MODE.WORK
          ? `${dateText} 已设为排产。`
          : `${dateText} 已恢复默认规则。`,
    );
  }

  function switchPlanningMode(nextMode) {
    if (!nextMode || scenario.planningMode === nextMode) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        planningMode: nextMode,
      }),
      nextMode === PLANNING_MODE.DURATION_MANUAL_FINISH
        ? "已切换为按天数排产。"
        : "已切换为按数量排产。",
    );
  }

  function onHorizonStartChange(nextDate) {
    applyScenario((prev) => ({
      ...prev,
      horizonStart: nextDate,
    }));
  }

  function onSkipHolidaysChange(checked) {
    applyScenario(
      (prev) => ({
        ...prev,
        skipStatutoryHolidays: checked,
      }),
      checked ? "已开启：排产跳过法定节假日。" : "已关闭：排产包含法定节假日。",
    );
  }

  function onWeekendModeChange(option) {
    applyScenario(
      (prev) => ({
        ...prev,
        weekendRestMode: option.value,
      }),
      `已设置周末模式：${option.label}。`,
    );
  }

  return {
    advanceOneDay,
    replanFromToday,
    resetScenario,
    exportScheduledOrdersExcel,
    setDateWorkMode,
    switchPlanningMode,
    onHorizonStartChange,
    onSkipHolidaysChange,
    onWeekendModeChange,
  };
}
