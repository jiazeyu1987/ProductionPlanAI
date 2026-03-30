import { normalizeCompanyCode, normalizeProcessCode } from "./codeNormalizeUtils";
import { toInt } from "./dataTransformUtils";

export function sortLineTopologyRows(lineTopologyRows) {
  return [...(lineTopologyRows || [])].sort((a, b) => {
    const byCompany = String(a?.company_code || "").localeCompare(String(b?.company_code || ""), "zh-Hans-CN");
    if (byCompany !== 0) {
      return byCompany;
    }
    const byWorkshop = String(a?.workshop_code || "").localeCompare(String(b?.workshop_code || ""), "zh-Hans-CN");
    if (byWorkshop !== 0) {
      return byWorkshop;
    }
    const byLine = String(a?.line_code || "").localeCompare(String(b?.line_code || ""), "zh-Hans-CN");
    if (byLine !== 0) {
      return byLine;
    }
    return String(a?.process_code || "").localeCompare(String(b?.process_code || ""), "zh-Hans-CN");
  });
}

export function buildLineOptions(lineTopologyRows) {
  const byLine = new Map();
  for (const row of lineTopologyRows || []) {
    const lineCode = String(row?.line_code || "").trim().toUpperCase();
    if (!lineCode) {
      continue;
    }
    const existing = byLine.get(lineCode);
    if (existing) {
      continue;
    }
    byLine.set(lineCode, {
      lineCode,
      lineName: String(row?.line_name || lineCode).trim() || lineCode,
      companyCode: normalizeCompanyCode(row?.company_code),
      workshopCode: String(row?.workshop_code || "").trim().toUpperCase(),
    });
  }
  return [...byLine.values()].sort((a, b) => String(a.lineCode).localeCompare(String(b.lineCode), "zh-Hans-CN"));
}

export function buildTopologyWorkshopGroups(lineTopologyRows, processNameByCode) {
  const workshopMap = new Map();
  for (const row of lineTopologyRows || []) {
    const companyCode = normalizeCompanyCode(row?.company_code);
    const workshopCode = String(row?.workshop_code || "").trim();
    const lineCode = String(row?.line_code || "").trim().toUpperCase();
    const lineName = String(row?.line_name || lineCode).trim() || lineCode;
    const processCode = normalizeProcessCode(row?.process_code);
    if (!workshopCode || !lineCode || !processCode) {
      continue;
    }
    const workshopKey = `${companyCode}#${workshopCode.toUpperCase()}`;
    if (!workshopMap.has(workshopKey)) {
      workshopMap.set(workshopKey, {
        key: workshopKey,
        companyCode,
        workshopCode,
        lines: new Map(),
      });
    }
    const workshop = workshopMap.get(workshopKey);
    const lineKey = `${workshopKey}#${lineCode}`;
    if (!workshop.lines.has(lineKey)) {
      workshop.lines.set(lineKey, {
        key: lineKey,
        companyCode,
        workshopCode,
        lineCode,
        lineName,
        processRowsByCode: new Map(),
      });
    }
    const line = workshop.lines.get(lineKey);
    line.processRowsByCode.set(processCode, {
      company_code: companyCode,
      workshop_code: workshopCode,
      line_code: lineCode,
      line_name: lineName,
      process_code: processCode,
      process_name_cn: processNameByCode?.get(processCode) || processCode,
      capacity_per_shift: row?.capacity_per_shift ?? "",
      required_workers: row?.required_workers ?? "",
      required_machines: row?.required_machines ?? "",
      enabled_flag: toInt(row?.enabled_flag, 1) === 1 ? 1 : 0,
    });
  }

  return [...workshopMap.values()]
    .map((workshop) => ({
      ...workshop,
      lines: [...workshop.lines.values()]
        .map((line) => ({
          ...line,
          processRows: [...line.processRowsByCode.values()].sort((a, b) =>
            String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN"),
          ),
          processCodes: [...line.processRowsByCode.keys()].sort((a, b) => String(a).localeCompare(String(b), "zh-Hans-CN")),
        }))
        .sort((a, b) => String(a.lineCode).localeCompare(String(b.lineCode), "zh-Hans-CN")),
    }))
    .sort((a, b) => String(a.workshopCode).localeCompare(String(b.workshopCode), "zh-Hans-CN"));
}

