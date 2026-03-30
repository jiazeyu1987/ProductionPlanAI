import { normalizeCompanyCode } from "./codeNormalizeUtils";
import { toInt } from "./dataTransformUtils";
import { matchesSectionLeaderBindingRow } from "./lineTopologyUtils";

export function sortSectionLeaderBindingRows(rows) {
  return [...(rows || [])].sort((a, b) => {
    const byLeader = String(a?.leader_id || "").localeCompare(String(b?.leader_id || ""), "zh-Hans-CN");
    if (byLeader !== 0) {
      return byLeader;
    }
    return String(a?.line_code || "").localeCompare(String(b?.line_code || ""), "zh-Hans-CN");
  });
}

export function buildSectionLeaderLineMetaMap(lineOptions) {
  const byCode = new Map();
  for (const line of lineOptions || []) {
    const lineCode = String(line?.lineCode || "").trim().toUpperCase();
    if (!lineCode || byCode.has(lineCode)) {
      continue;
    }
    byCode.set(lineCode, {
      lineCode,
      lineName: String(line?.lineName || lineCode).trim() || lineCode,
      companyCode: normalizeCompanyCode(line?.companyCode),
      workshopCode: String(line?.workshopCode || "").trim().toUpperCase(),
    });
  }
  return byCode;
}

export function updateSectionLeaderBindingField(row, field, value, lineMetaByCode) {
  if (field === "line_code") {
    const nextLineCode = String(value || "").trim().toUpperCase();
    const meta = lineMetaByCode?.get(nextLineCode);
    return {
      ...row,
      line_code: nextLineCode,
      line_name: meta?.lineName || nextLineCode,
      company_code: normalizeCompanyCode(meta?.companyCode),
      workshop_code: meta?.workshopCode || "",
    };
  }
  if (field === "leader_id") {
    return { ...row, leader_id: String(value || "").trim().toUpperCase() };
  }
  if (field === "active_flag") {
    return { ...row, active_flag: toInt(value, 1) };
  }
  return { ...row, [field]: value };
}

export function updateSectionLeaderBindingRows(rows, target, field, value, lineMetaByCode) {
  return (rows || []).map((row) => {
    if (!matchesSectionLeaderBindingRow(row, target)) {
      return row;
    }
    return updateSectionLeaderBindingField(row, field, value, lineMetaByCode);
  });
}

export function buildNewSectionLeaderBinding(firstLine, existingCount) {
  const nextIndex = Math.max(0, toInt(existingCount, 0)) + 1;
  const lineCode = String(firstLine?.lineCode || "").trim().toUpperCase();
  const lineName = String(firstLine?.lineName || lineCode).trim() || lineCode;
  return {
    leader_id: `LEADER-NEW-${nextIndex}`,
    leader_name: "鐎规悶鍎查宀勬⒐?{prev.sectionLeaderBindings.length + 1}",
    line_code: lineCode,
    line_name: lineName,
    company_code: normalizeCompanyCode(firstLine?.companyCode),
    workshop_code: String(firstLine?.workshopCode || "").trim().toUpperCase(),
    active_flag: 1,
  };
}
