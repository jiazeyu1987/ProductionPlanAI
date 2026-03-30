import { normalizeCompanyCode, normalizeProcessCode } from "./codeNormalizeUtils";
import { toInt, toNumber } from "./dataTransformUtils";

function buildValidProcessCodeSet(processOptions) {
  return new Set((processOptions || []).map((option) => normalizeProcessCode(option?.code)).filter(Boolean));
}

export function normalizeLineTopologyRowsForSave(lineTopologyRows, processOptions) {
  const validProcessCodes = buildValidProcessCodeSet(processOptions);
  if (validProcessCodes.size === 0) {
    throw new Error("No available process options. Please maintain process config first.");
  }

  const normalizedLineTopology = [];
  const lineTopologySeen = new Set();
  for (const row of lineTopologyRows || []) {
    const companyCode = normalizeCompanyCode(row?.company_code);
    const workshopCode = String(row?.workshop_code || "").trim().toUpperCase();
    const lineCode = String(row?.line_code || "").trim().toUpperCase();
    const lineName = String(row?.line_name || "").trim();
    const processCode = normalizeProcessCode(row?.process_code);
    if (!workshopCode || !lineCode || !processCode) {
      throw new Error("Workshop/line/process cannot be empty in line topology.");
    }
    if (!validProcessCodes.has(processCode)) {
      throw new Error(`Line topology contains unknown process code: ${processCode}`);
    }

    const topologyKey = `${companyCode}#${workshopCode}#${lineCode}#${processCode}`;
    if (lineTopologySeen.has(topologyKey)) {
      throw new Error(`Line topology has duplicate row: ${workshopCode} / ${lineCode} / ${processCode}`);
    }
    lineTopologySeen.add(topologyKey);

    const capacityPerShift = toNumber(row?.capacity_per_shift, 0);
    const requiredWorkers = toInt(row?.required_workers, 0);
    const requiredMachines = toInt(row?.required_machines, 0);
    if (capacityPerShift <= 0) {
      throw new Error(`Line ${lineCode} / process ${processCode}: capacity_per_shift must be > 0.`);
    }
    if (requiredWorkers <= 0) {
      throw new Error(`Line ${lineCode} / process ${processCode}: required_workers must be > 0.`);
    }
    if (requiredMachines <= 0) {
      throw new Error(`Line ${lineCode} / process ${processCode}: required_machines must be > 0.`);
    }

    normalizedLineTopology.push({
      company_code: companyCode,
      workshop_code: workshopCode,
      line_code: lineCode,
      line_name: lineName,
      process_code: processCode,
      capacity_per_shift: capacityPerShift,
      required_workers: requiredWorkers,
      required_machines: requiredMachines,
      enabled_flag: toInt(row?.enabled_flag, 1) === 1 ? 1 : 0
    });
  }

  if (normalizedLineTopology.length === 0) {
    throw new Error("At least one line topology row is required.");
  }
  return normalizedLineTopology;
}

export function buildMasterdataConfigSavePayload(lineTopologyRows, processOptions) {
  return {
    line_topology: normalizeLineTopologyRowsForSave(lineTopologyRows, processOptions)
  };
}
