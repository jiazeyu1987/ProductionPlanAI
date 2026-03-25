import { useEffect, useMemo, useState } from "react";
import SimpleTable from "../components/SimpleTable";
import {
  addDays,
  advanceLiteScenarioOneDay,
  buildLiteSchedule,
  createDefaultLiteScenario,
  diffDays,
  isoToday,
  normalizeLiteScenario
} from "../utils/liteSchedulerEngine";

const STORAGE_KEY = "liteScheduler.scenario.v1";
const LITE_TAB_ITEMS = [
  { id: "orders", label: "订单录入" },
  { id: "schedule", label: "每日排产" },
  { id: "capacity", label: "产能调整" },
  { id: "lines", label: "产线管理" }
];
const WEEKDAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

function parseIsoAsUtcDate(dateText) {
  const text = String(dateText || "");
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }
  return new Date(Date.UTC(year, month - 1, day));
}

function formatUtcDateToIso(date) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseMonthText(monthText) {
  const text = String(monthText || "").trim();
  const match = text.match(/^(\d{4})-(\d{2})$/);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
    return null;
  }
  return { year, month };
}

function formatMonthText(year, month) {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}`;
}

function getMonthDayCount(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function monthTextFromDate(dateText) {
  const parsedDate = parseIsoAsUtcDate(dateText);
  if (!parsedDate) {
    return null;
  }
  return formatMonthText(parsedDate.getUTCFullYear(), parsedDate.getUTCMonth() + 1);
}

function buildCalendarWeeksByMonth(monthText) {
  const parsed = parseMonthText(monthText);
  if (!parsed) {
    return [];
  }
  const { year, month } = parsed;
  const daysInMonth = getMonthDayCount(year, month);
  const monthStart = new Date(Date.UTC(year, month - 1, 1));
  const leadingEmptyCount = (monthStart.getUTCDay() + 6) % 7;

  const cells = [];
  for (let i = 0; i < leadingEmptyCount; i += 1) {
    cells.push(null);
  }
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(formatUtcDateToIso(new Date(Date.UTC(year, month - 1, day))));
  }
  const trailingEmptyCount = (7 - (cells.length % 7)) % 7;
  for (let i = 0; i < trailingEmptyCount; i += 1) {
    cells.push(null);
  }

  const weeks = [];
  for (let idx = 0; idx < cells.length; idx += 7) {
    weeks.push(cells.slice(idx, idx + 7));
  }
  return weeks;
}

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function formatNumber(value, digits = 1) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return n.toFixed(digits);
}

function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return "-";
  }
  return `${(n * 100).toFixed(1)}%`;
}

function makeId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function loadScenario() {
  if (typeof window === "undefined") {
    return createDefaultLiteScenario();
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return createDefaultLiteScenario();
    }
    return normalizeLiteScenario(JSON.parse(raw));
  } catch {
    return createDefaultLiteScenario();
  }
}

function saveScenario(scenario) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(scenario));
}

function downloadJson(fileName, payload) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function confirmAction(message) {
  if (typeof window === "undefined") {
    return true;
  }
  const ua = String(window.navigator?.userAgent || "").toLowerCase();
  if (ua.includes("jsdom")) {
    return true;
  }
  if (typeof window.confirm !== "function") {
    return true;
  }
  try {
    const result = window.confirm(message);
    return result !== false;
  } catch {
    return true;
  }
}

function formatAutoOrderNo(seqValue) {
  const seq = Math.max(1, Math.round(toNumber(seqValue, 1)));
  return `PO-${String(seq).padStart(4, "0")}`;
}

function buildModalLineTotals(scenario, prevLineTotals = {}) {
  const totals = {};
  scenario.lines.forEach((line) => {
    const prevValue = prevLineTotals?.[line.id];
    totals[line.id] = prevValue === undefined || prevValue === null ? "0" : String(prevValue);
  });
  return totals;
}

function createOrderModalForm(scenario) {
  return {
    orderNo: formatAutoOrderNo(scenario?.nextOrderSeq || 1),
    dueDate: addDays(scenario.horizonStart, 7),
    releaseDate: scenario.horizonStart,
    priority: "NORMAL",
    lineTotals: buildModalLineTotals(scenario)
  };
}

function createOrderModalFormFromOrder(scenario, order) {
  const lineTotals = buildModalLineTotals(scenario);
  Object.entries(order?.lineWorkloads || {}).forEach(([lineId, value]) => {
    if (Object.prototype.hasOwnProperty.call(lineTotals, lineId)) {
      lineTotals[lineId] = String(value);
    }
  });
  return {
    orderNo: String(order?.orderNo || ""),
    dueDate: order?.dueDate || addDays(scenario.horizonStart, 7),
    releaseDate: order?.releaseDate || scenario.horizonStart,
    priority: order?.priority === "URGENT" ? "URGENT" : "NORMAL",
    lineTotals
  };
}

export default function LiteSchedulerPage() {
  const [scenario, setScenario] = useState(loadScenario);
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const loaded = loadScenario();
    return monthTextFromDate(loaded.horizonStart) || monthTextFromDate(isoToday()) || "2026-01";
  });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [lineForm, setLineForm] = useState({ name: "", baseCapacity: "300" });
  const [capacityForm, setCapacityForm] = useState({
    lineId: "",
    capacity: "300"
  });
  const [lockForm, setLockForm] = useState({
    orderId: "",
    lineId: "",
    startDate: "",
    endDate: "",
    workloadDays: "1"
  });

  const [showOrderModal, setShowOrderModal] = useState(false);
  const [editingOrderId, setEditingOrderId] = useState(null);
  const [orderModalForm, setOrderModalForm] = useState(() => createOrderModalForm(loadScenario()));
  const [activeTab, setActiveTab] = useState("orders");
  const [showInsertModal, setShowInsertModal] = useState(false);
  const [insertForm, setInsertForm] = useState({
    orderId: "",
    date: ""
  });

  const plan = useMemo(() => buildLiteSchedule(scenario), [scenario]);
  const lineNameMap = useMemo(() => {
    return Object.fromEntries(scenario.lines.map((line) => [line.id, line.name]));
  }, [scenario.lines]);
  const orderNoMap = useMemo(() => {
    return Object.fromEntries(scenario.orders.map((order) => [order.id, order.orderNo]));
  }, [scenario.orders]);
  const calendarPlan = useMemo(() => {
    const parsedMonth = parseMonthText(calendarMonth);
    if (!parsedMonth) {
      return plan;
    }
    const monthEndIso = `${calendarMonth}-${String(getMonthDayCount(parsedMonth.year, parsedMonth.month)).padStart(2, "0")}`;
    const daysToMonthEnd = diffDays(monthEndIso, scenario.horizonStart) + 1;
    const expandedDays = Math.max(1, scenario.horizonDays, daysToMonthEnd);
    if (expandedDays <= scenario.horizonDays) {
      return plan;
    }
    return buildLiteSchedule({
      ...scenario,
      horizonDays: expandedDays
    });
  }, [calendarMonth, plan, scenario]);
  const scheduleDateSet = useMemo(() => new Set(calendarPlan.dates), [calendarPlan.dates]);
  const calendarWeeks = useMemo(() => buildCalendarWeeksByMonth(calendarMonth), [calendarMonth]);

  useEffect(() => {
    saveScenario(scenario);
  }, [scenario]);

  useEffect(() => {
    setCapacityForm((prev) => {
      const safeLine = scenario.lines.find((line) => line.id === prev.lineId) || scenario.lines[0];
      const nextLineId = safeLine?.id || "";
      const nextCapacity = prev.lineId === nextLineId ? prev.capacity : String(safeLine?.baseCapacity ?? 0);
      return {
        ...prev,
        lineId: nextLineId,
        capacity: nextCapacity
      };
    });

    setLockForm((prev) => ({
      ...prev,
      lineId: scenario.lines.find((line) => line.id === prev.lineId)?.id || scenario.lines[0]?.id || "",
      orderId: scenario.orders.find((order) => order.id === prev.orderId)?.id || scenario.orders[0]?.id || "",
      startDate: prev.startDate || scenario.horizonStart,
      endDate: prev.endDate || addDays(scenario.horizonStart, 2)
    }));

    setOrderModalForm((prev) => ({
      ...prev,
      releaseDate: prev.releaseDate || scenario.horizonStart,
      dueDate: prev.dueDate || addDays(scenario.horizonStart, 7),
      lineTotals: buildModalLineTotals(scenario, prev.lineTotals)
    }));

    setInsertForm((prev) => ({
      orderId: scenario.orders.find((order) => order.id === prev.orderId)?.id || prev.orderId || "",
      date: prev.date || scenario.horizonStart
    }));
  }, [scenario.horizonStart, scenario.lines, scenario.orders]);

  function applyScenario(mutator, successMessage = "") {
    setError("");
    setMessage("");
    try {
      setScenario((prev) => normalizeLiteScenario(mutator(normalizeLiteScenario(prev))));
      if (successMessage) {
        setMessage(successMessage);
      }
    } catch (e) {
      setError(e.message || "操作失败");
    }
  }

  function addLine() {
    const name = String(lineForm.name || "").trim() || `产线-${scenario.lines.length + 1}`;
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
            enabled: true
          }
        ]
      }),
      `已新增产线：${name}`
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
      })
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
      })
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

    const orderUsingLine = scenario.orders.find((order) => Number(order.lineWorkloads?.[lineId] || 0) > 0);
    if (orderUsingLine) {
      setError(`订单 ${orderUsingLine.orderNo} 仍有该产线工作量，请先清空再删除产线。`);
      return;
    }

    if (!confirmAction("确认删除该产线吗？")) {
      return;
    }

    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.filter((line) => line.id !== lineId)
      }),
      "产线已删除。"
    );
  }

  function saveDailyCapacity() {
    if (!capacityForm.lineId) {
      setError("请先选择产线。");
      return;
    }
    const cap = Math.max(0, toNumber(capacityForm.capacity, 0));
    if (!confirmAction("确认保存该产线的日产能吗？保存后将立即重排。")) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        lines: prev.lines.map((line) => {
          if (line.id !== capacityForm.lineId) {
            return line;
          }
          return {
            ...line,
            baseCapacity: cap,
            capacityOverrides: {}
          };
        })
      }),
      "日产能已保存，排产已更新。"
    );
  }

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
    setInsertForm({
      orderId,
      date: scenario.horizonStart
    });
    setShowInsertModal(true);
    setError("");
    setMessage("");
  }

  function closeInsertModal() {
    setShowInsertModal(false);
    setInsertForm((prev) => ({
      ...prev,
      orderId: "",
      date: scenario.horizonStart
    }));
  }

  function submitInsertOrder() {
    const order = scenario.orders.find((row) => row.id === insertForm.orderId);
    if (!order) {
      setError("请选择要插入的订单。");
      return;
    }
    const insertDate = String(insertForm.date || "").trim() || scenario.horizonStart;
    if (!confirmAction(`确认将订单 ${order.orderNo} 从 ${insertDate} 开始插入排产，并顺延其他任务吗？`)) {
      return;
    }

    applyScenario(
      (prev) => {
        const safeInsertDate = insertDate || prev.horizonStart;
        const sortedIds = prev.orders
          .slice()
          .sort((a, b) => (toNumber(a.orderSeq, 0) || 0) - (toNumber(b.orderSeq, 0) || 0))
          .map((row) => row.id);
        const withoutTarget = sortedIds.filter((id) => id !== order.id);
        const reordered = [order.id, ...withoutTarget];
        const seqMap = Object.fromEntries(reordered.map((id, idx) => [id, idx + 1]));

        const nextOrders = prev.orders.map((row) => {
          if (row.id !== order.id) {
            return {
              ...row,
              orderSeq: seqMap[row.id] || toNumber(row.orderSeq, 1)
            };
          }
          return {
            ...row,
            releaseDate: safeInsertDate,
            orderSeq: 1
          };
        });

        return {
          ...prev,
          orders: nextOrders,
          locks: prev.locks.filter((lock) => Number(lock.seq) >= 0)
        };
      },
      `订单 ${order.orderNo} 已插入到 ${insertDate}，其余任务已顺延。`
    );

    setShowInsertModal(false);
  }

  function updateOrderLineTotal(lineId, value) {
    setOrderModalForm((prev) => ({
      ...prev,
      lineTotals: {
        ...prev.lineTotals,
        [lineId]: value
      }
    }));
  }

  function submitOrderFromModal() {
    const inputOrderNo = String(orderModalForm.orderNo || "").trim();
    const autoOrderNo = formatAutoOrderNo(scenario?.nextOrderSeq || 1);
    const editingOrderNo = scenario.orders.find((order) => order.id === editingOrderId)?.orderNo || "";
    const orderNo = inputOrderNo || editingOrderNo || autoOrderNo;
    const dueDate = orderModalForm.dueDate || addDays(scenario.horizonStart, 7);
    const releaseDate = orderModalForm.releaseDate || scenario.horizonStart;
    const lineWorkloads = {};

    Object.entries(orderModalForm.lineTotals || {}).forEach(([lineIdRaw, value]) => {
      const lineId = String(lineIdRaw || "").trim();
      const workload = Math.max(0, toNumber(value, 0));
      if (!lineId || !lineNameMap[lineId] || workload <= 0) {
        return;
      }
      lineWorkloads[lineId] = (lineWorkloads[lineId] || 0) + workload;
    });

    const totalWorkload = Object.values(lineWorkloads).reduce((sum, value) => sum + value, 0);
    if (totalWorkload <= 0) {
      setError("请至少填写一条产线的工作量。");
      return;
    }
    if (editingOrderId) {
      applyScenario(
        (prev) => ({
          ...prev,
          orders: prev.orders.map((order) => {
            if (order.id !== editingOrderId) {
              return order;
            }
            return {
              ...order,
              orderNo,
              workloadDays: totalWorkload,
              completedDays: Math.min(toNumber(order.completedDays, 0), totalWorkload),
              dueDate,
              releaseDate,
              priority: "NORMAL",
              lineWorkloads
            };
          })
        }),
        `订单已更新：${orderNo}`
      );
    } else {
      applyScenario(
        (prev) => ({
          ...prev,
          nextOrderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))) + 1,
          orders: [
            ...prev.orders,
            {
              id: makeId("order"),
              orderNo,
              orderSeq: Math.max(1, Math.round(toNumber(prev.nextOrderSeq, 1))),
              workloadDays: totalWorkload,
              completedDays: 0,
              dueDate,
              releaseDate,
              priority: "NORMAL",
              lineWorkloads
            }
          ]
        }),
        `订单已新增：${orderNo}`
      );
    }

    setShowOrderModal(false);
    setEditingOrderId(null);
  }

  function removeOrder(orderId) {
    const hasLock = scenario.locks.some((lock) => lock.orderId === orderId);
    if (hasLock) {
      setError("该订单存在锁定片段，请先删除锁定后再删除订单。");
      return;
    }

    if (!confirmAction("确认删除该订单吗？")) {
      return;
    }
    applyScenario(
      (prev) => ({
        ...prev,
        orders: prev.orders.filter((order) => order.id !== orderId)
      }),
      "订单已删除。"
    );
  }

  function addLock() {
    if (!lockForm.orderId || !lockForm.lineId) {
      setError("请先选择订单和产线。");
      return;
    }
    const startDate = lockForm.startDate || scenario.horizonStart;
    const endDate = lockForm.endDate || startDate;
    const workloadDays = Math.max(0.1, toNumber(lockForm.workloadDays, 0));

    applyScenario(
      (prev) => ({
        ...prev,
        locks: [
          ...prev.locks,
          {
            id: makeId("lock"),
            orderId: lockForm.orderId,
            lineId: lockForm.lineId,
            startDate,
            endDate,
            workloadDays,
            seq: prev.locks.length + 1
          }
        ]
      }),
      "锁定片段已新增。"
    );
  }

  function removeLock(lockId) {
    if (!confirmAction("确认删除该锁定片段吗？")) {
      return;
    }

    applyScenario(
      (prev) => ({
        ...prev,
        locks: prev.locks.filter((lock) => lock.id !== lockId)
      }),
      "锁定片段已删除。"
    );
  }

  function advanceOneDay() {
    setError("");
    setMessage("");
    try {
      const result = advanceLiteScenarioOneDay(scenario);
      setScenario(result.nextScenario);
      setMessage(`已推进到 ${result.nextScenario.horizonStart}，当日完成：${formatNumber(result.daySummary.completedWorkload)}`);
    } catch (e) {
      setError(e.message || "推进失败");
    }
  }

  function replanFromToday() {
    const today = isoToday();
    applyScenario(
      (prev) => ({
        ...prev,
        horizonStart: today
      }),
      `已从今天 ${today} 开始重排。`
    );
    const month = monthTextFromDate(today);
    if (month) {
      setCalendarMonth(month);
    }
  }

  function resetScenario() {
    if (!confirmAction("确认重置默认场景吗？当前录入的数据会被清空。")) {
      return;
    }

    const nextScenario = createDefaultLiteScenario(scenario.horizonStart);
    setScenario(nextScenario);
    setOrderModalForm(createOrderModalForm(nextScenario));
    setShowOrderModal(false);
    setEditingOrderId(null);
    setShowInsertModal(false);
    setInsertForm({
      orderId: "",
      date: nextScenario.horizonStart
    });
    setCalendarMonth(monthTextFromDate(nextScenario.horizonStart) || calendarMonth);
    setMessage("场景已重置。");
    setError("");
  }

  function selectCalendarMonth(monthText) {
    const parsed = parseMonthText(monthText);
    if (!parsed) {
      return;
    }
    const { year, month } = parsed;
    const nextMonthText = formatMonthText(year, month);
    setCalendarMonth(nextMonthText);
  }

  function moveCalendarMonth(step) {
    const fallbackMonth = monthTextFromDate(scenario.horizonStart) || monthTextFromDate(addDays(isoToday(), 0));
    const base = parseMonthText(calendarMonth) || parseMonthText(fallbackMonth);
    if (!base) {
      return;
    }
    const next = new Date(Date.UTC(base.year, base.month - 1 + Number(step || 0), 1));
    const nextMonthText = formatMonthText(next.getUTCFullYear(), next.getUTCMonth() + 1);
    selectCalendarMonth(nextMonthText);
  }

  function exportScenario() {
    downloadJson(`lite-schedule-${scenario.horizonStart}.json`, scenario);
    setMessage("场景已导出。");
  }

  async function importScenario(event) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setError("");
    setMessage("");
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      const nextScenario = normalizeLiteScenario(parsed);
      setScenario(nextScenario);
      setOrderModalForm(createOrderModalForm(nextScenario));
      setShowOrderModal(false);
      setEditingOrderId(null);
      setShowInsertModal(false);
      setInsertForm({
        orderId: "",
        date: nextScenario.horizonStart
      });
      setCalendarMonth(monthTextFromDate(nextScenario.horizonStart) || calendarMonth);
      setMessage("场景导入成功。");
    } catch (e) {
      setError(e.message || "导入失败，请检查文件格式。");
    } finally {
      event.target.value = "";
    }
  }

  const lineRows = plan.lineRows.map((row) => ({
    id: row.lineId,
    line_name: row.lineName,
    base_capacity: scenario.lines.find((line) => line.id === row.lineId)?.baseCapacity ?? 0,
    assigned_total: row.assignedTotal,
    capacity_total: row.capacityTotal,
    utilization: row.utilization
  }));
  const totalDailyCapacity = scenario.lines
    .filter((line) => line.enabled !== false)
    .reduce((sum, line) => sum + Math.max(0, toNumber(line.baseCapacity, 0)), 0);

  const orderRows = plan.orderRows.map((row) => {
    const lineWorkloadDesc = Object.entries(row.lineWorkloads || {})
      .map(([lineId, qty]) => `${lineNameMap[lineId] || lineId}: ${formatNumber(qty)}个`)
      .join(" | ");

    return {
      id: row.id,
      order_no: row.orderNo,
      priority: row.priority === "URGENT" ? "加急" : "常规",
      workload_qty: row.workloadDays,
      completed_qty: row.completedDays,
      remaining_qty: row.remainingDays,
      remaining_plan_days: totalDailyCapacity > 0 ? row.remainingDays / totalDailyCapacity : 0,
      line_workloads: lineWorkloadDesc || "-",
      release_date: row.releaseDate,
      due_date: row.dueDate,
      completion_date: row.completionDate || "-",
      delay_days: row.delayDays ?? 0,
      reason: row.reason
    };
  });

  const lockRows = scenario.locks.map((lock) => {
    const order = scenario.orders.find((row) => row.id === lock.orderId);
    const line = scenario.lines.find((row) => row.id === lock.lineId);
    return {
      id: lock.id,
      order_no: order?.orderNo || lock.orderId,
      line_name: line?.name || lock.lineId,
      start_date: lock.startDate,
      end_date: lock.endDate,
      workload_qty: lock.workloadDays
    };
  });

  const lineDailyCapacityRows = scenario.lines.map((line) => ({
    id: line.id,
    line_name: line.name,
    daily_capacity: line.baseCapacity
  }));

  const modalTotalWorkload = Object.values(orderModalForm.lineTotals || {}).reduce((sum, value) => {
    return sum + Math.max(0, toNumber(value, 0));
  }, 0);

  return (
    <section className="lite-page">
      <h2>璞慧排产</h2>
      <p className="hint">订单录入按“个数”填写，系统同优先级并按订单顺序向后排产。</p>

      <div className="toolbar">
        <label>
          排产起始日
          <input
            type="date"
            data-testid="horizon-start-input"
            value={scenario.horizonStart}
            onChange={(e) => applyScenario((prev) => ({ ...prev, horizonStart: e.target.value }))}
          />
        </label>
        <button data-testid="advance-day-btn" onClick={advanceOneDay}>
          推进1天
        </button>
        <button data-testid="replan-today-btn" onClick={replanFromToday}>
          从今天重排
        </button>
        <button onClick={exportScenario}>导出场景</button>
        <label className="lite-file-label">
          导入场景
          <input type="file" accept="application/json" onChange={importScenario} />
        </label>
        <button className="btn-danger-text" onClick={resetScenario}>
          重置默认
        </button>
      </div>

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      {plan.warnings.length > 0 ? (
        <div className="panel lite-warning">
          <h3>重排提醒</h3>
          <ul className="lite-warning-list">
            {plan.warnings.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="card-grid">
        <article className="metric-card">
          <span>订单数</span>
          <strong>{plan.summary.totalOrders}</strong>
        </article>
        <article className="metric-card">
          <span>总分配 / 总产能</span>
          <strong>
            {formatNumber(plan.summary.totalAssigned)} / {formatNumber(plan.summary.totalCapacity)}
          </strong>
        </article>
        <article className="metric-card">
          <span>利用率</span>
          <strong>{formatPercent(plan.summary.utilization)}</strong>
        </article>
        <article className="metric-card">
          <span>延期订单 / 剩余工作量</span>
          <strong>
            {plan.summary.delayedOrders} / {formatNumber(plan.summary.totalRemaining)}
          </strong>
        </article>
      </div>

      <div className="panel">
        <div className="report-tabs">
          {LITE_TAB_ITEMS.map((tab) => (
            <button
              key={tab.id}
              data-testid={`lite-tab-${tab.id}`}
              className={activeTab === tab.id ? "active" : ""}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {activeTab === "lines" ? (
      <div className="panel">
        <h3>产线管理</h3>
        <div className="toolbar">
          <label>
            产线名称
            <input
              placeholder="例如：导管产线-2"
              value={lineForm.name}
              onChange={(e) => setLineForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            默认日产能(个/天)
            <input
              type="number"
              min="0"
              step="0.1"
              value={lineForm.baseCapacity}
              onChange={(e) => setLineForm((prev) => ({ ...prev, baseCapacity: e.target.value }))}
            />
          </label>
          <button onClick={addLine}>新增产线</button>
        </div>

        <SimpleTable
          columns={[
            {
              key: "line_name",
              title: "产线",
              render: (value, row) => (
                <input defaultValue={String(value || "")} onBlur={(e) => updateLineName(row.id, e.target.value)} />
              )
            },
            {
              key: "base_capacity",
              title: "默认日产能(个/天)",
              render: (value, row) => (
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  defaultValue={String(value)}
                  onBlur={(e) => updateLineBaseCapacity(row.id, e.target.value)}
                />
              )
            },
            { key: "assigned_total", title: "周期内分配(个)", render: (value) => formatNumber(value) },
            { key: "capacity_total", title: "周期内最大产能(个)", render: (value) => formatNumber(value) },
            { key: "utilization", title: "利用率", render: (value) => formatPercent(value) },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => (
                <button className="btn-danger-text" onClick={() => removeLine(row.id)}>
                  删除
                </button>
              )
            }
          ]}
          rows={lineRows}
        />
      </div>
      ) : null}

      {activeTab === "capacity" ? (
      <div className="panel">
        <h3>日产能调整</h3>
        <div className="toolbar">
          <label>
            产线
            <select
              value={capacityForm.lineId}
              onChange={(e) => {
                const nextLineId = e.target.value;
                const line = scenario.lines.find((row) => row.id === nextLineId);
                setCapacityForm((prev) => ({
                  ...prev,
                  lineId: nextLineId,
                  capacity: String(line?.baseCapacity ?? 0)
                }));
              }}
            >
              {scenario.lines.map((line) => (
                <option key={line.id} value={line.id}>
                  {line.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            产能(个/天)
            <input
              type="number"
              min="0"
              step="0.1"
              value={capacityForm.capacity}
              onChange={(e) => setCapacityForm((prev) => ({ ...prev, capacity: e.target.value }))}
            />
          </label>
          <button className="btn-success-text" onClick={saveDailyCapacity}>
            保存
          </button>
        </div>

        <SimpleTable
          columns={[
            { key: "line_name", title: "产线" },
            { key: "daily_capacity", title: "日产能(个/天)", render: (value) => formatNumber(value) }
          ]}
          rows={lineDailyCapacityRows}
        />
      </div>
      ) : null}

      {activeTab === "orders" ? (
      <div className="panel">
        <h3>订单录入</h3>
        <div className="toolbar">
          <button data-testid="open-order-modal" onClick={openOrderModal}>
            新增订单
          </button>
          <span className="hint">弹框里每条产线填写的是该订单在该产线的总工作量，不是日工作量。</span>
        </div>

        <SimpleTable
          columns={[
            { key: "order_no", title: "订单号" },
            { key: "workload_qty", title: "总工作量(个)", render: (value) => formatNumber(value) },
            { key: "completed_qty", title: "已完工(个)", render: (value) => formatNumber(value) },
            { key: "remaining_qty", title: "未排量(个)", render: (value) => formatNumber(value) },
            { key: "remaining_plan_days", title: "约需天数", render: (value) => formatNumber(value) },
            { key: "completion_date", title: "预计完成", render: (value) => String(value || "-") },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => (
                <div className="row-actions">
                  <button onClick={() => openInsertModal(row.id)}>插入</button>
                  <button onClick={() => openEditOrderModal(row.id)}>编辑</button>
                  <button className="btn-danger-text" onClick={() => removeOrder(row.id)}>
                    删除
                  </button>
                </div>
              )
            }
          ]}
          rows={orderRows}
        />
      </div>
      ) : null}

      {activeTab === "schedule" ? (
      <div className="panel">
        <h3>每日工作安排（按产线）</h3>
        <p className="hint">瓶颈产线：{calendarPlan.summary.bottleneckLineName || "-"}。切换月份仅用于查看，不会改排产起点。</p>

        <div className="toolbar">
          <button type="button" onClick={() => moveCalendarMonth(-1)} data-testid="calendar-prev-month-btn">
            上个月
          </button>
          <label>
            选择月份
            <input
              type="month"
              data-testid="calendar-month-input"
              value={calendarMonth}
              onChange={(e) => selectCalendarMonth(e.target.value)}
            />
          </label>
          <button type="button" onClick={() => moveCalendarMonth(1)} data-testid="calendar-next-month-btn">
            下个月
          </button>
        </div>

        <div className="lite-calendar-wrap">
          <div className="lite-calendar-head">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label} className="lite-calendar-weekday">
                {label}
              </div>
            ))}
          </div>
          <div className="lite-calendar-grid">
            {calendarWeeks.flatMap((week, weekIdx) =>
              week.map((date, dayIdx) => {
                if (!date) {
                  return <article key={`${weekIdx}-${dayIdx}`} className="lite-calendar-cell lite-calendar-cell-empty" />;
                }
                const inRange = scheduleDateSet.has(date);
                const totalAssigned = calendarPlan.lineRows.reduce((sum, line) => sum + (line.daily[date]?.assigned || 0), 0);
                const totalCapacity = calendarPlan.lineRows.reduce((sum, line) => sum + (line.daily[date]?.capacity || 0), 0);
                const lineAssignments = inRange
                  ? calendarPlan.lineRows
                      .map((line) => {
                        const items = line.daily[date]?.items || [];
                        if (items.length === 0) {
                          return null;
                        }
                        return {
                          lineId: line.lineId,
                          lineName: line.lineName,
                          orders: items.map((item) => {
                            const label = orderNoMap[item.orderId] || item.orderId;
                            return `${label}(${formatNumber(item.workloadDays)})`;
                          })
                        };
                      })
                      .filter(Boolean)
                  : [];
                return (
                  <article
                    key={`${weekIdx}-${date}`}
                    data-testid={`calendar-day-${date}`}
                    className={`lite-calendar-cell ${inRange ? "" : "lite-calendar-cell-out"}`}
                  >
                    <div className="lite-calendar-date">{date.slice(8)}</div>
                    <div className="lite-cal-summary">
                      <span>总排产/产能</span>
                      <strong>
                        {formatNumber(totalAssigned)} / {formatNumber(totalCapacity)}
                      </strong>
                    </div>
                    <div className="lite-cal-line-list">
                      {lineAssignments.length === 0 ? (
                        <span className="hint">无分配</span>
                      ) : (
                        lineAssignments.map((entry) => (
                          <div key={`${date}-${entry.lineId}`} className="lite-cal-line-item">
                            <div className="lite-cal-line-name">{entry.lineName}</div>
                            <div className="lite-cal-line-orders">{entry.orders.join("、")}</div>
                          </div>
                        ))
                      )}
                    </div>
                  </article>
                );
              })
            )}
          </div>
        </div>
      </div>
      ) : null}

      {showInsertModal ? (
        <div className="lite-modal-backdrop" onClick={closeInsertModal}>
          <div className="lite-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="panel-head">
              <h3>插入订单</h3>
              <button onClick={closeInsertModal}>关闭</button>
            </div>
            <div className="toolbar">
              <label>
                订单
                <select
                  value={insertForm.orderId}
                  onChange={(e) => setInsertForm((prev) => ({ ...prev, orderId: e.target.value }))}
                >
                  <option value="">请选择</option>
                  {scenario.orders.map((order) => (
                    <option key={order.id} value={order.id}>
                      {order.orderNo}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                插入日期
                <input
                  type="date"
                  value={insertForm.date}
                  onChange={(e) => setInsertForm((prev) => ({ ...prev, date: e.target.value }))}
                />
              </label>
            </div>
            <p className="hint">系统会从所选日期开始优先执行该订单，其他订单自动顺延。</p>
            <div className="row-actions">
              <button onClick={closeInsertModal}>取消</button>
              <button className="btn-success-text" onClick={submitInsertOrder}>
                插入并重排
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {showOrderModal ? (
        <div className="lite-modal-backdrop" onClick={closeOrderModal}>
          <div
            className="lite-modal"
            role="dialog"
            aria-modal="true"
            data-testid="order-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="panel-head">
              <h3>{editingOrderId ? "编辑订单" : "新增订单"}</h3>
              <button onClick={closeOrderModal}>关闭</button>
            </div>

            <div className="toolbar">
              <label>
                订单号
                <input
                  data-testid="order-no-input"
                  placeholder="可选"
                  value={orderModalForm.orderNo}
                  onChange={(e) => setOrderModalForm((prev) => ({ ...prev, orderNo: e.target.value }))}
                />
              </label>
            </div>

            <p className="hint">系统默认同优先级，按订单顺序向后排产。</p>

            <h4 className="lite-sub-title">产线工作量分配（订单总量，非日量）</h4>
            <div className="lite-allocation-list">
              {scenario.lines.map((line) => (
                <div className="lite-allocation-row" key={line.id}>
                  <span className="lite-line-name">{line.name}</span>
                  <input
                    data-testid={`order-line-days-${line.id}`}
                    aria-label={`${line.name} 工作量`}
                    type="number"
                    min="0"
                    step="0.1"
                  value={orderModalForm.lineTotals?.[line.id] || "0"}
                  onChange={(e) => updateOrderLineTotal(line.id, e.target.value)}
                />
                  <span className="hint">总量</span>
                </div>
              ))}
            </div>

            <div className="toolbar">
              <span className="hint">订单总工作量：{formatNumber(modalTotalWorkload)} 个</span>
            </div>

            <div className="row-actions">
              <button onClick={closeOrderModal}>取消</button>
              <button
                className={editingOrderId ? "" : "btn-success-text"}
                data-testid="submit-order-modal"
                onClick={submitOrderFromModal}
              >
                {editingOrderId ? "保存修改" : "创建订单"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
