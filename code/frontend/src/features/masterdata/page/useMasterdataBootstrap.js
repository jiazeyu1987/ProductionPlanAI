import { useCallback, useEffect, useState } from "react";
import { fetchMasterdataBootstrap, fetchOrderMaterialAvailability } from "../service";

export function useMasterdataBootstrap() {
  const [routes, setRoutes] = useState([]);
  const [configRes, setConfigRes] = useState(null);
  const [rulesRes, setRulesRes] = useState(null);
  const [schedules, setSchedules] = useState([]);

  const [orderMaterialRows, setOrderMaterialRows] = useState([]);
  const [orderMaterialLoading, setOrderMaterialLoading] = useState(false);
  const [orderMaterialRefreshing, setOrderMaterialRefreshing] = useState(false);
  const [orderMaterialError, setOrderMaterialError] = useState("");
  const [orderMaterialLoaded, setOrderMaterialLoaded] = useState(false);

  const refreshMasterdata = useCallback(async () => {
    try {
      const snapshot = await fetchMasterdataBootstrap();
      setRoutes(snapshot.routes ?? []);
      setConfigRes(snapshot.configRes ?? null);
      setRulesRes(snapshot.rulesRes ?? null);
      setSchedules(snapshot.schedules ?? []);
    } catch (_error) {
      setRoutes([]);
      setConfigRes(null);
      setRulesRes(null);
      setSchedules([]);
    }
  }, []);

  const refreshOrderMaterialRows = useCallback(async (refreshFromErp = false) => {
    if (refreshFromErp) {
      setOrderMaterialRefreshing(true);
    } else {
      setOrderMaterialLoading(true);
    }
    setOrderMaterialError("");
    try {
      const items = await fetchOrderMaterialAvailability(refreshFromErp);
      setOrderMaterialRows(items ?? []);
      setOrderMaterialLoaded(true);
    } catch (error) {
      setOrderMaterialError(error.message || "Failed to refresh order material data.");
    } finally {
      if (refreshFromErp) {
        setOrderMaterialRefreshing(false);
      } else {
        setOrderMaterialLoading(false);
      }
    }
  }, []);

  const ensureOrderMaterialRowsLoaded = useCallback(async () => {
    if (orderMaterialLoaded || orderMaterialLoading || orderMaterialRefreshing) {
      return;
    }
    await refreshOrderMaterialRows(false);
  }, [
    orderMaterialLoaded,
    orderMaterialLoading,
    orderMaterialRefreshing,
    refreshOrderMaterialRows,
  ]);

  useEffect(() => {
    refreshMasterdata().catch(() => {});
  }, [refreshMasterdata]);

  return {
    routes,
    configRes,
    rulesRes,
    schedules,
    orderMaterialRows,
    orderMaterialLoading,
    orderMaterialRefreshing,
    orderMaterialError,
    refreshMasterdata,
    refreshOrderMaterialRows,
    ensureOrderMaterialRowsLoaded,
  };
}
