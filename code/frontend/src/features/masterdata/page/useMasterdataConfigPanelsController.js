import { useEffect, useMemo, useState } from "react";
import { supportedCnHolidayYears } from "../../../utils/liteSchedulerEngine";
import {
  buildConfigDates,
  buildHorizonDates,
  buildMasterdataConfigSavePayload,
  buildProcessDefaultParamByCode,
  buildProcessNameByCode,
  buildProcessOptions,
  firstDateFromConfig,
  normalizeDateShiftModeByDate,
  normalizeWeekendRestMode,
  parseConfigResponse,
  parseScheduleCalendarRulesResponse,
  resolveSelectedConfigDateUpdate,
  saveMasterdataConfig,
} from "..";
import { useMasterdataDeviceTopologyController } from "./useMasterdataDeviceTopologyController";
import { useMasterdataOrgPanelsController } from "./useMasterdataOrgPanelsController";
import {
  CONFIG_SUB_LEADER,
  CONFIG_SUB_MATERIAL,
  CONFIG_SUB_RESOURCE,
  CONFIG_SUB_TOPOLOGY,
  CONFIG_TAB,
  DEVICE_CONFIG_TAB,
  EQUIPMENT_TAB,
  ROUTE_TAB,
  WEEKEND_REST_MODE_OPTIONS,
} from "./constants";

function isoTodayLocal() {
  const now = new Date();
  const pad2 = (value) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
}

function scheduleBackgroundTask(task) {
  const browser = typeof window !== "undefined" ? window : globalThis;
  if (typeof browser.requestIdleCallback === "function") {
    const idleId = browser.requestIdleCallback(() => task());
    return () => {
      if (typeof browser.cancelIdleCallback === "function") {
        browser.cancelIdleCallback(idleId);
      }
    };
  }
  const timeoutId = browser.setTimeout(task, 0);
  return () => browser.clearTimeout(timeoutId);
}

const DEFAULT_CONFIG_DATA = Object.freeze({
  processConfigs: [],
  lineTopology: [],
  sectionLeaderBindings: [],
  resourcePool: [],
  materialAvailability: [],
  horizonStartDate: "",
  horizonDays: 0,
  shiftsPerDay: 2,
  skipStatutoryHolidays: false,
  weekendRestMode: "DOUBLE",
  dateShiftModeByDate: {},
});

