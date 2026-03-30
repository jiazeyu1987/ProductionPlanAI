import { pad2 } from "../calendarDateUtils";
import { normalizeCodeText } from "../calendarModeUtils";

const MIN_WORKSHOP_TAB_COUNT = 4;
const DEFAULT_WORKSHOP_LINE_COUNT = 4;

function normalizeLineTopologyRow(row) {
  const companyCode = String(row?.company_code ?? row?.companyCode ?? "COMPANY-MAIN")
    .trim()
    .toUpperCase() || "COMPANY-MAIN";
  const workshopCode = String(row?.workshop_code ?? row?.workshopCode ?? "")
    .trim()
    .toUpperCase();
  const lineCode = String(row?.line_code ?? row?.lineCode ?? "")
    .trim()
    .toUpperCase();
  const processCode = String(row?.process_code ?? row?.processCode ?? "")
    .trim()
    .toUpperCase();
  const lineName = String(row?.line_name ?? row?.lineName ?? lineCode).trim() || lineCode;
  const enabledRaw = row?.enabled_flag ?? row?.enabledFlag ?? row?.enabled ?? 1;
  const enabledText = String(enabledRaw).trim().toUpperCase();
  const enabled = !(enabledText === "0" || enabledText === "FALSE" || enabledText === "NO");
  if (!enabled || !workshopCode || !lineCode || !processCode) {
    return null;
  }
  return {
    companyCode,
    workshopCode,
    lineCode,
    lineName,
    processCode
  };
}

function buildFallbackTopologyRows(processCodes) {
  const processList = Array.isArray(processCodes) && processCodes.length > 0 ? processCodes : ["PROC-DEFAULT"];
  const rows = [];
  for (let workshopIndex = 1; workshopIndex <= 4; workshopIndex += 1) {
    const workshopCode = `WS-${pad2(workshopIndex)}`;
    for (let lineIndex = 1; lineIndex <= 4; lineIndex += 1) {
      const lineCode = `${workshopCode}-L${lineIndex}`;
      const lineName = `产线${lineIndex}`;
      for (const processCode of processList) {
        rows.push({
          companyCode: "COMPANY-MAIN",
          workshopCode,
          lineCode,
          lineName,
          processCode
        });
      }
    }
  }
  return rows;
}

function sortUnique(values) {
  return [...new Set(values.filter(Boolean))].sort((a, b) => String(a).localeCompare(String(b), "zh-Hans-CN"));
}

function buildSupplementWorkshop(workshopCode) {
  const workshopKey = `COMPANY-MAIN#${workshopCode}`;
  const lines = [];
  for (let lineIndex = 1; lineIndex <= DEFAULT_WORKSHOP_LINE_COUNT; lineIndex += 1) {
    const lineCode = `${workshopCode}-L${lineIndex}`;
    lines.push({
      key: `${workshopKey}#${lineCode}`,
      companyCode: "COMPANY-MAIN",
      lineCode,
      lineName: `产线${lineIndex}`,
      workshopCode,
      workshopName: workshopCode
    });
  }
  return {
    key: workshopKey,
    workshopCode,
    workshopName: workshopCode,
    lines
  };
}

function ensureMinimumWorkshopTabs(workshops) {
  const base = Array.isArray(workshops) ? workshops.slice() : [];
  if (base.length >= MIN_WORKSHOP_TAB_COUNT) {
    return base;
  }
  const usedCodes = new Set(base.map((row) => String(row?.workshopCode || "").trim().toUpperCase()).filter(Boolean));
  let cursor = 1;
  while (base.length < MIN_WORKSHOP_TAB_COUNT) {
    const code = `WS-DEFAULT-${pad2(cursor)}`;
    cursor += 1;
    if (usedCodes.has(code)) {
      continue;
    }
    usedCodes.add(code);
    base.push(buildSupplementWorkshop(code));
  }
  return base;
}

