import { parseIsoDate } from "./calendar";
import { makeId, positiveOr } from "./engine-core";

export function normalizeLine(line, index) {
  const id = String(line?.id || makeId("line"));
  const name = String(line?.name || "").trim() || `产线-${index + 1}`;
  const baseCapacity = positiveOr(line?.baseCapacity, 300);
  const enabled = line?.enabled !== false;
  const capacityOverrides = {};
  Object.entries(line?.capacityOverrides || {}).forEach(
    ([dateKey, capacityValue]) => {
      if (parseIsoDate(dateKey)) {
        capacityOverrides[dateKey] = positiveOr(capacityValue, baseCapacity);
      }
    },
  );
  return { id, name, baseCapacity, capacityOverrides, enabled };
}

