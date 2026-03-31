import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  listMaterialChildrenByParentCode,
  listOrderPoolMaterials,
} from "../../order-execution";
import { fetchOrdersPoolSnapshot } from "../../order-execution/ordersPoolService";
import { listOrderMaterialAvailability } from "../../masterdata";
import {
  buildFinishedSummary,
  buildMaterialTreeRows,
  buildSelectedOrderReportings,
  collapseExpandedMaterialNodeKeys,
  createOrderCommandAndRefresh,
  deleteOrderAndRefresh,
  filterOrdersPoolRows,
  findSelectedOrderByOrderNoFilter,
  hasOwn,
  isInProgressStatus,
  isOrderCompleted,
  markOrderCompletedAndRefresh,
  normalizeMaterialCode,
  normalizeProcessContexts,
  saveExpectedStartDateAndRefresh,
} from "..";

const ORDERS_POOL_FILTERS_STORAGE_KEY = "orders-pool-filters";
const ORDER_STATUS_FILTER_SET = new Set(["OPEN", "IN_PROGRESS", "DONE", "DELAY"]);

function normalizeOrderStatusFilter(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return ORDER_STATUS_FILTER_SET.has(normalized) ? normalized : "";
}

function readStoredOrdersPoolFilters() {
  if (typeof window === "undefined" || !window.localStorage) {
    return {};
  }
  try {
    const raw = window.localStorage.getItem(ORDERS_POOL_FILTERS_STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (_error) {
    return {};
  }
}

function writeStoredOrdersPoolFilters(filters) {
  if (typeof window === "undefined" || !window.localStorage) {
    return;
  }
  try {
    window.localStorage.setItem(
      ORDERS_POOL_FILTERS_STORAGE_KEY,
      JSON.stringify(filters),
    );
  } catch (_error) {
    // ignore storage write failures
  }
}

export function useOrdersPoolPageController() {
  const storedFilters = readStoredOrdersPoolFilters();
  const [allRows, setAllRows] = useState([]);
  const [scheduledOrderNos, setScheduledOrderNos] = useState([]);
  const [reportings, setReportings] = useState([]);
  const [selectedOrderMaterials, setSelectedOrderMaterials] = useState([]);
  const [materialsLoading, setMaterialsLoading] = useState(false);
  const [materialsRefreshing, setMaterialsRefreshing] = useState(false);
  const [selfMadeRefreshing, setSelfMadeRefreshing] = useState(false);
  const [inventoryRefreshing, setInventoryRefreshing] = useState(false);
  const [materialsError, setMaterialsError] = useState("");
  const [materialsRefreshWarning, setMaterialsRefreshWarning] = useState("");
  const [expandedMaterialNodeKeys, setExpandedMaterialNodeKeys] = useState([]);
  const [materialChildrenByParentCode, setMaterialChildrenByParentCode] = useState({});
  const [materialChildrenLoadingByParentCode, setMaterialChildrenLoadingByParentCode] = useState({});
  const [materialChildrenErrorByParentCode, setMaterialChildrenErrorByParentCode] = useState({});
  const [productKeyword, setProductKeyword] = useState(
    typeof storedFilters.productKeyword === "string"
      ? storedFilters.productKeyword
      : "",
  );
  const [orderStatusFilter, setOrderStatusFilter] = useState(
    normalizeOrderStatusFilter(storedFilters.orderStatusFilter),
  );
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [expectedStartDraft, setExpectedStartDraft] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();
  const autoLoadedMaterialsOrderNoRef = useRef("");

  const orderNoFilter = (searchParams.get("order_no") || "").trim();
  const [orderNoDraft, setOrderNoDraft] = useState(orderNoFilter);

  async function refreshSnapshot() {
    const snapshot = await fetchOrdersPoolSnapshot();
    setAllRows(snapshot.allRows);
    setReportings(snapshot.reportings);
    setScheduledOrderNos(snapshot.scheduledOrderNos);
  }

  async function refresh() {
    setError("");
    try {
      await refreshSnapshot();
    } catch (e) {
      setError(e?.message || "刷新失败。");
    }
  }

  async function createCommand(orderNo, commandType) {
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const noticeMessage = await createOrderCommandAndRefresh(orderNo, commandType, refreshSnapshot);
      setNotice(noticeMessage);
    } catch (e) {
      setError(e?.message || "操作失败。");
    } finally {
      setSubmitting(false);
    }
  }

  async function markCompleted(orderNo, row) {
    const confirmed = window.confirm(`确认将生产订单 ${orderNo} 标记为已完成吗？`);
    if (!confirmed) {
      return;
    }
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const noticeMessage = await markOrderCompletedAndRefresh(orderNo, row, refreshSnapshot);
      setNotice(noticeMessage);
    } catch (e) {
      setError(e?.message || "标记已完成失败。");
    } finally {
      setSubmitting(false);
    }
  }

  async function deleteOrder(orderNo) {
    const confirmed = window.confirm(`确认删除生产订单 ${orderNo} 吗？删除后将无法恢复。`);
    if (!confirmed) {
      return;
    }
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const noticeMessage = await deleteOrderAndRefresh(orderNo, refreshSnapshot);
      setNotice(noticeMessage);
      if (orderNoFilter && orderNoFilter === orderNo) {
        clearOrderNoFilter();
      }
    } catch (e) {
      setError(e?.message || "删除订单失败。");
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  useEffect(() => {
    writeStoredOrdersPoolFilters({
      productKeyword,
      orderStatusFilter,
    });
  }, [productKeyword, orderStatusFilter]);

  useEffect(() => {
    setOrderNoDraft(orderNoFilter);
  }, [orderNoFilter]);

  const scheduledSet = useMemo(
    () => new Set(scheduledOrderNos),
    [scheduledOrderNos],
  );

  const filteredRows = useMemo(() => {
    return filterOrdersPoolRows(allRows, orderStatusFilter, orderNoFilter, productKeyword);
  }, [allRows, orderStatusFilter, orderNoFilter, productKeyword]);

  const selectedOrder = useMemo(() => {
    return findSelectedOrderByOrderNoFilter(allRows, orderNoFilter);
  }, [allRows, orderNoFilter]);

  const selectedOrderNo = useMemo(
    () => String(selectedOrder?.order_no || "").trim(),
    [selectedOrder],
  );

  const selectedOrderReportings = useMemo(() => {
    return buildSelectedOrderReportings(reportings, selectedOrderNo);
  }, [reportings, selectedOrderNo]);

  const finishedSummary = useMemo(() => {
    return buildFinishedSummary(selectedOrder, selectedOrderReportings);
  }, [selectedOrder, selectedOrderReportings]);

  const selectedOrderIsScheduled = selectedOrder
    ? scheduledSet.has(String(selectedOrder.order_no || ""))
    : false;

  const selectedOrderInProgress = selectedOrder
    ? !isOrderCompleted(selectedOrder) && isInProgressStatus(selectedOrder.status)
    : false;

  const selectedOrderProcessContexts = useMemo(
    () => normalizeProcessContexts(selectedOrder),
    [selectedOrder],
  );

  useEffect(() => {
    if (!selectedOrder) {
      setExpectedStartDraft("");
      return;
    }
    const expectedStart = String(
      selectedOrder.expected_start_date ||
        selectedOrder.expected_start_time ||
        "",
    ).trim();
    setExpectedStartDraft(expectedStart ? expectedStart.slice(0, 10) : "");
  }, [selectedOrder]);

  const expandedMaterialNodeKeySet = useMemo(
    () => new Set(expandedMaterialNodeKeys),
    [expandedMaterialNodeKeys],
  );

  const materialTreeRows = useMemo(
    () =>
      buildMaterialTreeRows(
        selectedOrderMaterials,
        materialChildrenByParentCode,
        expandedMaterialNodeKeySet,
        materialChildrenLoadingByParentCode,
        materialChildrenErrorByParentCode,
      ),
    [
      selectedOrderMaterials,
      materialChildrenByParentCode,
      expandedMaterialNodeKeySet,
      materialChildrenLoadingByParentCode,
      materialChildrenErrorByParentCode,
    ],
  );

  async function loadMaterialChildrenByParentCode(parentCode, options = {}) {
    const normalizedParentCode = normalizeMaterialCode(parentCode);
    if (!normalizedParentCode) {
      return [];
    }
    const refreshFromErp = options.refreshFromErp === true;
    if (!refreshFromErp && hasOwn(materialChildrenByParentCode, normalizedParentCode)) {
      return materialChildrenByParentCode[normalizedParentCode] ?? [];
    }
    setMaterialChildrenLoadingByParentCode((prev) => ({
      ...prev,
      [normalizedParentCode]: true,
    }));
    setMaterialChildrenErrorByParentCode((prev) => ({
      ...prev,
      [normalizedParentCode]: "",
    }));
    try {
      const res = await listMaterialChildrenByParentCode(normalizedParentCode, refreshFromErp);
      const items = res.items ?? [];
      setMaterialChildrenByParentCode((prev) => ({
        ...prev,
        [normalizedParentCode]: items,
      }));
      return items;
    } catch (e) {
      const message =
        e?.message ||
        (refreshFromErp ? "刷新子项物料编码失败。" : "加载子项物料编码失败。");
      setMaterialChildrenErrorByParentCode((prev) => ({
        ...prev,
        [normalizedParentCode]: message,
      }));
      throw e;
    } finally {
      setMaterialChildrenLoadingByParentCode((prev) => ({
        ...prev,
        [normalizedParentCode]: false,
      }));
    }
  }

  function collapseMaterialNode(nodeKey) {
    if (!nodeKey) {
      return;
    }
    setExpandedMaterialNodeKeys((prev) => collapseExpandedMaterialNodeKeys(prev, nodeKey));
  }

  async function toggleMaterialNode(row) {
    const nodeKey = row?._treeNodeKey;
    const parentCode = normalizeMaterialCode(row?._treeMaterialCode);
    if (!row?._treeExpandable || !nodeKey || !parentCode) {
      return;
    }
    if (row._treeExpanded) {
      collapseMaterialNode(nodeKey);
      return;
    }
    setExpandedMaterialNodeKeys((prev) => (prev.includes(nodeKey) ? prev : [...prev, nodeKey]));
    if (hasOwn(materialChildrenByParentCode, parentCode) || materialChildrenLoadingByParentCode[parentCode]) {
      return;
    }
    try {
      await loadMaterialChildrenByParentCode(parentCode);
    } catch (_e) {
      // Error state is already stored per node for retry feedback.
    }
  }

  useEffect(() => {
    let cancelled = false;
    if (!orderNoFilter) {
      autoLoadedMaterialsOrderNoRef.current = "";
    }
    if (!selectedOrderNo) {
      setSelectedOrderMaterials([]);
      setMaterialsError("");
      setMaterialsLoading(false);
      setMaterialsRefreshWarning("");
      setExpandedMaterialNodeKeys([]);
      setMaterialChildrenByParentCode({});
      setMaterialChildrenLoadingByParentCode({});
      setMaterialChildrenErrorByParentCode({});
      return () => {
        cancelled = true;
      };
    }
    if (autoLoadedMaterialsOrderNoRef.current === selectedOrderNo) {
      return () => {
        cancelled = true;
      };
    }
    setMaterialsLoading(true);
    setMaterialsError("");
    setMaterialsRefreshWarning("");
    setExpandedMaterialNodeKeys([]);
    setMaterialChildrenByParentCode({});
    setMaterialChildrenLoadingByParentCode({});
    setMaterialChildrenErrorByParentCode({});
    listOrderPoolMaterials(selectedOrderNo)
      .then((res) => {
        if (cancelled) {
          return;
        }
        setSelectedOrderMaterials(res.items ?? []);
        autoLoadedMaterialsOrderNoRef.current = selectedOrderNo;
      })
      .catch((e) => {
        if (cancelled) {
          return;
        }
        setSelectedOrderMaterials([]);
        autoLoadedMaterialsOrderNoRef.current = selectedOrderNo;
        setMaterialsError(e?.message || "加载子物料编码失败。");
      })
      .finally(() => {
        if (!cancelled) {
          setMaterialsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [orderNoFilter, selectedOrderNo]);

  async function refreshMaterialsFromErp() {
    if (!selectedOrderNo) {
      return;
    }
    setMaterialsRefreshing(true);
    setMaterialsLoading(true);
    setMaterialsError("");
    setMaterialsRefreshWarning("");
    setMaterialChildrenErrorByParentCode({});
    try {
      const res = await listOrderPoolMaterials(selectedOrderNo, true);
      setSelectedOrderMaterials(res.items ?? []);
    } catch (e) {
      setSelectedOrderMaterials([]);
      setMaterialsError(e?.message || "刷新子物料编码失败。");
    } finally {
      setMaterialsLoading(false);
      setMaterialsRefreshing(false);
    }
  }

  function collectSelfMadeMaterialCodes() {
    const codes = new Set();
    const pushRows = (rows) => {
      for (const row of rows ?? []) {
        if (String(row?.child_material_supply_type || "").toUpperCase() !== "SELF_MADE") {
          continue;
        }
        const code = normalizeMaterialCode(row?.child_material_code);
        if (code) {
          codes.add(code);
        }
      }
    };
    pushRows(selectedOrderMaterials);
    Object.values(materialChildrenByParentCode).forEach((rows) => pushRows(rows));
    return Array.from(codes);
  }

  async function refreshSelfMadeMaterialsFromErp() {
    if (!selectedOrderNo) {
      return;
    }
    const parentCodes = collectSelfMadeMaterialCodes();
    if (parentCodes.length === 0) {
      setMaterialsRefreshWarning("当前订单没有可刷新的自制子物料。");
      return;
    }
    setSelfMadeRefreshing(true);
    setMaterialsError("");
    setMaterialsRefreshWarning("");
    try {
      const refreshedEntries = await Promise.all(
        parentCodes.map(async (parentCode) => {
          const res = await listMaterialChildrenByParentCode(parentCode, true);
          return [parentCode, res.items ?? []];
        }),
      );
      setMaterialChildrenByParentCode((prev) => {
        const next = { ...prev };
        for (const [parentCode, rows] of refreshedEntries) {
          next[parentCode] = rows;
        }
        return next;
      });
    } catch (e) {
      setMaterialsError(e?.message || "刷新自制子物料失败。");
    } finally {
      setSelfMadeRefreshing(false);
    }
  }

  async function refreshInventoryFromErp() {
    if (!selectedOrderNo) {
      return;
    }
    setInventoryRefreshing(true);
    setMaterialsError("");
    setMaterialsRefreshWarning("");
    try {
      await listOrderMaterialAvailability(true);
    } catch (e) {
      setMaterialsError(e?.message || "刷新库存失败。");
    } finally {
      setInventoryRefreshing(false);
    }
  }

  async function saveExpectedStartTime() {
    if (!selectedOrder?.order_no) {
      return;
    }
    if (!expectedStartDraft) {
      setError("请先选择预计开始日期。");
      return;
    }
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const noticeMessage = await saveExpectedStartDateAndRefresh(
        selectedOrder.order_no,
        expectedStartDraft,
        refreshSnapshot,
      );
      setNotice(noticeMessage);
    } catch (e) {
      setError(e?.message || "保存预计开始时间失败。");
    } finally {
      setSubmitting(false);
    }
  }

  function clearOrderNoFilter() {
    const next = new URLSearchParams(searchParams);
    next.delete("order_no");
    setSearchParams(next);
  }

  function applyOrderNoFilter() {
    const next = new URLSearchParams(searchParams);
    const value = String(orderNoDraft || "").trim();
    if (value) {
      next.set("order_no", value);
    } else {
      next.delete("order_no");
    }
    setSearchParams(next);
  }

  return {
    allRows,
    filteredRows,
    orderNoFilter,
    orderNoDraft,
    setOrderNoDraft,
    applyOrderNoFilter,
    productKeyword,
    setProductKeyword,
    orderStatusFilter,
    setOrderStatusFilter,
    error,
    notice,
    submitting,
    refresh,
    createCommand,
    deleteOrder,
    clearOrderNoFilter,
    markCompleted,
    selectedOrder,
    selectedOrderNo,
    selectedOrderIsScheduled,
    selectedOrderInProgress,
    expectedStartDraft,
    setExpectedStartDraft,
    saveExpectedStartTime,
    finishedSummary,
    selectedOrderReportings,
    selectedOrderProcessContexts,
    materialsLoading,
    materialsRefreshing,
    selfMadeRefreshing,
    inventoryRefreshing,
    materialsError,
    materialsRefreshWarning,
    materialTreeRows,
    toggleMaterialNode,
    refreshMaterialsFromErp,
    refreshSelfMadeMaterialsFromErp,
    refreshInventoryFromErp,
  };
}
