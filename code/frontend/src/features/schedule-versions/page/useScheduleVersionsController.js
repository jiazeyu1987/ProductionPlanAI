import { useEffect, useMemo, useState } from "react";
import {
  listScheduleVersionAlgorithm,
  listScheduleVersionDiff,
  listScheduleVersions,
  listScheduleVersionTasks,
  publishScheduleVersion,
  rollbackScheduleVersion,
} from "../../schedule";
import { buildAlgorithmNarrative, normalizeProcessSummaryRow } from "../scheduleVersionsAlgorithmUtils";
import { buildDiffRows } from "../scheduleVersionsDiffUtils";
import { toNumber } from "../scheduleVersionsFormattersUtils";

export function useScheduleVersionsController() {
  const [rows, setRows] = useState([]);
  const [showDraft, setShowDraft] = useState(false);
  const [compareWith, setCompareWith] = useState("");
  const [diff, setDiff] = useState(null);
  const [algorithmDetail, setAlgorithmDetail] = useState(null);
  const [selectedMaxAllocationRow, setSelectedMaxAllocationRow] = useState(null);
  const [message, setMessage] = useState("");

  const algorithmNarrative = useMemo(() => buildAlgorithmNarrative(algorithmDetail), [algorithmDetail]);
  const processSummaryRows = useMemo(() => {
    const detailRows = Array.isArray(algorithmDetail?.process_summary) ? algorithmDetail.process_summary : [];
    return detailRows.map(normalizeProcessSummaryRow);
  }, [algorithmDetail]);

  const visibleRows = useMemo(
    () => (showDraft ? rows : rows.filter((row) => row.status !== "DRAFT")),
    [showDraft, rows],
  );

  async function refresh() {
    const data = await listScheduleVersions();
    setRows(data.items ?? []);
  }

  async function publish(versionNo) {
    await publishScheduleVersion(versionNo, {
      operator: "publisher01",
      reason: "MVP publish",
    });
    setMessage(`已发布版本：${versionNo}`);
    await refresh();
  }

  async function rollback(versionNo) {
    await rollbackScheduleVersion(versionNo, {
      operator: "publisher01",
      reason: "MVP rollback simulation",
    });
    setMessage(`已回滚到 ${versionNo}`);
    await refresh();
  }

  async function compare(versionNo) {
    if (!compareWith) {
      setMessage("请先选择对比基线版本。");
      return;
    }
    const [summary, baseTasks, currentTasks] = await Promise.all([
      listScheduleVersionDiff(versionNo, compareWith),
      listScheduleVersionTasks(compareWith),
      listScheduleVersionTasks(versionNo),
    ]);

    const diffRows = buildDiffRows(baseTasks.items ?? [], currentTasks.items ?? []);
    const affectedOrders = new Set(diffRows.map((row) => row.order_no)).size;
    const netDeltaQty = diffRows.reduce((sum, row) => sum + toNumber(row.delta_qty), 0);
    setDiff({
      baseVersionNo: compareWith,
      compareVersionNo: versionNo,
      summary,
      rows: diffRows,
      affectedOrders,
      netDeltaQty,
    });
    setMessage("");
  }

  async function showAlgorithm(versionNo) {
    const detail = await listScheduleVersionAlgorithm(versionNo);
    setAlgorithmDetail(detail);
    setSelectedMaxAllocationRow(null);
  }

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  useEffect(() => {
    if (visibleRows.length === 0) {
      if (compareWith !== "") {
        setCompareWith("");
      }
      return;
    }
    const exists = visibleRows.some((row) => row.version_no === compareWith);
    if (!exists) {
      setCompareWith(visibleRows[0].version_no);
    }
  }, [compareWith, visibleRows]);

  return {
    rows,
    visibleRows,
    showDraft,
    compareWith,
    diff,
    algorithmDetail,
    algorithmNarrative,
    processSummaryRows,
    selectedMaxAllocationRow,
    message,
    setShowDraft,
    setCompareWith,
    setSelectedMaxAllocationRow,
    refresh,
    publish,
    rollback,
    compare,
    showAlgorithm,
  };
}

