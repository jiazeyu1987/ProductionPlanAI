import { useEffect, useMemo, useRef, useState } from "react";
import {
  applyLineTopologyWithSectionLeaderPrune,
  buildLineTopologyProcessRow,
  buildTopologyLineProcessCodeSet,
  buildTopologyLineProcessRow,
  buildTopologyWorkshopGroups,
  filterOutLineTopologyRows,
  filterOutTopologyLineProcessRows,
  filterOutTopologyLineRows,
  filterOutTopologyWorkshopRows,
  findTopologyWorkshopParamMatch,
  nextLineCodeFromTopology,
  nextWorkshopCodeFromTopology,
  normalizeCompanyCode,
  normalizeProcessCode,
  pickSelectedTopologyLine,
  pickSelectedTopologyLineProcessRows,
  pickSelectedTopologyWorkshop,
  resolveSelectedTopologyLineCodeOnLineEdit,
  resolveTopologyLineCodeUpdate,
  resolveTopologyLineProcessToggleAction,
  resolveTopologyWorkshopKeyUpdate,
  sortLineTopologyRows,
  updateLineTopologyConfigRows,
  updateTopologyLineRows,
  updateTopologyWorkshopRows,
} from "..";

const EMPTY_WORKSHOP_GROUPS = Object.freeze([]);
const EMPTY_PROCESS_ROWS = Object.freeze([]);

