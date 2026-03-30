import { useEffect, useMemo, useRef, useState } from "react";
import {
  generateLegacySchedule,
  listLegacySchedules,
  listOrderPool,
  listScheduleVersionAlgorithm,
  listScheduleVersionDailyProcessLoad,
  listScheduleVersions,
  listScheduleVersionTasks,
  publishScheduleVersion,
} from "../../schedule";
import {
  buildBoardSummary,
  buildOrderAllocationRows,
  normalizeStrategyCode,
  normalizeVersionNo,
  strategyLabel,
  toNumber,
} from "../scheduleBoardUtils";

export function useScheduleBoardController() {
  const [versions, setVersions] = useState([]);
  const [selected, setSelected] = useState("");
  const [strategyCode, setStrategyCode] = useState("KEY_ORDER_FIRST");
  const [tasks, setTasks] = useState([]);
  const [orderAllocationRows, setOrderAllocationRows] = useState([]);
  const [dailyProcessLoadRows, setDailyProcessLoadRows] = useState([]);
  const [algorithmDetail, setAlgorithmDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState("");
  const detailRequestSeqRef = useRef(0);

  const selectedVersion = useMemo(
    () => versions.find((item) => normalizeVersionNo(item) === selected) || null,
    [selected, versions],
  );

  const canPublishSelected = String(selectedVersion?.status || "").toUpperCase() === "DRAFT";

  const boardSummary = useMemo(
    () => buildBoardSummary(orderAllocationRows),
    [orderAllocationRows],
  );

  async function loadVersions() {
    const data = await listScheduleVersions();
    const items = data.items ?? [];
    setVersions(items);
    if (items.length === 0) {
      setSelected("");
      return;
    }
    const exists = items.some((item) => normalizeVersionNo(item) === selected);
    if (!selected || !exists) {
      setSelected(normalizeVersionNo(items[items.length - 1]));
    }
  }

  async function loadVersionDetail(versionNo) {
    const requestSeq = detailRequestSeqRef.current + 1;
    detailRequestSeqRef.current = requestSeq;
    if (!versionNo) {
      setTasks([]);
      setAlgorithmDetail(null);
      setOrderAllocationRows([]);
      setDailyProcessLoadRows([]);
      setDetailLoading(false);
      return;
    }
    setDetailLoading(true);
    setTasks([]);
    setAlgorithmDetail(null);
    setOrderAllocationRows([]);
    setDailyProcessLoadRows([]);
    try {
      const [tasksRes, algorithmRes, schedulesRes, orderPoolRes, dailyLoadRes] =
        await Promise.all([
          listScheduleVersionTasks(versionNo),
          listScheduleVersionAlgorithm(versionNo),
          listLegacySchedules(),
          listOrderPool(),
          listScheduleVersionDailyProcessLoad(versionNo),
        ]);
      if (requestSeq !== detailRequestSeqRef.current) {
        return;
      }
      setTasks(tasksRes.items ?? []);
      setAlgorithmDetail(algorithmRes);
      setStrategyCode(
        normalizeStrategyCode(
          algorithmRes?.strategy_code ||
            algorithmRes?.metadata?.schedule_strategy_code ||
            algorithmRes?.metadata?.scheduleStrategyCode,
        ),
      );
      const scheduleItems = schedulesRes.items ?? [];
      const selectedSchedule = scheduleItems.find(
        (item) => normalizeVersionNo(item) === versionNo,
      );
      setOrderAllocationRows(
        buildOrderAllocationRows(
          selectedSchedule,
          orderPoolRes.items ?? [],
          tasksRes.items ?? [],
        ),
      );
      setDailyProcessLoadRows(dailyLoadRes.items ?? []);
    } finally {
      if (requestSeq === detailRequestSeqRef.current) {
        setDetailLoading(false);
      }
    }
  }

  async function generate() {
    setMessage("");
    const nextStrategyCode = normalizeStrategyCode(strategyCode);
    const schedule = await generateLegacySchedule({
      base_version_no: selected || null,
      autoReplan: false,
      strategy_code: nextStrategyCode,
    });
    const versionNo = String(schedule.version_no || schedule.versionNo || "");
    setMessage(`生成完成：${versionNo || "-"}（${strategyLabel(nextStrategyCode)}）`);
    await loadVersions();
    if (versionNo) {
      setSelected(versionNo);
    }
  }

  async function publishSelectedDraft() {
    if (!selected) {
      setMessage("请先选择草稿版本。");
      return;
    }
    if (!canPublishSelected) {
      setMessage("当前选择的版本不是草稿，无法发布。");
      return;
    }
    setPublishing(true);
    setMessage("");
    try {
      await publishScheduleVersion(selected, {
        operator: "publisher01",
        reason: "调度台发布草稿",
      });
      setMessage(`已发布版本：${selected}`);
      await loadVersions();
      await loadVersionDetail(selected);
    } catch (e) {
      setMessage(e.message);
    } finally {
      setPublishing(false);
    }
  }

  useEffect(() => {
    loadVersions().catch(() => {});
  }, []);

  useEffect(() => {
    loadVersionDetail(selected).catch(() => {});
  }, [selected]);

  function onSelectVersionNo(value) {
    setSelected(String(value || ""));
  }

  function onStrategyChange(value) {
    setStrategyCode(normalizeStrategyCode(value));
  }

  const allocationExplainSummary = useMemo(() => {
    if (!algorithmDetail) {
      return null;
    }
    return {
      version_no: algorithmDetail.version_no || selected || "-",
      task_count: algorithmDetail?.summary?.task_count,
      scheduled_qty: algorithmDetail?.summary?.scheduled_qty,
      schedule_completion_rate: algorithmDetail?.summary?.schedule_completion_rate,
      strategyText: strategyLabel(algorithmDetail.strategy_code || strategyCode),
    };
  }, [algorithmDetail, selected, strategyCode]);

  const affectedTaskCount = useMemo(() => toNumber(tasks?.length), [tasks]);

  return {
    versions,
    selectedVersionNo: selected,
    selectedVersion,
    canPublishSelected,
    strategyCode,
    tasks,
    affectedTaskCount,
    orderAllocationRows,
    dailyProcessLoadRows,
    algorithmDetail,
    allocationExplainSummary,
    detailLoading,
    publishing,
    message,
    boardSummary,
    setMessage,
    loadVersions,
    generate,
    publishSelectedDraft,
    onSelectVersionNo,
    onStrategyChange,
  };
}

