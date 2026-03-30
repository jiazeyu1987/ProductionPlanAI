import { useCallback, useMemo, useState } from "react";

function createInitialFinishForm() {
  return {
    key: "",
    lineId: "",
    lineName: "",
    orderId: "",
    orderLabel: "",
    startDate: "",
    finishDate: "",
  };
}

export function useManualFinishModal({
  isDurationMode,
  horizonStart,
  manualFinishByLineOrder,
  compareDate,
  makeLineOrderKey,
  applyScenario,
  setError,
  setMessage,
}) {
  const [showFinishModal, setShowFinishModal] = useState(false);
  const [finishModalForm, setFinishModalForm] = useState(createInitialFinishForm);

  const closeFinishModal = useCallback(() => {
    setShowFinishModal(false);
    setFinishModalForm(createInitialFinishForm());
  }, []);

  const openFinishModal = useCallback(
    (orderEntry) => {
      if (!isDurationMode || !orderEntry?.orderId || !orderEntry?.lineId) {
        return;
      }
      const key = makeLineOrderKey(orderEntry.lineId, orderEntry.orderId);
      const startDate = orderEntry.segmentStartDate || horizonStart;
      const maxDate = horizonStart;
      const existingDate = manualFinishByLineOrder?.[key] || "";
      let finishDate = existingDate || maxDate;
      if (compareDate(finishDate, startDate) < 0) {
        finishDate = startDate;
      }
      if (compareDate(finishDate, maxDate) > 0) {
        finishDate = maxDate;
      }
      setFinishModalForm({
        key,
        lineId: orderEntry.lineId,
        lineName: orderEntry.lineName || orderEntry.lineId,
        orderId: orderEntry.orderId,
        orderLabel: orderEntry.orderLabel || orderEntry.orderId,
        startDate,
        finishDate,
      });
      setShowFinishModal(true);
      setError("");
      setMessage("");
    },
    [
      compareDate,
      horizonStart,
      isDurationMode,
      makeLineOrderKey,
      manualFinishByLineOrder,
      setError,
      setMessage,
    ],
  );

  const submitManualFinish = useCallback(() => {
    if (!finishModalForm.key || !finishModalForm.finishDate) {
      setError("请先选择结束日期。");
      return;
    }
    if (compareDate(finishModalForm.finishDate, finishModalForm.startDate) < 0) {
      setError("结束日期不能早于该订单在产线上的开始日期。");
      return;
    }
    if (compareDate(finishModalForm.finishDate, horizonStart) > 0) {
      setError("结束日期不能晚于当前排产日期。");
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        manualFinishByLineOrder: {
          ...(prev.manualFinishByLineOrder || {}),
          [finishModalForm.key]: finishModalForm.finishDate,
        },
      }),
      `${finishModalForm.lineName} - ${finishModalForm.orderLabel} 已结束，实际结束时间：${finishModalForm.finishDate}`,
    );
    closeFinishModal();
  }, [applyScenario, closeFinishModal, compareDate, finishModalForm, horizonStart, setError]);

  const clearManualFinish = useCallback(() => {
    if (!finishModalForm.key) {
      return;
    }
    applyScenario(
      (prev) => {
        const nextMap = { ...(prev.manualFinishByLineOrder || {}) };
        delete nextMap[finishModalForm.key];
        return {
          ...prev,
          manualFinishByLineOrder: nextMap,
        };
      },
      `${finishModalForm.lineName} - ${finishModalForm.orderLabel} 已清除报结束。`,
    );
    closeFinishModal();
  }, [applyScenario, closeFinishModal, finishModalForm]);

  const hasSavedFinish = useMemo(
    () => Boolean(manualFinishByLineOrder?.[finishModalForm.key]),
    [finishModalForm.key, manualFinishByLineOrder],
  );

  return {
    showFinishModal,
    finishModalForm,
    setFinishModalForm,
    hasSavedFinish,
    openFinishModal,
    closeFinishModal,
    submitManualFinish,
    clearManualFinish,
  };
}