export function normalizeLineTopologyRowsForSave(rows) {
  const list = Array.isArray(rows) ? rows : [];
  const out = new Map();
  for (const row of list) {
    const companyCode = normalizeCodeText(row?.company_code ?? row?.companyCode ?? "COMPANY-MAIN") || "COMPANY-MAIN";
    const workshopCodeRaw = String(row?.workshop_code ?? row?.workshopCode ?? "").trim();
    const lineCodeRaw = String(row?.line_code ?? row?.lineCode ?? "").trim();
    const processCodeRaw = normalizeCodeText(row?.process_code ?? row?.processCode);
    if (!workshopCodeRaw || !lineCodeRaw || !processCodeRaw) {
      continue;
    }
    const lineName = String(row?.line_name ?? row?.lineName ?? lineCodeRaw).trim() || lineCodeRaw;
    const enabledRaw = row?.enabled_flag ?? row?.enabledFlag ?? row?.enabled ?? 1;
    const enabledText = String(enabledRaw || "").trim().toUpperCase();
    const enabledFlag = enabledText === "0" || enabledText === "FALSE" || enabledText === "NO" ? 0 : 1;
    const key =
      `${companyCode}#${normalizeCodeText(workshopCodeRaw)}#${normalizeCodeText(lineCodeRaw)}#${processCodeRaw}`;
    out.set(key, {
      company_code: companyCode,
      workshop_code: workshopCodeRaw,
      line_code: lineCodeRaw,
      line_name: lineName,
      process_code: processCodeRaw,
      enabled_flag: enabledFlag
    });
  }
  return [...out.values()].sort((a, b) => {
    const byWorkshop = normalizeCodeText(a.workshop_code).localeCompare(normalizeCodeText(b.workshop_code), "zh-Hans-CN");
    if (byWorkshop !== 0) {
      return byWorkshop;
    }
    const byLine = normalizeCodeText(a.line_code).localeCompare(normalizeCodeText(b.line_code), "zh-Hans-CN");
    if (byLine !== 0) {
      return byLine;
    }
    return normalizeCodeText(a.process_code).localeCompare(normalizeCodeText(b.process_code), "zh-Hans-CN");
  });
}

export function buildTopology(rawRows, taskRows) {
  const taskProcessCodes = sortUnique(
    (Array.isArray(taskRows) ? taskRows : []).map((row) => String(row.processCode || "").trim().toUpperCase())
  );
  const normalizedRows = (Array.isArray(rawRows) ? rawRows : [])
    .map(normalizeLineTopologyRow)
    .filter(Boolean);
  const rows = normalizedRows.length > 0 ? normalizedRows : buildFallbackTopologyRows(taskProcessCodes);
  const isFallback = normalizedRows.length === 0;

  const workshopMap = new Map();
  const lineMap = new Map();
  const processLineMap = new Map();

  for (const row of rows) {
    const workshopKey = `${row.companyCode}#${row.workshopCode}`;
    if (!workshopMap.has(workshopKey)) {
      workshopMap.set(workshopKey, {
        key: workshopKey,
        workshopCode: row.workshopCode,
        workshopName: row.workshopCode,
        lines: []
      });
    }
    const workshop = workshopMap.get(workshopKey);
    const lineKey = `${workshopKey}#${row.lineCode}`;
    if (!lineMap.has(lineKey)) {
      const line = {
        key: lineKey,
        companyCode: row.companyCode,
        lineCode: row.lineCode,
        lineName: row.lineName || row.lineCode,
        workshopCode: row.workshopCode,
        workshopName: row.workshopCode
      };
      lineMap.set(lineKey, line);
      workshop.lines.push(line);
    }
    const processCode = row.processCode;
    const set = processLineMap.get(processCode) || new Set();
    set.add(lineKey);
    processLineMap.set(processCode, set);
  }

  const configuredWorkshops = [...workshopMap.values()]
    .map((workshop) => ({
      ...workshop,
      lines: workshop.lines
        .slice()
        .sort((a, b) => String(a.lineCode).localeCompare(String(b.lineCode), "zh-Hans-CN"))
    }))
    .sort((a, b) => String(a.workshopCode).localeCompare(String(b.workshopCode), "zh-Hans-CN"));
  const workshops = ensureMinimumWorkshopTabs(configuredWorkshops);

  const allLineKeys = configuredWorkshops.flatMap((workshop) => workshop.lines.map((line) => line.key));
  const processToLines = new Map();
  for (const [processCode, set] of processLineMap.entries()) {
    processToLines.set(
      processCode,
      [...set].sort((a, b) => String(a).localeCompare(String(b), "zh-Hans-CN"))
    );
  }
  for (const processCode of taskProcessCodes) {
    if (!processToLines.has(processCode)) {
      processToLines.set(processCode, allLineKeys);
    }
  }

  return {
    workshops,
    allLineKeys,
    processToLines,
    isFallback
  };
}