export function pickSelectedTopologyWorkshop(topologyWorkshopGroups, selectedTopologyWorkshopKey) {
  if (!Array.isArray(topologyWorkshopGroups) || topologyWorkshopGroups.length === 0) {
    return null;
  }
  return topologyWorkshopGroups.find((row) => row.key === selectedTopologyWorkshopKey) || topologyWorkshopGroups[0];
}

export function pickSelectedTopologyLine(selectedTopologyWorkshop, selectedTopologyLineCode) {
  const lines = Array.isArray(selectedTopologyWorkshop?.lines) ? selectedTopologyWorkshop.lines : [];
  if (lines.length === 0) {
    return null;
  }
  return (
    lines.find(
      (line) => String(line.lineCode || "").trim().toUpperCase() === String(selectedTopologyLineCode || "").trim().toUpperCase(),
    ) || lines[0]
  );
}

export function pickSelectedTopologyLineProcessRows(selectedTopologyLine) {
  if (!selectedTopologyLine || !Array.isArray(selectedTopologyLine.processRows)) {
    return [];
  }
  return selectedTopologyLine.processRows;
}

export function resolveTopologyWorkshopKeyUpdate(topologyWorkshopGroups, selectedTopologyWorkshopKey) {
  if (!Array.isArray(topologyWorkshopGroups) || topologyWorkshopGroups.length === 0) {
    return selectedTopologyWorkshopKey ? "" : null;
  }
  const exists = topologyWorkshopGroups.some((group) => group.key === selectedTopologyWorkshopKey);
  return exists ? null : topologyWorkshopGroups[0].key;
}

export function findTopologyWorkshopParamMatch(topologyWorkshopGroups, topologyWorkshopParam, topologyCompanyParam) {
  if (!topologyWorkshopParam || !Array.isArray(topologyWorkshopGroups) || topologyWorkshopGroups.length === 0) {
    return null;
  }
  const workshopCode = String(topologyWorkshopParam || "").trim().toUpperCase();
  const applyKey = `${normalizeCompanyCode(topologyCompanyParam)}#${workshopCode}`;
  const matchedByCompany = topologyWorkshopGroups.find(
    (group) =>
      normalizeCompanyCode(group.companyCode) === normalizeCompanyCode(topologyCompanyParam)
      && String(group.workshopCode || "").trim().toUpperCase() === workshopCode,
  );
  const matched = matchedByCompany || topologyWorkshopGroups.find(
    (group) => String(group.workshopCode || "").trim().toUpperCase() === workshopCode,
  );
  if (!matched) {
    return null;
  }
  return {
    applyKey,
    matchedKey: matched.key,
  };
}

export function resolveTopologyLineCodeUpdate(selectedTopologyWorkshop, selectedTopologyLineCode, topologyLineParam) {
  const lines = Array.isArray(selectedTopologyWorkshop?.lines) ? selectedTopologyWorkshop.lines : [];
  if (lines.length === 0) {
    return selectedTopologyLineCode ? "" : null;
  }
  const exists = lines.some(
    (line) => String(line.lineCode || "").trim().toUpperCase() === String(selectedTopologyLineCode || "").trim().toUpperCase(),
  );
  if (exists) {
    return null;
  }
  const matchedByParam = topologyLineParam
    ? lines.find((line) => String(line.lineCode || "").trim().toUpperCase() === topologyLineParam)
    : null;
  if (matchedByParam) {
    return topologyLineParam;
  }
  return String(lines[0].lineCode || "").trim().toUpperCase();
}

