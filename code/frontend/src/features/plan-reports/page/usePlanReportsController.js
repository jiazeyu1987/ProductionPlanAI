import { createElement, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  exportWorkshopMonthlyPlan,
  exportWorkshopWeeklyPlan,
  fetchPlanReportsSnapshot,
  monthlyColumnDefs,
  monthlyGroups,
  sharedFieldPairs,
  weeklyColumnDefs,
} from "../../integration";
import {
  alignWeeklyRows,
  sortByOrderNo,
  TAB_MONTHLY,
  TAB_WEEKLY,
  validateConsistency,
} from "../planReportsUtils";

function buildOrderNoLinkColumn(column) {
  return {
    ...column,
    render: (value) => {
      const orderNo = String(value || "").trim();
      if (!orderNo) {
        return "-";
      }
      return createElement(
        Link,
        {
          className: "table-link",
          to: `/orders/pool?order_no=${encodeURIComponent(orderNo)}`,
        },
        orderNo,
      );
    },
  };
}

export function usePlanReportsController() {
  const [activeTab, setActiveTab] = useState(TAB_MONTHLY);
  const [weeklyRawRows, setWeeklyRawRows] = useState([]);
  const [monthlyRows, setMonthlyRows] = useState([]);
  const [orderProductCodeMap, setOrderProductCodeMap] = useState({});
  const [versionRows, setVersionRows] = useState([]);
  const [selectedVersionNo, setSelectedVersionNo] = useState("");
  const [effectiveVersionNo, setEffectiveVersionNo] = useState("");
  const [effectiveVersionStatusName, setEffectiveVersionStatusName] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const monthlySortedRows = useMemo(
    () => sortByOrderNo(monthlyRows),
    [monthlyRows],
  );
  const weeklySortedRawRows = useMemo(
    () => sortByOrderNo(weeklyRawRows),
    [weeklyRawRows],
  );

  const weeklyRows = useMemo(
    () => alignWeeklyRows(weeklySortedRawRows, monthlySortedRows),
    [weeklySortedRawRows, monthlySortedRows],
  );

  const consistency = useMemo(
    () => validateConsistency(weeklySortedRawRows, monthlySortedRows, sharedFieldPairs),
    [weeklySortedRawRows, monthlySortedRows],
  );

  const monthlyColumns = useMemo(
    () =>
      monthlyColumnDefs.map((column) => {
        if (column.key !== "production_order_no") {
          return column;
        }
        return buildOrderNoLinkColumn(column);
      }),
    [],
  );

  const weeklyColumns = useMemo(
    () =>
      weeklyColumnDefs.map((column) => {
        if (column.key === "production_order_no") {
          return buildOrderNoLinkColumn(column);
        }
        if (column.key === "product_name") {
          return {
            ...column,
            render: (value, row) => {
              const orderNo = String(row.production_order_no || "").trim();
              const productCode = String(orderProductCodeMap[orderNo] || "").trim();
              const productName = String(value || "").trim() || "-";
              if (!productCode) {
                return productName;
              }
              return createElement(
                Link,
                {
                  className: "table-link",
                  to: `/masterdata?product_code=${encodeURIComponent(productCode)}`,
                },
                productName,
              );
            },
          };
        }
        return column;
      }),
    [orderProductCodeMap],
  );

  async function refresh(nextVersionNo = selectedVersionNo) {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const snapshot = await fetchPlanReportsSnapshot(nextVersionNo);
      setVersionRows(snapshot.versions);
      setSelectedVersionNo(snapshot.resolvedVersionNo);
      setEffectiveVersionNo(snapshot.resolvedVersionNo);
      setEffectiveVersionStatusName(snapshot.resolvedVersionStatusName);
      setWeeklyRawRows(snapshot.weeklyRawRows);
      setMonthlyRows(snapshot.monthlyRows);
      setOrderProductCodeMap(snapshot.orderProductCodeMap);
      setMessage(
        `周计划与月计划数据已刷新（排产版本：${snapshot.resolvedVersionNo || "实时状态"}）。`,
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function exportCurrent() {
    setError("");
    try {
      if (activeTab === TAB_WEEKLY) {
        await exportWorkshopWeeklyPlan(selectedVersionNo);
      } else {
        await exportWorkshopMonthlyPlan(selectedVersionNo);
      }
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => {
    refresh("").catch((e) => setError(e.message));
  }, []);

  return {
    activeTab,
    setActiveTab,
    weeklyRawRows,
    weeklyRows,
    monthlyRows,
    monthlySortedRows,
    versionRows,
    selectedVersionNo,
    effectiveVersionNo,
    effectiveVersionStatusName,
    monthlyColumns,
    weeklyColumns,
    monthlyGroups,
    consistency,
    message,
    error,
    loading,
    refresh,
    exportCurrent,
    setError,
    setMessage,
  };
}