export function useMasterdataConfigPanelsController({
  routes,
  configRes,
  rulesRes,
  tabParam,
  configSubParam,
  topologyCompanyParam,
  topologyWorkshopParam,
  topologyLineParam,
  processCodeFilter,
  productCodeFilter,
  orderMaterialRows,
  orderMaterialLoading,
  orderMaterialRefreshing,
  orderMaterialError,
  refreshOrderMaterialRows,
  ensureOrderMaterialRowsLoaded,
}) {
  const [activeTab, setActiveTab] = useState(ROUTE_TAB);
  const [activeConfigSubTab, setActiveConfigSubTab] = useState(CONFIG_SUB_MATERIAL);

  const [configData, setConfigData] = useState({ ...DEFAULT_CONFIG_DATA });
  const [selectedConfigDate, setSelectedConfigDate] = useState("");

  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    if (processCodeFilter || productCodeFilter) {
      setActiveTab(ROUTE_TAB);
    }
  }, [processCodeFilter, productCodeFilter]);

  useEffect(() => {
    if (configSubParam === CONFIG_SUB_TOPOLOGY || configSubParam === "process") {
      setActiveTab(DEVICE_CONFIG_TAB);
    } else if (tabParam === DEVICE_CONFIG_TAB || tabParam === EQUIPMENT_TAB) {
      setActiveTab(DEVICE_CONFIG_TAB);
    } else if (tabParam === CONFIG_TAB) {
      setActiveTab(CONFIG_TAB);
    } else if (tabParam === ROUTE_TAB) {
      setActiveTab(ROUTE_TAB);
    }
  }, [tabParam, configSubParam]);

  useEffect(() => {
    if (activeTab === DEVICE_CONFIG_TAB && activeConfigSubTab !== CONFIG_SUB_TOPOLOGY) {
      setActiveConfigSubTab(CONFIG_SUB_TOPOLOGY);
      return;
    }
    if (activeTab === CONFIG_TAB && activeConfigSubTab === CONFIG_SUB_TOPOLOGY) {
      setActiveConfigSubTab(CONFIG_SUB_MATERIAL);
    }
  }, [activeTab, activeConfigSubTab]);

  const processOptions = useMemo(
    () => buildProcessOptions(routes, configData.lineTopology),
    [routes, configData.lineTopology],
  );
  const processNameByCode = useMemo(() => buildProcessNameByCode(processOptions), [processOptions]);
  const processDefaultParamByCode = useMemo(
    () => buildProcessDefaultParamByCode(configData.lineTopology),
    [configData.lineTopology],
  );

  const configDates = useMemo(
    () =>
      buildConfigDates(
        configData.horizonStartDate,
        configData.horizonDays,
        configData.resourcePool,
        configData.materialAvailability,
      ),
    [configData.resourcePool, configData.materialAvailability, configData.horizonStartDate, configData.horizonDays],
  );

  useEffect(() => {
    const nextDate = resolveSelectedConfigDateUpdate(configDates, selectedConfigDate);
    if (nextDate !== null) {
      setSelectedConfigDate(nextDate);
    }
  }, [configDates, selectedConfigDate]);

  useEffect(() => {
    if (!configRes) {
      return;
    }
    let cancelled = false;
    const cancelScheduledTask = scheduleBackgroundTask(() => {
      if (cancelled) {
        return;
      }
      const parsed = parseConfigResponse(configRes);
      if (rulesRes) {
        const rules = parseScheduleCalendarRulesResponse(rulesRes);
        parsed.horizonStartDate = rules.horizonStartDate;
        parsed.horizonDays = rules.horizonDays;
        parsed.skipStatutoryHolidays = rules.skipStatutoryHolidays;
        parsed.weekendRestMode = rules.weekendRestMode;
        parsed.dateShiftModeByDate = rules.dateShiftModeByDate;
      }
      setConfigData(parsed);

      const horizonDates = buildHorizonDates(parsed.horizonStartDate, parsed.horizonDays);
      const firstDate = horizonDates[0] || firstDateFromConfig(parsed) || isoTodayLocal();
      setSelectedConfigDate((prev) => prev || firstDate);
    });

    return () => {
      cancelled = true;
      cancelScheduledTask();
    };
  }, [configRes, rulesRes]);

  const isMaterialPanelActive = activeTab === CONFIG_TAB && activeConfigSubTab === CONFIG_SUB_MATERIAL;
  const isLeaderPanelActive = activeTab === CONFIG_TAB && activeConfigSubTab === CONFIG_SUB_LEADER;
  const isResourcePanelActive = activeTab === CONFIG_TAB && activeConfigSubTab === CONFIG_SUB_RESOURCE;
  const isDeviceTopologyActive = activeTab === DEVICE_CONFIG_TAB;

  useEffect(() => {
    if (!isMaterialPanelActive) {
      return;
    }
    ensureOrderMaterialRowsLoaded().catch(() => {});
  }, [ensureOrderMaterialRowsLoaded, isMaterialPanelActive]);

  async function saveConfig() {
    setSaveMessage("");
    setSaveError("");
    setSaving(true);
    try {
      const payload = buildMasterdataConfigSavePayload(configData.lineTopology, processOptions);
      const res = await saveMasterdataConfig(payload);
      const parsed = parseConfigResponse(res);
      parsed.horizonStartDate = configData.horizonStartDate;
      parsed.horizonDays = configData.horizonDays;
      parsed.skipStatutoryHolidays = configData.skipStatutoryHolidays === true;
      parsed.weekendRestMode = normalizeWeekendRestMode(configData.weekendRestMode);
      parsed.dateShiftModeByDate = normalizeDateShiftModeByDate(configData.dateShiftModeByDate);
      setConfigData(parsed);
      setSelectedConfigDate((prev) => {
        const horizonDates = buildHorizonDates(parsed.horizonStartDate, parsed.horizonDays);
        const validDateSet = new Set(buildConfigDates(
          parsed.horizonStartDate,
          parsed.horizonDays,
          parsed.resourcePool,
          parsed.materialAvailability,
        ));
        if (prev && validDateSet.has(prev)) {
          return prev;
        }
        return horizonDates[0] || firstDateFromConfig(parsed);
      });
      setSaveMessage("Configuration saved successfully.");
    } catch (error) {
      setSaveError(error.message || "Failed to save configuration.");
    } finally {
      setSaving(false);
    }
  }

  const holidayYearHintText = useMemo(() => {
    const years = supportedCnHolidayYears();
    return years.length > 0 ? years.join(" / ") : "-";
  }, []);

  function updatePlanningWindow(field, value) {
    setConfigData((prev) => ({
      ...prev,
      [field]: value,
    }));
  }

  const planningWindowProps = useMemo(
    () => ({
      horizonStartDate: configData.horizonStartDate,
      horizonDays: configData.horizonDays,
      onPlanningWindowChange: updatePlanningWindow,
      skipStatutoryHolidays: configData.skipStatutoryHolidays,
      onSkipStatutoryHolidaysChange: (checked) =>
        setConfigData((prev) => ({
          ...prev,
          skipStatutoryHolidays: checked,
        })),
      weekendOptions: WEEKEND_REST_MODE_OPTIONS,
      selectedWeekendRestMode: normalizeWeekendRestMode(configData.weekendRestMode),
      onWeekendRestModeChange: (value) =>
        setConfigData((prev) => ({
          ...prev,
          weekendRestMode: value,
        })),
      holidayYearHintText,
    }),
    [
      configData.horizonStartDate,
      configData.horizonDays,
      configData.skipStatutoryHolidays,
      configData.weekendRestMode,
      holidayYearHintText,
    ],
  );

  const topologyController = useMasterdataDeviceTopologyController({
    enabled: isDeviceTopologyActive,
    configData,
    setConfigData,
    processOptions,
    processNameByCode,
    saving,
    saveConfig,
    topologyCompanyParam,
    topologyWorkshopParam,
    topologyLineParam,
    setSaveError,
  });

  const orgPanelsController = useMasterdataOrgPanelsController({
    enableSectionLeader: isLeaderPanelActive,
    enableResourcePool: isResourcePanelActive,
    configData,
    setConfigData,
    selectedConfigDate,
    processOptions,
    processNameByCode,
    processDefaultParamByCode,
    setSaveError,
  });

  const orderMaterialProps = useMemo(
    () => ({
      orderMaterialRows,
      orderMaterialLoading,
      orderMaterialRefreshing,
      orderMaterialError,
      onRefresh: () => refreshOrderMaterialRows(true).catch(() => {}),
    }),
    [orderMaterialRows, orderMaterialLoading, orderMaterialRefreshing, orderMaterialError, refreshOrderMaterialRows],
  );

  return {
    activeTab,
    setActiveTab,
    activeConfigSubTab,
    setActiveConfigSubTab,
    saving,
    saveMessage,
    saveError,
    saveConfig,
    planningWindowProps,
    deviceTopologyProps: topologyController.deviceTopologyProps,
    sectionLeaderProps: orgPanelsController.sectionLeaderProps,
    resourcePoolProps: orgPanelsController.resourcePoolProps,
    orderMaterialProps,
    lineTopology: topologyController.lineTopology,
  };
}
