import { useCallback, useEffect, useMemo, useState } from "react";
import {
  generateLegacySchedule,
  getMasterdataConfig,
  getScheduleCalendarRules,
  listScheduleVersions,
  listScheduleVersionTasks,
  saveScheduleCalendarRules
} from "../schedule";
import {
  buildCalendarWeeksByMonth,
  formatMonthText,
  getMonthDayCount,
  isoTodayLocal,
  monthTextFromDate,
  pad2,
  parseMonthText
} from "../schedule/calendarDateUtils";
import {
  DATE_SHIFT_MODE,
  DATE_SHIFT_MODE_LABEL,
  defaultDateShiftMode,
  nextDateShiftMode,
  normalizeDateShiftMode,
  normalizeDateShiftModeByDate,
  normalizeWeekendRestMode,
  resolveDateShiftMode
} from "../schedule/calendarModeUtils";
import {
  buildDayScheduleMap,
  buildEmptyDaySchedule,
  buildTopology,
  normalizeTaskRows
} from "../schedule/topologyScheduleUtils";
import { isCnStatutoryHoliday, WEEKEND_REST_MODE } from "../../utils/liteSchedulerEngine";
import {
  normalizeStrategyCode,
  normalizeVersionNo,
  parseCalendarRulesResponse,
  parseMasterdataConfigResponse,
  pickPreferredVersionNo
} from "./scheduleCalendarPageUtils";

