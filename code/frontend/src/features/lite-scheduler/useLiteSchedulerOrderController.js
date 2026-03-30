import { useState } from "react";
import { addDays } from "../../utils/liteSchedulerEngine";
import {
  buildInsertOrderMutation,
  buildOrderUpsertResult,
} from "./liteSchedulerPageControllerActions";
import { loadLiteScenario } from "./liteSchedulerPageControllerStorage";
import {
  createOrderModalForm,
  createOrderModalFormFromOrder,
} from "./orderModalUtils";
import { confirmAction } from "./uiUtils";

export function useLiteSchedulerOrderController({
  scenario,
  isDurationMode,
  lineNameMap,
  applyScenario,
  setError,
  setMessage,
}) {
  const [showOrderModal, setShowOrderModal] = useState(false);
  const [editingOrderId, setEditingOrderId] = useState(null);
  const [orderModalForm, setOrderModalForm] = useState(() =>
    createOrderModalForm(loadLiteScenario()),
  );
  const [showInsertModal, setShowInsertModal] = useState(false);
  const [insertForm, setInsertForm] = useState({ orderId: "", date: "" });

  function openOrderModal() {
    setOrderModalForm(createOrderModalForm(scenario));
    setEditingOrderId(null);
    setShowOrderModal(true);
    setError("");
    setMessage("");
  }

  function openEditOrderModal(orderId) {
    const order = scenario.orders.find((row) => row.id === orderId);
    if (!order) {
      setError("未找到要编辑的订单。");
      return;
    }
    setOrderModalForm(createOrderModalFormFromOrder(scenario, order));
    setEditingOrderId(order.id);
    setShowOrderModal(true);
    setError("");
    setMessage("");
  }

  function closeOrderModal() {
    setShowOrderModal(false);
    setEditingOrderId(null);
  }

  function openInsertModal(orderId) {
    setInsertForm({ orderId, date: scenario.horizonStart });
    setShowInsertModal(true);
    setError("");
    setMessage("");
  }

  function closeInsertModal() {
    setShowInsertModal(false);
    setInsertForm((prev) => ({
      ...prev,
      orderId: "",
      date: scenario.horizonStart,
    }));
  }

  function submitInsertOrder() {
    const order = scenario.orders.find((row) => row.id === insertForm.orderId);
    if (!order) {
      setError("请选择要插入的订单。");
      return;
    }
    const insertDate =
      String(insertForm.date || "").trim() || scenario.horizonStart;
    if (
      !confirmAction(
        `确认将订单 ${order.orderNo} 从 ${insertDate} 开始插入排产，并顺延其余任务吗？`,
      )
    ) {
      return;
    }
    applyScenario(
      buildInsertOrderMutation({ orderId: order.id, insertDate }),
      "订单已插入，并已顺延后续任务。",
    );
    setShowInsertModal(false);
  }

  function submitOrderFromModal() {
    const result = buildOrderUpsertResult({
      scenario,
      orderModalForm: {
        ...orderModalForm,
        dueDate:
          orderModalForm.dueDate || addDays(scenario.horizonStart, 7),
        releaseDate: orderModalForm.releaseDate || scenario.horizonStart,
      },
      editingOrderId,
      isDurationMode,
      lineNameMap,
    });
    if (result.error) {
      setError(result.error);
      return;
    }
    applyScenario(result.mutator, result.message);
    setShowOrderModal(false);
    setEditingOrderId(null);
  }

  function removeOrder(orderId) {
    const hasLock = scenario.locks.some((lock) => lock.orderId === orderId);
    if (hasLock) {
      setError("该订单存在锁定片段，请先删除锁定后再删除订单。");
      return;
    }
    if (!confirmAction("确认删除该锁定片段吗？")) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        orders: prev.orders.filter((order) => order.id !== orderId),
      }),
      "订单已删除。",
    );
  }

  return {
    showOrderModal,
    setShowOrderModal,
    editingOrderId,
    setEditingOrderId,
    orderModalForm,
    setOrderModalForm,
    showInsertModal,
    setShowInsertModal,
    insertForm,
    setInsertForm,
    openOrderModal,
    openEditOrderModal,
    closeOrderModal,
    openInsertModal,
    closeInsertModal,
    submitInsertOrder,
    submitOrderFromModal,
    removeOrder,
  };
}

