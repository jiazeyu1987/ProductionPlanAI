import { useCallback, useEffect, useState } from "react";
import {
  addManualProductionOrder as apiAddManualProductionOrder,
  advanceSimulationOneDay as apiAdvanceSimulationOneDay,
  getSimulationState as apiGetSimulationState,
  listSimulationEvents as apiListSimulationEvents,
  resetManualSimulation as apiResetManualSimulation,
  resetSimulation as apiResetSimulation,
  runSimulation as apiRunSimulation
} from "../../dispatch-alert/apiClient";
import {
  formatClientLocalDate,
  SHOW_BATCH_SIMULATION,
  TAB_BATCH,
  TAB_MANUAL,
  toNumber
} from "../simulationUtils";

export function useSimulationController() {
  const [activeTab, setActiveTab] = useState(SHOW_BATCH_SIMULATION ? TAB_BATCH : TAB_MANUAL);
  const [form, setForm] = useState({
    days: 7,
    daily_sales_order_count: 20,
    scenario: "STABLE",
    seed: 20260322
  });
  const [state, setState] = useState(null);
  const [events, setEvents] = useState([]);
  const [summary, setSummary] = useState(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    const [stateRes, eventsRes] = await Promise.all([apiGetSimulationState(), apiListSimulationEvents()]);
    setState(stateRes);
    setEvents(eventsRes.items ?? []);
    if (stateRes.last_run_summary && Object.keys(stateRes.last_run_summary).length > 0) {
      setSummary(stateRes.last_run_summary);
    }
  }, []);

  const runBatch = useCallback(
    async (daysOverride) => {
      setLoading(true);
      setMessage("");
      setError("");
      try {
        const payload = {
          days: daysOverride ?? toNumber(form.days, 7),
          daily_sales_order_count: toNumber(form.daily_sales_order_count, 20),
          scenario: form.scenario,
          seed: toNumber(form.seed, 20260322)
        };
        const res = await apiRunSimulation(payload);
        setSummary(res);
        setMessage("批量仿真执行完成。");
        await refresh();
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    },
    [form.daily_sales_order_count, form.days, form.scenario, form.seed, refresh]
  );

  const resetBatch = useCallback(async () => {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      await apiResetSimulation({
        seed: toNumber(form.seed, 20260322),
        scenario: form.scenario,
        daily_sales_order_count: toNumber(form.daily_sales_order_count, 20)
      });
      setSummary(null);
      setMessage("批量仿真状态已重置。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [form.daily_sales_order_count, form.scenario, form.seed, refresh]);

  const addManualProductionOrder = useCallback(async () => {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await apiAddManualProductionOrder({});
      setMessage(`已新增生产订单：${res.production_order_no}`);
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  const advanceManualOneDay = useCallback(async () => {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await apiAdvanceSimulationOneDay({
        client_date: formatClientLocalDate()
      });
      setSummary(res);
      setMessage("手动模拟已推进1天，并完成当日排产与报工推进。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  const resetManualSimulation = useCallback(async () => {
    setLoading(true);
    setMessage("");
    setError("");
    try {
      const res = await apiResetManualSimulation({});
      setSummary(
        res.last_run_summary && Object.keys(res.last_run_summary).length > 0 ? res.last_run_summary : null
      );
      setMessage(res.message || "已恢复到手动模拟前的数据状态。");
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  useEffect(() => {
    refresh().catch((e) => setError(e.message));
  }, [refresh]);

  return {
    activeTab,
    setActiveTab,
    form,
    setForm,
    state,
    events,
    summary,
    message,
    error,
    loading,

    refresh,
    runBatch,
    resetBatch,
    addManualProductionOrder,
    advanceManualOneDay,
    resetManualSimulation
  };
}