export function resolveSelectedTopologyLineCodeOnLineEdit(selectedTopologyLineCode, line, value) {
  const previousLineCode = String(line?.lineCode || "").trim().toUpperCase();
  const nextLineCode = String(value || "").trim().toUpperCase();
  if (!previousLineCode) {
    return null;
  }
  return previousLineCode === String(selectedTopologyLineCode || "").trim().toUpperCase() ? nextLineCode : null;
}

export function matchesTopologyWorkshop(row, companyCode, workshopCode) {
  return normalizeCompanyCode(row?.company_code) === normalizeCompanyCode(companyCode)
    && String(row?.workshop_code || "").trim().toUpperCase() === String(workshopCode || "").trim().toUpperCase();
}

export function matchesTopologyLine(row, line) {
  return matchesTopologyWorkshop(row, line?.companyCode, line?.workshopCode)
    && String(row?.line_code || "").trim().toUpperCase() === String(line?.lineCode || "").trim().toUpperCase();
}

export function filterOutTopologyWorkshopRows(rows, workshop) {
  return (rows || []).filter((row) => !matchesTopologyWorkshop(row, workshop?.companyCode, workshop?.workshopCode));
}

export function filterOutTopologyLineRows(rows, line) {
  return (rows || []).filter((row) => !matchesTopologyLine(row, line));
}

export function updateTopologyWorkshopRows(rows, workshop, field, value) {
  return (rows || []).map((row) => {
    if (!matchesTopologyWorkshop(row, workshop?.companyCode, workshop?.workshopCode)) {
      return row;
    }
    if (field === "company_code") {
      return { ...row, company_code: normalizeCompanyCode(value) };
    }
    if (field === "workshop_code") {
      return { ...row, workshop_code: String(value || "").trim() };
    }
    return row;
  });
}

export function updateTopologyLineRows(rows, line, field, value) {
  return (rows || []).map((row) => {
    if (!matchesTopologyLine(row, line)) {
      return row;
    }
    if (field === "line_name") {
      return { ...row, line_name: String(value || "").trim() };
    }
    if (field === "line_code") {
      return { ...row, line_code: String(value || "").trim().toUpperCase() };
    }
    return row;
  });
}

export function buildLineTopologyProcessRow(params, options = {}) {
  const normalizedProcessCode = normalizeProcessCode(params?.processCode);
  const requireProcess = options?.requireProcess === true;
  if (requireProcess && !normalizedProcessCode) {
    return null;
  }
  const normalizedLineCode = String(params?.lineCode || "").trim().toUpperCase();
  return {
    company_code: normalizeCompanyCode(params?.companyCode),
    workshop_code: String(params?.workshopCode || "").trim(),
    line_code: normalizedLineCode,
    line_name: String(params?.lineName || normalizedLineCode).trim(),
    process_code: normalizedProcessCode,
    capacity_per_shift: "",
    required_workers: "",
    required_machines: "",
    enabled_flag: 1,
  };
}

export function buildTopologyLineProcessRow(line, processCode) {
  return buildLineTopologyProcessRow({
    companyCode: line?.companyCode,
    workshopCode: line?.workshopCode,
    lineCode: line?.lineCode,
    lineName: line?.lineName || line?.lineCode,
    processCode,
  }, { requireProcess: true });
}

export function filterOutTopologyLineProcessRows(rows, line, processCode) {
  const normalizedProcessCode = normalizeProcessCode(processCode);
  return (rows || []).filter((row) => {
    if (!matchesTopologyLine(row, line)) {
      return true;
    }
    return normalizeProcessCode(row?.process_code) !== normalizedProcessCode;
  });
}

export function buildTopologyLineProcessCodeSet(rows, line) {
  const result = new Set();
  for (const row of rows || []) {
    if (!matchesTopologyLine(row, line)) {
      continue;
    }
    const code = normalizeProcessCode(row?.process_code);
    if (code) {
      result.add(code);
    }
  }
  return result;
}