export function useScheduleCalendarController() {
  const [versions, setVersions] = useState([]);
  const [selectedVersionNo, setSelectedVersionNo] = useState("");
  const [tasks, setTasks] = useState([]);
  const [lineTopologyRows, setLineTopologyRows] = useState([]);
  const [skipStatutoryHolidays, setSkipStatutoryHolidays] = useState(false);
  const [weekendRestMode, setWeekendRestMode] = useState(WEEKEND_REST_MODE.DOUBLE);
  const [dateShiftModeByDate, setDateShiftModeByDate] = useState({});
  const [calendarMonth, setCalendarMonth] = useState(
    monthTextFromDate(isoTodayLocal()) || "2026-01"
  );
  const [selectedDate, setSelectedDate] = useState(isoTodayLocal());
  const [loadingInit, setLoadingInit] = useState(false);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [savingRules, setSavingRules] = useState(false);
  const [replanning, setReplanning] = useState(false);
  const [activeWorkshopCode, setActiveWorkshopCode] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const topology = useMemo(() => buildTopology(lineTopologyRows, tasks), [lineTopologyRows, tasks]);
  const dayScheduleByDate = useMemo(() => buildDayScheduleMap(tasks, topology), [tasks, topology]);
  const calendarWeeks = useMemo(() => buildCalendarWeeksByMonth(calendarMonth), [calendarMonth]);

  const selectedDaySchedule = useMemo(() => {
    if (!selectedDate) {
      return null;
    }
    return dayScheduleByDate[selectedDate] || buildEmptyDaySchedule(selectedDate, topology.workshops);
  }, [dayScheduleByDate, selectedDate, topology.workshops]);

  const workshopTabs = useMemo(
    () => (Array.isArray(selectedDaySchedule?.workshops) ? selectedDaySchedule.workshops : []),
    [selectedDaySchedule]
  );

  const activeWorkshop = useMemo(() => {
    if (workshopTabs.length === 0) {
      return null;
    }
    return workshopTabs.find((item) => item.workshopCode === activeWorkshopCode) || workshopTabs[0];
  }, [activeWorkshopCode, workshopTabs]);

  const versionOptions = useMemo(
    () =>
      (versions || []).map((row) => ({
        versionNo: normalizeVersionNo(row),
        statusName: String(row?.status_name_cn || row?.status || "").trim() || "-"
      })),
    [versions]
  );

  const selectedVersionRow = useMemo(
    () => (versions || []).find((row) => normalizeVersionNo(row) === selectedVersionNo) || null,
    [selectedVersionNo, versions]
  );

  const refreshVersionsAndConfig = useCallback(async (keepCurrentSelection = true) => {
    setLoadingInit(true);
    setError("");
    try {
      const [versionsRes, configRes, rulesRes] = await Promise.all([
        listScheduleVersions(),
        getMasterdataConfig().catch(() => null),
        getScheduleCalendarRules().catch(() => null)
      ]);

      const versionRows = Array.isArray(versionsRes?.items) ? versionsRes.items : [];
      setVersions(versionRows);
      setSelectedVersionNo((prev) => {
        if (
          keepCurrentSelection &&
          prev &&
          versionRows.some((row) => normalizeVersionNo(row) === prev)
        ) {
          return prev;
        }
        return pickPreferredVersionNo(versionRows);
      });

      if (configRes) {
        const configData = parseMasterdataConfigResponse(configRes);
        setLineTopologyRows(Array.isArray(configData.lineTopology) ? configData.lineTopology : []);
      }
      if (rulesRes) {
        const rulesData = parseCalendarRulesResponse(rulesRes);
        setSkipStatutoryHolidays(rulesData.skipStatutoryHolidays === true);
        setWeekendRestMode(normalizeWeekendRestMode(rulesData.weekendRestMode));
        setDateShiftModeByDate(normalizeDateShiftModeByDate(rulesData.dateShiftModeByDate));
      }
    } catch (e) {
      setError(e.message || "加载失败");
    } finally {
      setLoadingInit(false);
    }
  }, []);

  const refreshTasks = useCallback(async (versionNo) => {
    const safeVersionNo = String(versionNo || "").trim();
    if (!safeVersionNo) {
      setTasks([]);
      return;
    }
    setLoadingTasks(true);
    setError("");
    try {
      const result = await listScheduleVersionTasks(safeVersionNo);
      const normalized = normalizeTaskRows(result?.items ?? []);
      setTasks(normalized);
      if (normalized.length > 0) {
        const firstDate = normalized[0].date;
        setCalendarMonth((prev) => prev || monthTextFromDate(firstDate) || monthTextFromDate(isoTodayLocal()));
        setSelectedDate((prev) => prev || firstDate);
      }
    } catch (e) {
      setTasks([]);
      setError(e.message || "加载排产任务失败");
    } finally {
      setLoadingTasks(false);
    }
  }, []);

  useEffect(() => {
    refreshVersionsAndConfig(false).catch(() => {});
  }, [refreshVersionsAndConfig]);

  useEffect(() => {
    refreshTasks(selectedVersionNo).catch(() => {});
  }, [refreshTasks, selectedVersionNo]);

  useEffect(() => {
    if (!selectedDate) {
      return;
    }
    const parsed = parseMonthText(calendarMonth);
    if (!parsed) {
      return;
    }
    const firstDay = `${parsed.year}-${pad2(parsed.month)}-01`;
    const lastDay = `${parsed.year}-${pad2(parsed.month)}-${pad2(
      getMonthDayCount(parsed.year, parsed.month)
    )}`;
    if (selectedDate < firstDay || selectedDate > lastDay) {
      setSelectedDate(firstDay);
    }
  }, [calendarMonth, selectedDate]);

  useEffect(() => {
    if (workshopTabs.length === 0) {
      setActiveWorkshopCode("");
      return;
    }
    const exists = workshopTabs.some((item) => item.workshopCode === activeWorkshopCode);
    if (!exists) {
      setActiveWorkshopCode(workshopTabs[0].workshopCode);
    }
  }, [activeWorkshopCode, workshopTabs]);

  const moveCalendarMonth = useCallback(
    (offset) => {
      const parsed = parseMonthText(calendarMonth);
      if (!parsed) {
        return;
      }
      const date = new Date(Date.UTC(parsed.year, parsed.month - 1, 1));
      date.setUTCMonth(date.getUTCMonth() + Number(offset || 0));
      setCalendarMonth(formatMonthText(date.getUTCFullYear(), date.getUTCMonth() + 1));
    },
    [calendarMonth]
  );

  const selectCalendarMonth = useCallback((value) => {
    const parsed = parseMonthText(value);
    if (!parsed) {
      return;
    }
    setCalendarMonth(formatMonthText(parsed.year, parsed.month));
  }, []);

  const restReasonForDate = useCallback(
    (dateText) => {
      if (skipStatutoryHolidays === true && isCnStatutoryHoliday(dateText)) {
        return "法定节假日";
      }
      return "";
    },
    [skipStatutoryHolidays]
  );

  const setDateMode = useCallback(
    (dateText, mode) => {
      const safeDate = String(dateText || "").trim();
      if (!safeDate) {
        return;
      }
      const normalizedActionMode = normalizeDateShiftMode(mode);
      if (!normalizedActionMode) {
        return;
      }
      const currentMode = resolveDateShiftMode(
        safeDate,
        skipStatutoryHolidays === true,
        weekendRestMode,
        dateShiftModeByDate
      );
      const normalizedMode = nextDateShiftMode(currentMode, normalizedActionMode);
      const defaultMode = defaultDateShiftMode(safeDate, skipStatutoryHolidays === true, weekendRestMode);
      setDateShiftModeByDate((prev) => {
        const next = { ...prev };
        if (!normalizedMode || normalizedMode === defaultMode) {
          delete next[safeDate];
        } else {
          next[safeDate] = normalizedMode;
        }
        return next;
      });
      setMessage(`${safeDate} 已设为${DATE_SHIFT_MODE_LABEL[normalizedMode] || normalizedMode}。`);
      setError("");
    },
    [dateShiftModeByDate, skipStatutoryHolidays, weekendRestMode]
  );

  const saveRules = useCallback(async () => {
    setSavingRules(true);
    setMessage("");
    setError("");
    try {
      const payload = {
        operator: "calendar-admin",
        skip_statutory_holidays: skipStatutoryHolidays === true,
        weekend_rest_mode: normalizeWeekendRestMode(weekendRestMode),
        date_shift_mode_by_date: normalizeDateShiftModeByDate(dateShiftModeByDate)
      };
      await saveScheduleCalendarRules(payload);
      setMessage("排期规则已保存。请重新生成排产版本后再查看规则生效结果。");
    } catch (e) {
      setError(e.message || "保存失败");
    } finally {
      setSavingRules(false);
    }
  }, [dateShiftModeByDate, skipStatutoryHolidays, weekendRestMode]);

  const saveAndReplan = useCallback(async () => {
    if (!selectedVersionNo) {
      setError("请先选择一个排产版本。");
      setMessage("");
      return;
    }
    setReplanning(true);
    setMessage("");
    setError("");
    try {
      const payload = {
        operator: "calendar-admin",
        skip_statutory_holidays: skipStatutoryHolidays === true,
        weekend_rest_mode: normalizeWeekendRestMode(weekendRestMode),
        date_shift_mode_by_date: normalizeDateShiftModeByDate(dateShiftModeByDate)
      };
      await saveScheduleCalendarRules(payload);
      const generated = await generateLegacySchedule({
        base_version_no: selectedVersionNo,
        autoReplan: false,
        strategy_code: normalizeStrategyCode(
          selectedVersionRow?.strategy_code || selectedVersionRow?.strategyCode
        )
      });
      const nextVersionNo = normalizeVersionNo(generated);
      if (nextVersionNo) {
        await refreshVersionsAndConfig(false);
        setSelectedVersionNo(nextVersionNo);
        await refreshTasks(nextVersionNo);
        setMessage(`已按最新微调规则重排，生成新版本：${nextVersionNo}`);
      } else {
        await refreshVersionsAndConfig(false);
        setMessage("已触发重排，但未获取到新版本号。请刷新后确认。");
      }
    } catch (e) {
      setError(e.message || "重排失败");
    } finally {
      setReplanning(false);
    }
  }, [
    dateShiftModeByDate,
    refreshTasks,
    refreshVersionsAndConfig,
    selectedVersionNo,
    selectedVersionRow,
    skipStatutoryHolidays,
    weekendRestMode
  ]);

  const selectedDateMode = useMemo(
    () =>
      resolveDateShiftMode(
        selectedDate,
        skipStatutoryHolidays === true,
        weekendRestMode,
        dateShiftModeByDate
      ),
    [dateShiftModeByDate, selectedDate, skipStatutoryHolidays, weekendRestMode]
  );

  return {
    versions,
    selectedVersionNo,
    setSelectedVersionNo,
    tasks,
    lineTopologyRows,
    skipStatutoryHolidays,
    setSkipStatutoryHolidays,
    weekendRestMode,
    setWeekendRestMode,
    dateShiftModeByDate,
    calendarMonth,
    setCalendarMonth,
    selectedDate,
    setSelectedDate,
    loadingInit,
    loadingTasks,
    savingRules,
    replanning,
    activeWorkshopCode,
    setActiveWorkshopCode,
    message,
    error,

    topology,
    dayScheduleByDate,
    calendarWeeks,
    selectedDaySchedule,
    workshopTabs,
    activeWorkshop,
    versionOptions,
    selectedVersionRow,
    selectedDateMode,

    refreshVersionsAndConfig,
    moveCalendarMonth,
    selectCalendarMonth,
    restReasonForDate,
    setDateMode,
    saveRules,
    saveAndReplan
  };
}

