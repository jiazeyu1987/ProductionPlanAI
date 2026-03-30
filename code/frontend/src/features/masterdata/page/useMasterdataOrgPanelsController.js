import { useEffect, useMemo, useState } from "react";
import {
  DATE_SHIFT_MODE,
  buildLineOptions,
  buildResourceRowKey,
  buildResourceRowsForDate,
  buildSectionLeaderLineMetaMap,
  buildNewSectionLeaderBinding,
  buildVisibleShiftCodeSet,
  filterOutSectionLeaderBindingRows,
  resolveAddResourceRowDraft,
  resolveDateShiftMode,
  resolveSelectableCodeUpdate,
  sortSectionLeaderBindingRows,
  updateSectionLeaderBindingRows,
} from "..";
import { SHIFT_OPTIONS } from "./constants";

const EMPTY_LINE_OPTIONS = Object.freeze([]);
const EMPTY_SECTION_ROWS = Object.freeze([]);
const EMPTY_RESOURCE_ROWS = Object.freeze([]);
const EMPTY_SHIFT_CODE_SET = new Set();

export function useMasterdataOrgPanelsController({
  enableSectionLeader = true,
  enableResourcePool = true,
  configData,
  setConfigData,
  selectedConfigDate,
  processOptions,
  processNameByCode,
  processDefaultParamByCode,
  setSaveError,
}) {
  const [newResourceShift, setNewResourceShift] = useState("DAY");
  const [newResourceProcess, setNewResourceProcess] = useState("");

  useEffect(() => {
    if (!enableResourcePool) {
      return;
    }
    const nextCode = resolveSelectableCodeUpdate(processOptions, newResourceProcess);
    if (nextCode !== null) {
      setNewResourceProcess(nextCode);
    }
  }, [enableResourcePool, processOptions, newResourceProcess]);

  const lineOptions = useMemo(
    () => (enableSectionLeader ? buildLineOptions(configData.lineTopology) : EMPTY_LINE_OPTIONS),
    [enableSectionLeader, configData.lineTopology],
  );
  const sectionLeaderLineMetaByCode = useMemo(
    () => (enableSectionLeader ? buildSectionLeaderLineMetaMap(lineOptions) : new Map()),
    [enableSectionLeader, lineOptions],
  );
  const sectionLeaderRows = useMemo(
    () => (enableSectionLeader ? sortSectionLeaderBindingRows(configData.sectionLeaderBindings) : EMPTY_SECTION_ROWS),
    [enableSectionLeader, configData.sectionLeaderBindings],
  );

  function updateSectionLeaderRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: updateSectionLeaderBindingRows(
        prev.sectionLeaderBindings,
        target,
        field,
        value,
        sectionLeaderLineMetaByCode,
      ),
    }));
  }

  function addSectionLeaderRow() {
    if (lineOptions.length === 0) {
      setSaveError("No production line available. Please configure line topology first.");
      return;
    }
    const firstLine = lineOptions[0];
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: [
        ...prev.sectionLeaderBindings,
        buildNewSectionLeaderBinding(firstLine, prev.sectionLeaderBindings.length),
      ],
    }));
  }

  function removeSectionLeaderRow(target) {
    const confirmed = window.confirm("Remove this section leader binding?");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: filterOutSectionLeaderBindingRows(prev.sectionLeaderBindings, target),
    }));
  }

  const sectionLeaderProps = useMemo(
    () => ({
      sectionLeaderRows,
      lineOptions,
      onAdd: addSectionLeaderRow,
      onUpdate: updateSectionLeaderRow,
      onRemove: removeSectionLeaderRow,
    }),
    [sectionLeaderRows, lineOptions],
  );

  const selectedDateMode = useMemo(
    () =>
      enableResourcePool
        ? resolveDateShiftMode(
            selectedConfigDate,
            configData.skipStatutoryHolidays === true,
            configData.weekendRestMode,
            configData.dateShiftModeByDate,
          )
        : DATE_SHIFT_MODE.DAY,
    [
      enableResourcePool,
      selectedConfigDate,
      configData.skipStatutoryHolidays,
      configData.weekendRestMode,
      configData.dateShiftModeByDate,
    ],
  );
  const visibleShiftCodeSet = useMemo(
    () => (enableResourcePool ? buildVisibleShiftCodeSet(selectedDateMode) : EMPTY_SHIFT_CODE_SET),
    [enableResourcePool, selectedDateMode],
  );
  const resourceRowsForDate = useMemo(
    () =>
      enableResourcePool
        ? buildResourceRowsForDate(configData.resourcePool, selectedConfigDate, visibleShiftCodeSet)
        : EMPTY_RESOURCE_ROWS,
    [enableResourcePool, configData.resourcePool, selectedConfigDate, visibleShiftCodeSet],
  );

  function updateResourceRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      resourcePool: prev.resourcePool.map((row) => {
        const isMatch = buildResourceRowKey(row) === buildResourceRowKey(target);
        return isMatch ? { ...row, [field]: value } : row;
      }),
    }));
  }

  function addResourceRow() {
    const addDraft = resolveAddResourceRowDraft({
      selectedConfigDate,
      newResourceShift,
      newResourceProcess,
      resourcePool: configData.resourcePool,
      processNameByCode,
      processDefaultParamByCode,
    });
    if (addDraft.error) {
      setSaveError(addDraft.error);
      return;
    }
    setSaveError("");
    setConfigData((prev) => ({
      ...prev,
      resourcePool: [...prev.resourcePool, addDraft.row],
    }));
  }

  function removeResourceRow(target) {
    const confirmed = window.confirm("Remove this resource row?");
    if (!confirmed) {
      return;
    }
    const targetKey = buildResourceRowKey(target);
    setConfigData((prev) => ({
      ...prev,
      resourcePool: prev.resourcePool.filter((row) => buildResourceRowKey(row) !== targetKey),
    }));
  }

  const resourcePoolProps = useMemo(
    () => ({
      selectedConfigDate,
      newResourceShift,
      onResourceShiftChange: setNewResourceShift,
      newResourceProcess,
      onResourceProcessChange: setNewResourceProcess,
      processOptions,
      shiftOptions: SHIFT_OPTIONS,
      onAddResourceRow: addResourceRow,
      resourceRowsForDate,
      isRestMode: selectedDateMode === DATE_SHIFT_MODE.REST,
      onUpdateResourceRow: updateResourceRow,
      onRemoveResourceRow: removeResourceRow,
    }),
    [
      selectedConfigDate,
      newResourceShift,
      newResourceProcess,
      processOptions,
      resourceRowsForDate,
      selectedDateMode,
    ],
  );

  return {
    sectionLeaderProps,
    resourcePoolProps,
  };
}
