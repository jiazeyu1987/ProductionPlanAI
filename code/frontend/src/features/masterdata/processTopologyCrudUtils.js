import { normalizeProcessCode, normalizeProductCode, normalizeShiftCode } from "./codeNormalizeUtils";
import { toInt } from "./dataTransformUtils";
import { buildMaterialRowKey, buildResourceRowKey } from "./dailyConfigRowsUtils";

function mapRowsWithProcessCode(rows, previousCode, nextCode) {
  return (rows || []).map((row) => {
    const processCode = normalizeProcessCode(row?.process_code);
    return processCode === previousCode ? { ...row, process_code: nextCode } : row;
  });
}

function resolveShiftNameCn(shiftCode) {
  if (shiftCode === "DAY") {
    return "DAY";
  }
  if (shiftCode === "NIGHT") {
    return "NIGHT";
  }
  return shiftCode;
}

export function buildProcessConfigUpdateResult(prevConfig, index, field, value) {
  const processConfigs = Array.isArray(prevConfig?.processConfigs) ? prevConfig.processConfigs : [];
  if (index < 0 || index >= processConfigs.length) {
    return prevConfig;
  }

  const nextProcessConfigs = processConfigs.map((row, rowIndex) =>
    rowIndex === index
      ? {
        ...row,
        [field]: field === "process_code" ? normalizeProcessCode(value) : value,
      }
      : row,
  );
  if (field !== "process_code") {
    return {
      ...prevConfig,
      processConfigs: nextProcessConfigs,
    };
  }

  const previousCode = normalizeProcessCode(processConfigs[index]?.process_code);
  const nextCode = normalizeProcessCode(value);
  if (!previousCode || !nextCode || previousCode === nextCode) {
    return {
      ...prevConfig,
      processConfigs: nextProcessConfigs,
    };
  }

  return {
    ...prevConfig,
    processConfigs: nextProcessConfigs,
    lineTopology: mapRowsWithProcessCode(prevConfig?.lineTopology, previousCode, nextCode),
    resourcePool: mapRowsWithProcessCode(prevConfig?.resourcePool, previousCode, nextCode),
    materialAvailability: mapRowsWithProcessCode(prevConfig?.materialAvailability, previousCode, nextCode),
  };
}

export function resolveNextProcessConfigCode(processConfigs, processOptions) {
  const existingCodes = new Set((processConfigs || []).map((row) => normalizeProcessCode(row?.process_code)).filter(Boolean));
  const candidate = (processOptions || []).find((option) => !existingCodes.has(option?.code));
  let nextCode = candidate?.code || "";
  if (!nextCode) {
    let serial = (processConfigs || []).length + 1;
    do {
      nextCode = `PROC_NEW_${serial}`;
      serial += 1;
    } while (existingCodes.has(nextCode));
  }
  return nextCode;
}

export function buildNewProcessConfigRow(processConfigs, processOptions, processNameByCode) {
  const nextCode = resolveNextProcessConfigCode(processConfigs, processOptions);
  return {
    process_code: nextCode,
    process_name_cn: processNameByCode?.get(nextCode) || nextCode,
    capacity_per_shift: 1,
    required_workers: 1,
    required_machines: 1,
  };
}

export function resolveDefaultResourceCapacity(processDefaultParamByCode, processCode) {
  const processConfig = processDefaultParamByCode?.get(processCode);
  return {
    workersAvailable: Math.max(0, toInt(processConfig?.required_workers, 1)),
    machinesAvailable: Math.max(0, toInt(processConfig?.required_machines, 1)),
  };
}

export function buildNewResourcePoolRow({
  date,
  shiftCode,
  processCode,
  processName,
  workersAvailable,
  machinesAvailable,
}) {
  return {
    calendar_date: date,
    shift_code: shiftCode,
    shift_name_cn: resolveShiftNameCn(shiftCode),
    process_code: processCode,
    process_name_cn: processName,
    open_flag: 1,
    workers_available: workersAvailable,
    machines_available: machinesAvailable,
  };
}

export function buildNewMaterialAvailabilityRow({
  date,
  shiftCode,
  productCode,
  productName,
  processCode,
  processName,
}) {
  return {
    calendar_date: date,
    shift_code: shiftCode,
    shift_name_cn: resolveShiftNameCn(shiftCode),
    product_code: productCode,
    product_name_cn: productName,
    process_code: processCode,
    process_name_cn: processName,
    available_qty: 0,
  };
}

export function resolveAddResourceRowDraft({
  selectedConfigDate,
  newResourceShift,
  newResourceProcess,
  resourcePool,
  processNameByCode,
  processDefaultParamByCode,
}) {
  const date = String(selectedConfigDate || "").trim();
  const shiftCode = normalizeShiftCode(newResourceShift);
  const processCode = normalizeProcessCode(newResourceProcess);
  if (!date) {
    return { error: "Please select a calendar date." };
  }
  if (!shiftCode || !processCode) {
    return { error: "Please select shift and process." };
  }
  const targetKey = `${date}#${shiftCode}#${processCode}`;
  if ((resourcePool || []).some((row) => buildResourceRowKey(row) === targetKey)) {
    return { error: "Resource record already exists." };
  }
  const processName = processNameByCode?.get(processCode) || processCode;
  const defaults = resolveDefaultResourceCapacity(processDefaultParamByCode, processCode);
  return {
    error: "",
    row: buildNewResourcePoolRow({
      date,
      shiftCode,
      processCode,
      processName,
      workersAvailable: defaults.workersAvailable,
      machinesAvailable: defaults.machinesAvailable,
    }),
  };
}

export function resolveAddMaterialRowDraft({
  selectedConfigDate,
  newMaterialShift,
  newMaterialProcess,
  newMaterialProduct,
  materialAvailability,
  processNameByCode,
  productOptions,
}) {
  const date = String(selectedConfigDate || "").trim();
  const shiftCode = normalizeShiftCode(newMaterialShift);
  const processCode = normalizeProcessCode(newMaterialProcess);
  const productCode = normalizeProductCode(newMaterialProduct);
  if (!date) {
    return { error: "Please select a calendar date." };
  }
  if (!shiftCode || !processCode || !productCode) {
    return { error: "Please select shift, process and product." };
  }
  const targetKey = `${date}#${shiftCode}#${productCode}#${processCode}`;
  if ((materialAvailability || []).some((row) => buildMaterialRowKey(row) === targetKey)) {
    return { error: "Material record already exists." };
  }
  const processName = processNameByCode?.get(processCode) || processCode;
  const productName = (productOptions || []).find((option) => option?.code === productCode)?.name || productCode;
  return {
    error: "",
    row: buildNewMaterialAvailabilityRow({
      date,
      shiftCode,
      productCode,
      productName,
      processCode,
      processName,
    }),
  };
}
