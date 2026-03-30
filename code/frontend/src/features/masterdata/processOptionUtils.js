import { normalizeProcessCode } from "./codeNormalizeUtils";
import { toInt, toNumber } from "./dataTransformUtils";

export function buildProcessOptions(routes, lineTopologyRows) {
  const byCode = new Map();
  for (const row of routes || []) {
    const processCode = String(row?.process_code || "").trim().toUpperCase();
    if (!processCode || byCode.has(processCode)) {
      continue;
    }
    byCode.set(processCode, row?.process_name_cn || row?.process_name || processCode);
  }
  if (byCode.size === 0) {
    for (const row of lineTopologyRows || []) {
      const processCode = String(row?.process_code || "").trim().toUpperCase();
      if (!processCode || byCode.has(processCode)) {
        continue;
      }
      byCode.set(processCode, row?.process_name_cn || row?.process_name || processCode);
    }
  }
  return [...byCode.entries()]
    .map(([code, name]) => ({ code, name: String(name || code).trim() || code }))
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-Hans-CN"));
}

export function buildProcessNameByCode(processOptions) {
  const map = new Map();
  for (const option of processOptions || []) {
    map.set(option.code, option.name);
  }
  return map;
}

export function buildProcessDefaultParamByCode(lineTopologyRows) {
  const map = new Map();
  for (const row of lineTopologyRows || []) {
    const processCode = normalizeProcessCode(row?.process_code);
    if (!processCode || map.has(processCode)) {
      continue;
    }
    const capacityPerShift = Math.max(0, toNumber(row?.capacity_per_shift, 0));
    const requiredWorkers = Math.max(0, toInt(row?.required_workers, 0));
    const requiredMachines = Math.max(0, toInt(row?.required_machines, 0));
    map.set(processCode, {
      capacity_per_shift: capacityPerShift,
      required_workers: requiredWorkers,
      required_machines: requiredMachines,
    });
  }
  return map;
}