export function useMasterdataDeviceTopologyController({
  enabled = true,
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
}) {
  const [selectedTopologyWorkshopKey, setSelectedTopologyWorkshopKey] = useState("");
  const [selectedTopologyLineCode, setSelectedTopologyLineCode] = useState("");
  const appliedTopologyWorkshopParamRef = useRef("");

  useEffect(() => {
    if (!enabled || !topologyLineParam) {
      return;
    }
    setSelectedTopologyLineCode(topologyLineParam);
  }, [enabled, topologyLineParam]);

  const lineTopologyRows = useMemo(
    () => (enabled ? sortLineTopologyRows(configData.lineTopology) : configData.lineTopology),
    [enabled, configData.lineTopology],
  );
  const topologyWorkshopGroups = useMemo(
    () => (enabled ? buildTopologyWorkshopGroups(lineTopologyRows, processNameByCode) : EMPTY_WORKSHOP_GROUPS),
    [enabled, lineTopologyRows, processNameByCode],
  );

  const selectedTopologyWorkshop = useMemo(
    () => (enabled ? pickSelectedTopologyWorkshop(topologyWorkshopGroups, selectedTopologyWorkshopKey) : null),
    [enabled, selectedTopologyWorkshopKey, topologyWorkshopGroups],
  );
  const selectedTopologyLine = useMemo(
    () => (enabled ? pickSelectedTopologyLine(selectedTopologyWorkshop, selectedTopologyLineCode) : null),
    [enabled, selectedTopologyWorkshop, selectedTopologyLineCode],
  );
  const selectedTopologyLineProcessRows = useMemo(
    () => (enabled ? pickSelectedTopologyLineProcessRows(selectedTopologyLine) : EMPTY_PROCESS_ROWS),
    [enabled, selectedTopologyLine],
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const nextWorkshopKey = resolveTopologyWorkshopKeyUpdate(topologyWorkshopGroups, selectedTopologyWorkshopKey);
    if (nextWorkshopKey !== null) {
      setSelectedTopologyWorkshopKey(nextWorkshopKey);
    }
  }, [enabled, selectedTopologyWorkshopKey, topologyWorkshopGroups]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const matched = findTopologyWorkshopParamMatch(
      topologyWorkshopGroups,
      topologyWorkshopParam,
      topologyCompanyParam,
    );
    if (!matched || appliedTopologyWorkshopParamRef.current === matched.applyKey) {
      return;
    }
    if (matched.matchedKey !== selectedTopologyWorkshopKey) {
      setSelectedTopologyWorkshopKey(matched.matchedKey);
    }
    appliedTopologyWorkshopParamRef.current = matched.applyKey;
  }, [enabled, topologyWorkshopParam, topologyCompanyParam, topologyWorkshopGroups, selectedTopologyWorkshopKey]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const nextLineCode = resolveTopologyLineCodeUpdate(
      selectedTopologyWorkshop,
      selectedTopologyLineCode,
      topologyLineParam,
    );
    if (nextLineCode !== null) {
      setSelectedTopologyLineCode(nextLineCode);
    }
  }, [enabled, selectedTopologyWorkshop, selectedTopologyLineCode, topologyLineParam]);

  function addTopologyWorkshop() {
    const defaultProcess = processOptions[0]?.code || "";
    if (!defaultProcess) {
      setSaveError("No process is available. Please maintain process list first.");
      return;
    }
    setSaveError("");
    setConfigData((prev) => {
      const workshopCode = nextWorkshopCodeFromTopology(prev.lineTopology);
      const lineCode = nextLineCodeFromTopology(prev.lineTopology);
      return {
        ...prev,
        lineTopology: [
          ...prev.lineTopology,
          buildLineTopologyProcessRow({
            companyCode: "COMPANY-MAIN",
            workshopCode,
            lineCode,
            processCode: defaultProcess,
          }),
        ],
      };
    });
  }

  function addTopologyLine(workshop) {
    const defaultProcess = processOptions[0]?.code || workshop?.lines?.[0]?.processCodes?.[0] || "";
    if (!defaultProcess) {
      setSaveError("No process is available. Please maintain process list first.");
      return;
    }
    setSaveError("");
    setConfigData((prev) => {
      const lineCode = nextLineCodeFromTopology(prev.lineTopology);
      return {
        ...prev,
        lineTopology: [
          ...prev.lineTopology,
          buildLineTopologyProcessRow({
            companyCode: normalizeCompanyCode(workshop.companyCode),
            workshopCode: String(workshop.workshopCode || "").trim(),
            lineCode,
            processCode: defaultProcess,
          }),
        ],
      };
    });
  }

  function updateTopologyWorkshop(workshop, field, value) {
    const nextValue = String(value || "").trim();
    if (!nextValue) {
      return;
    }
    setConfigData((prev) => ({
      ...prev,
      lineTopology: updateTopologyWorkshopRows(prev.lineTopology, workshop, field, nextValue),
    }));
  }

  function updateTopologyLine(line, field, value) {
    if (field === "line_code") {
      const nextSelectedLineCode = resolveSelectedTopologyLineCodeOnLineEdit(selectedTopologyLineCode, line, value);
      if (nextSelectedLineCode !== null) {
        setSelectedTopologyLineCode(nextSelectedLineCode);
      }
    }
    setConfigData((prev) => ({
      ...prev,
      lineTopology: updateTopologyLineRows(prev.lineTopology, line, field, value),
    }));
  }

  function updateLineTopologyRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      lineTopology: updateLineTopologyConfigRows(prev.lineTopology, target, field, value),
    }));
  }

  function removeTopologyWorkshop(workshop) {
    const confirmed = window.confirm("Remove this workshop and its related lines?");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => {
      const nextLineTopology = filterOutTopologyWorkshopRows(prev.lineTopology, workshop);
      return applyLineTopologyWithSectionLeaderPrune(prev, nextLineTopology);
    });
  }

  function removeTopologyLine(line) {
    const confirmed = window.confirm("Remove this line and its related bindings?");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => {
      const nextLineTopology = filterOutTopologyLineRows(prev.lineTopology, line);
      return applyLineTopologyWithSectionLeaderPrune(prev, nextLineTopology);
    });
  }

  function setTopologyLineProcess(line, processCode, checked) {
    const normalizedProcessCode = normalizeProcessCode(processCode);
    if (!normalizedProcessCode) {
      return;
    }
    setConfigData((prev) => {
      const currentCodes = buildTopologyLineProcessCodeSet(prev.lineTopology, line);
      const toggleAction = resolveTopologyLineProcessToggleAction(currentCodes, normalizedProcessCode, checked);
      if (toggleAction === "noop" || toggleAction === "invalid") {
        return prev;
      }
      if (toggleAction === "block_remove_last") {
        setSaveError("At least one process must remain on the line.");
        return prev;
      }
      setSaveError("");
      if (toggleAction === "add") {
        const newRow = buildTopologyLineProcessRow(line, normalizedProcessCode);
        if (!newRow) {
          return prev;
        }
        return {
          ...prev,
          lineTopology: [...prev.lineTopology, newRow],
        };
      }
      return {
        ...prev,
        lineTopology: filterOutTopologyLineProcessRows(prev.lineTopology, line, normalizedProcessCode),
      };
    });
  }

  function removeLineTopologyRow(target) {
    const confirmed = window.confirm("Remove this line-process mapping?");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => {
      const nextLineTopology = filterOutLineTopologyRows(prev.lineTopology, target);
      return applyLineTopologyWithSectionLeaderPrune(prev, nextLineTopology);
    });
  }

  const deviceTopologyProps = useMemo(
    () => ({
      saving,
      topologyWorkshopGroups,
      selectedTopologyWorkshop,
      selectedTopologyLine,
      selectedTopologyLineProcessRows,
      processOptions,
      processNameByCode,
      onAddTopologyWorkshop: addTopologyWorkshop,
      onSaveTopology: saveConfig,
      onSelectTopologyWorkshop: setSelectedTopologyWorkshopKey,
      onRemoveTopologyWorkshop: removeTopologyWorkshop,
      onAddTopologyLine: addTopologyLine,
      onSelectTopologyLine: setSelectedTopologyLineCode,
      onRemoveTopologyLine: removeTopologyLine,
      onUpdateTopologyLine: updateTopologyLine,
      onSetTopologyLineProcess: setTopologyLineProcess,
      onUpdateLineTopologyRow: updateLineTopologyRow,
      onRemoveLineTopologyRow: removeLineTopologyRow,
    }),
    [
      saving,
      topologyWorkshopGroups,
      selectedTopologyWorkshop,
      selectedTopologyLine,
      selectedTopologyLineProcessRows,
      processOptions,
      processNameByCode,
    ],
  );

  return {
    deviceTopologyProps,
    lineTopology: configData.lineTopology,
  };
}
