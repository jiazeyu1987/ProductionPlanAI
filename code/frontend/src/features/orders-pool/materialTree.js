function hasOwn(target, key) {
  return Object.prototype.hasOwnProperty.call(target, key);
}

function normalizeMaterialCode(value) {
  return String(value || "").trim();
}

function isSelfMadeMaterial(row) {
  return String(row?.child_material_supply_type || "").toUpperCase() === "SELF_MADE";
}

function collectSelfMadeCodes(rows) {
  if (!Array.isArray(rows)) {
    return [];
  }
  return rows
    .filter((row) => isSelfMadeMaterial(row))
    .map((row) => normalizeMaterialCode(row?.child_material_code))
    .filter(Boolean);
}

function buildMaterialTreeRows(
  rootRows,
  childrenByParentCode,
  expandedNodeKeySet,
  loadingByParentCode,
  errorByParentCode,
) {
  const flattened = [];
  function walk(rows, parentNodeKey, depth) {
    if (!Array.isArray(rows)) {
      return;
    }
    rows.forEach((row, index) => {
      const materialCode = normalizeMaterialCode(row?.child_material_code);
      const nodeCode = materialCode || "EMPTY";
      const nodeKey = parentNodeKey
        ? `${parentNodeKey}>${nodeCode}:${index}`
        : `${nodeCode}:${index}`;
      const expandable = isSelfMadeMaterial(row) && Boolean(materialCode);
      const expanded = expandable && expandedNodeKeySet.has(nodeKey);
      const loading = expandable && Boolean(loadingByParentCode[materialCode]);
      const nodeError = expandable ? String(errorByParentCode[materialCode] || "").trim() : "";
      flattened.push({
        ...row,
        id: `material-${nodeKey}`,
        _treeDepth: depth,
        _treeNodeKey: nodeKey,
        _treeMaterialCode: materialCode,
        _treeExpandable: expandable,
        _treeExpanded: expanded,
        _treeLoading: loading,
        _treeError: nodeError,
      });
      if (expanded) {
        walk(childrenByParentCode[materialCode] ?? [], nodeKey, depth + 1);
      }
    });
  }
  walk(rootRows, "", 0);
  return flattened;
}

function collapseExpandedMaterialNodeKeys(previousKeys, nodeKey) {
  if (!nodeKey) {
    return previousKeys;
  }
  return (previousKeys || []).filter(
    (key) => key !== nodeKey && !String(key || "").startsWith(`${nodeKey}>`),
  );
}

export {
  buildMaterialTreeRows,
  collectSelfMadeCodes,
  collapseExpandedMaterialNodeKeys,
  hasOwn,
  isSelfMadeMaterial,
  normalizeMaterialCode,
};

