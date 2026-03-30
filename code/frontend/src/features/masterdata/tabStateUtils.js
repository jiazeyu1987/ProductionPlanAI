export function resolveActiveTabFromQuery({
  tabParam,
  configSubParam,
  routeTab,
  configTab,
  deviceConfigTab,
  equipmentTab,
  configSubTopology,
  configSubProcess,
}) {
  if (configSubParam === configSubTopology || configSubParam === configSubProcess) {
    return deviceConfigTab;
  }
  if (tabParam === deviceConfigTab || tabParam === equipmentTab) {
    return deviceConfigTab;
  }
  if (tabParam === configTab) {
    return configTab;
  }
  if (tabParam === routeTab) {
    return routeTab;
  }
  return null;
}

export function resolveConfigSubTabFromQuery(configSubParam, allowedSubTabs = []) {
  return Array.isArray(allowedSubTabs) && allowedSubTabs.includes(configSubParam) ? configSubParam : null;
}

export function resolveConfigSubTabForActiveTab({
  activeTab,
  activeConfigSubTab,
  deviceConfigTab,
  configSubTopology,
  configTab,
  configSubMaterial,
}) {
  if (activeTab === deviceConfigTab && activeConfigSubTab !== configSubTopology) {
    return configSubTopology;
  }
  if (activeTab === configTab && activeConfigSubTab === configSubTopology) {
    return configSubMaterial;
  }
  return null;
}
