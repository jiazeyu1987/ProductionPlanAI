import { useEffect } from "react";
import { addDays } from "../../utils/liteSchedulerEngine";
import {
  buildModalLinePlanDays,
  buildModalLinePlanQuantities,
  buildModalLineTotals,
} from "./orderModalUtils";

export function useLiteSchedulerFormSync({
  scenario,
  setCapacityForm,
  setLockForm,
  setOrderModalForm,
  setInsertForm,
}) {
  useEffect(() => {
    setCapacityForm((prev) => {
      const safeLine =
        scenario.lines.find((line) => line.id === prev.lineId) ||
        scenario.lines[0];
      const nextLineId = safeLine?.id || "";
      const nextCapacity =
        prev.lineId === nextLineId
          ? prev.capacity
          : String(safeLine?.baseCapacity ?? 0);
      return { ...prev, lineId: nextLineId, capacity: nextCapacity };
    });

    setLockForm((prev) => ({
      ...prev,
      lineId:
        scenario.lines.find((line) => line.id === prev.lineId)?.id ||
        scenario.lines[0]?.id ||
        "",
      orderId:
        scenario.orders.find((order) => order.id === prev.orderId)?.id ||
        scenario.orders[0]?.id ||
        "",
      startDate: prev.startDate || scenario.horizonStart,
      endDate: prev.endDate || addDays(scenario.horizonStart, 2),
    }));

    setOrderModalForm((prev) => ({
      ...prev,
      releaseDate: prev.releaseDate || scenario.horizonStart,
      dueDate: prev.dueDate || addDays(scenario.horizonStart, 7),
      lineTotals: buildModalLineTotals(scenario, prev.lineTotals),
      linePlanDays: buildModalLinePlanDays(scenario, prev.linePlanDays),
      linePlanQuantities: buildModalLinePlanQuantities(
        scenario,
        prev.linePlanQuantities,
      ),
    }));

    setInsertForm((prev) => ({
      orderId:
        scenario.orders.find((order) => order.id === prev.orderId)?.id ||
        prev.orderId ||
        "",
      date: prev.date || scenario.horizonStart,
    }));
  }, [
    scenario.horizonStart,
    scenario.lines,
    scenario.orders,
    setCapacityForm,
    setLockForm,
    setOrderModalForm,
    setInsertForm,
  ]);
}