export function resolveTopologyLineProcessToggleAction(currentCodes, processCode, checked) {
  const normalizedProcessCode = normalizeProcessCode(processCode);
  if (!normalizedProcessCode) {
    return "invalid";
  }
  const hasCurrent = Boolean(currentCodes?.has(normalizedProcessCode));
  if (checked === hasCurrent) {
    return "noop";
  }
  if (!checked && (currentCodes?.size || 0) <= 1) {
    return "block_remove_last";
  }
  return checked ? "add" : "remove";
}

export function filterSectionLeadersByLineTopology(nextLineTopology, prevSectionLeaders) {
  const validLineCodes = new Set(
    (nextLineTopology || []).map((row) => String(row?.line_code || "").trim().toUpperCase()).filter(Boolean),
  );
  return (prevSectionLeaders || []).filter((row) =>
    validLineCodes.has(String(row?.line_code || "").trim().toUpperCase()),
  );
}

export function applyLineTopologyWithSectionLeaderPrune(prevConfig, nextLineTopology) {
  return {
    ...prevConfig,
    lineTopology: nextLineTopology,
    sectionLeaderBindings: filterSectionLeadersByLineTopology(nextLineTopology, prevConfig?.sectionLeaderBindings),
  };
}

export function nextWorkshopCodeFromTopology(lineTopology) {
  const used = new Set((lineTopology || []).map((row) => String(row?.workshop_code || "").trim().toUpperCase()).filter(Boolean));
  let seq = 1;
  while (seq < 10000) {
    const code = `WS-NEW-${String(seq).padStart(2, "0")}`;
    if (!used.has(code)) {
      return code;
    }
    seq += 1;
  }
  return `WS-NEW-${Date.now()}`;
}

export function nextLineCodeFromTopology(lineTopology) {
  const used = new Set((lineTopology || []).map((row) => String(row?.line_code || "").trim().toUpperCase()).filter(Boolean));
  let seq = 1;
  while (seq < 100000) {
    const code = `LINE-NEW-${seq}`;
    if (!used.has(code)) {
      return code;
    }
    seq += 1;
  }
  return `LINE-NEW-${Date.now()}`;
}

export function matchesLineTopologyRow(row, target) {
  return normalizeCompanyCode(row?.company_code) === normalizeCompanyCode(target?.company_code)
    && String(row?.workshop_code || "").trim().toUpperCase() === String(target?.workshop_code || "").trim().toUpperCase()
    && String(row?.line_code || "").trim().toUpperCase() === String(target?.line_code || "").trim().toUpperCase()
    && normalizeProcessCode(row?.process_code) === normalizeProcessCode(target?.process_code);
}

export function filterOutLineTopologyRows(rows, target) {
  return (rows || []).filter((row) => !matchesLineTopologyRow(row, target));
}

export function updateLineTopologyConfigRows(rows, target, field, value) {
  return (rows || []).map((row) => {
    if (!matchesLineTopologyRow(row, target)) {
      return row;
    }
    const nextValue = field === "enabled_flag" ? toInt(value, 1) : value;
    if (field === "company_code") {
      return { ...row, company_code: normalizeCompanyCode(nextValue) };
    }
    if (field === "workshop_code" || field === "line_code" || field === "process_code") {
      return { ...row, [field]: String(nextValue || "").trim().toUpperCase() };
    }
    if (field === "capacity_per_shift" || field === "required_workers" || field === "required_machines") {
      return { ...row, [field]: value };
    }
    return { ...row, [field]: nextValue };
  });
}

export function matchesSectionLeaderBindingRow(row, target) {
  return String(row?.leader_id || "").trim().toUpperCase() === String(target?.leader_id || "").trim().toUpperCase()
    && String(row?.line_code || "").trim().toUpperCase() === String(target?.line_code || "").trim().toUpperCase();
}

export function filterOutSectionLeaderBindingRows(rows, target) {
  return (rows || []).filter((row) => !matchesSectionLeaderBindingRow(row, target));
}
