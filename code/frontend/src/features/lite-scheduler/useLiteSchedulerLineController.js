import { useState } from "react";
import { makeId } from "./snapshotUtils";
import { toNumber } from "./numberFormatUtils";
import { confirmAction } from "./uiUtils";

export function useLiteSchedulerLineController({
  scenario,
  applyScenario,
  setError,
  setMessage,
}) {
  const [lineForm, setLineForm] = useState({ name: "", baseCapacity: "300" });
  const [capacityForm, setCapacityForm] = useState({
    lineId: "",
    capacity: "300",
  });

  function addLine() {
    const name =
      String(lineForm.name || "").trim() || `产线-${scenario.lines.length + 1}`;
    const baseCapacity = Math.max(0, toNumber(lineForm.baseCapacity, 300));
    applyScenario(
      (prev) => ({
        ...prev,
        lines: [
          ...prev.lines,
          {
            id: makeId("line"),
            name,
            baseCapacity,
            capacityOverrides: {},
            enabled: true,
          },
        ],
      }),
      `已新增产线：${name}`,
    );
    setLineForm({ name: "", baseCapacity: "300" });
  }

  function updateLineName(lineId, nextValue) {
    const nextName = String(nextValue || "").trim();
    if (!nextName) {
      return;
    }
    applyScenario((prev) => ({
      ...prev,
      lines: prev.lines.map((line) => {
        if (line.id !== lineId) {
          return line;
        }
        return { ...line, name: nextName };
      }),
    }));
  }

  function updateLineBaseCapacity(lineId, nextValue) {
    const baseCapacity = Math.max(0, toNumber(nextValue, 0));
    applyScenario((prev) => ({
      ...prev,
      lines: prev.lines.map((line) => {
        if (line.id !== lineId) {
          return line;
        }
        return { ...line, baseCapacity };
      }),
    }));
  }

  function removeLine(lineId) {
    if (scenario.lines.length <= 1) {
      setError("至少保留一条产线。");
      return;
    }
    const lockUsingLine = scenario.locks.some((lock) => lock.lineId === lineId);
    if (lockUsingLine) {
      setError("该产线存在锁定片段，请先删除锁定后再删除产线。");
      return;
    }
    const orderUsingLine = scenario.orders.find(
      (order) =>
        Number(order.lineWorkloads?.[lineId] || 0) > 0 ||
        Number(order.linePlanDays?.[lineId] || 0) > 0,
    );
    if (orderUsingLine) {
      setError(
        `订单 ${orderUsingLine.orderNo} 仍有该产线工作量，请先清空再删除产线。`,
      );
      return;
    }
    if (!confirmAction("确认删除该产线吗？")) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.filter((line) => line.id !== lineId),
      }),
      "产线已删除。",
    );
  }

  function saveDailyCapacity() {
    if (!capacityForm.lineId) {
      setError("请先选择产线。");
      return;
    }
    const cap = Math.max(0, toNumber(capacityForm.capacity, 0));
    if (!confirmAction("确认保存该产线日产能并重排吗？")) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.map((line) => {
          if (line.id !== capacityForm.lineId) {
            return line;
          }
          return { ...line, baseCapacity: cap, capacityOverrides: {} };
        }),
      }),
      "日产能已保存，排产已更新。",
    );
  }

  function clearLineMessage() {
    setError("");
    setMessage("");
  }

  return {
    lineForm,
    setLineForm,
    capacityForm,
    setCapacityForm,
    addLine,
    updateLineName,
    updateLineBaseCapacity,
    removeLine,
    saveDailyCapacity,
    clearLineMessage,
  };
}

