import {
  normalizeProcessCode,
  normalizeProductCode,
  normalizeShiftCode,
  shiftSortIndex,
} from "./codeNormalizeUtils";

export function buildResourceRowsForDate(resourcePoolRows, selectedDate, visibleShiftCodeSet) {
  const date = selectedDate || "";
  return (resourcePoolRows || [])
    .filter((row) => {
      if (String(row?.calendar_date || "").trim() !== date) {
        return false;
      }
      return visibleShiftCodeSet.has(normalizeShiftCode(row?.shift_code));
    })
    .slice()
    .sort((a, b) => {
      const byShift = shiftSortIndex(a?.shift_code) - shiftSortIndex(b?.shift_code);
      if (byShift !== 0) {
        return byShift;
      }
      return String(a?.process_code || "").localeCompare(String(b?.process_code || ""), "zh-Hans-CN");
    });
}

export function buildMaterialRowsForDate(materialAvailabilityRows, selectedDate, visibleShiftCodeSet) {
  const date = selectedDate || "";
  return (materialAvailabilityRows || [])
    .filter((row) => {
      if (String(row?.calendar_date || "").trim() !== date) {
        return false;
      }
      return visibleShiftCodeSet.has(normalizeShiftCode(row?.shift_code));
    })
    .slice()
    .sort((a, b) => {
      const byShift = shiftSortIndex(a?.shift_code) - shiftSortIndex(b?.shift_code);
      if (byShift !== 0) {
        return byShift;
      }
      const byProduct = String(a?.product_code || "").localeCompare(String(b?.product_code || ""), "zh-Hans-CN");
      if (byProduct !== 0) {
        return byProduct;
      }
      return String(a?.process_code || "").localeCompare(String(b?.process_code || ""), "zh-Hans-CN");
    });
}

export function buildResourceRowKey(row) {
  return [
    String(row?.calendar_date || "").trim(),
    normalizeShiftCode(row?.shift_code),
    normalizeProcessCode(row?.process_code),
  ].join("#");
}

export function buildMaterialRowKey(row) {
  return [
    String(row?.calendar_date || "").trim(),
    normalizeShiftCode(row?.shift_code),
    normalizeProductCode(row?.product_code),
    normalizeProcessCode(row?.process_code),
  ].join("#");
}

export function ensureNightRowsInConfigData(configData, dateText) {
  if (!dateText || !configData) {
    return configData;
  }
  let changed = false;

  const resourceNightKeys = new Set(
    (configData.resourcePool || [])
      .filter((row) => String(row?.calendar_date || "").trim() === dateText && normalizeShiftCode(row?.shift_code) === "NIGHT")
      .map((row) => `${normalizeProcessCode(row?.process_code)}`),
  );
  const nextResourcePool = [...(configData.resourcePool || [])];
  (configData.resourcePool || []).forEach((row) => {
    if (String(row?.calendar_date || "").trim() !== dateText || normalizeShiftCode(row?.shift_code) !== "DAY") {
      return;
    }
    const processCode = normalizeProcessCode(row?.process_code);
    if (!processCode || resourceNightKeys.has(processCode)) {
      return;
    }
    resourceNightKeys.add(processCode);
    changed = true;
    nextResourcePool.push({
      ...row,
      shift_code: "NIGHT",
      shift_name_cn: "NIGHT",
      open_flag: 1,
    });
  });

  const materialNightKeys = new Set(
    (configData.materialAvailability || [])
      .filter((row) => String(row?.calendar_date || "").trim() === dateText && normalizeShiftCode(row?.shift_code) === "NIGHT")
      .map((row) => `${normalizeProductCode(row?.product_code)}#${normalizeProcessCode(row?.process_code)}`),
  );
  const nextMaterialAvailability = [...(configData.materialAvailability || [])];
  (configData.materialAvailability || []).forEach((row) => {
    if (String(row?.calendar_date || "").trim() !== dateText || normalizeShiftCode(row?.shift_code) !== "DAY") {
      return;
    }
    const key = `${normalizeProductCode(row?.product_code)}#${normalizeProcessCode(row?.process_code)}`;
    if (!key || materialNightKeys.has(key)) {
      return;
    }
    materialNightKeys.add(key);
    changed = true;
    nextMaterialAvailability.push({
      ...row,
      shift_code: "NIGHT",
      shift_name_cn: "NIGHT",
    });
  });

  if (!changed) {
    return configData;
  }
  return {
    ...configData,
    resourcePool: nextResourcePool,
    materialAvailability: nextMaterialAvailability,
  };
}
