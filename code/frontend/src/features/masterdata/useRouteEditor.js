import { useState } from "react";
import {
  deleteMasterdataRouteByProductCode,
  saveMasterdataRouteByMode,
} from "./service";

function buildRouteStepsDraft(routeStepsByProduct, productCode, normalizeDependencyType, createEmptyRouteStep) {
  const rows = routeStepsByProduct.get(String(productCode || "").trim().toUpperCase()) || [];
  if (rows.length === 0) {
    return [createEmptyRouteStep()];
  }
  return rows.map((row) => ({
    process_code: String(row.process_code || "").trim().toUpperCase(),
    dependency_type: normalizeDependencyType(row.dependency_type),
  }));
}

function normalizedRouteStepPayload(routeStepsDraft, normalizeDependencyType) {
  return routeStepsDraft
    .map((row, index) => ({
      process_code: String(row.process_code || "").trim().toUpperCase(),
      dependency_type: normalizeDependencyType(row.dependency_type),
      sequence_no: index + 1,
    }))
    .filter((row) => row.process_code);
}

export function useMasterdataRouteEditor({
  routeStepsByProduct,
  refreshMasterdata,
  createEmptyRouteStep,
  normalizeDependencyType,
  modes,
}) {
  const createMode = modes?.create ?? "create";
  const editMode = modes?.edit ?? "edit";
  const copyMode = modes?.copy ?? "copy";

  const [routeEditorOpen, setRouteEditorOpen] = useState(false);
  const [routeEditorMode, setRouteEditorMode] = useState(createMode);
  const [routeSourceProductCode, setRouteSourceProductCode] = useState("");
  const [routeTargetProductCode, setRouteTargetProductCode] = useState("");
  const [routeStepsDraft, setRouteStepsDraft] = useState([createEmptyRouteStep()]);
  const [routeSaving, setRouteSaving] = useState(false);
  const [routeMessage, setRouteMessage] = useState("");
  const [routeError, setRouteError] = useState("");

  function openCreateRouteEditor() {
    setRouteEditorOpen(true);
    setRouteEditorMode(createMode);
    setRouteSourceProductCode("");
    setRouteTargetProductCode("");
    setRouteStepsDraft([createEmptyRouteStep()]);
    setRouteMessage("");
    setRouteError("");
  }

  function openEditRouteEditor(productCode) {
    const normalized = String(productCode || "").trim().toUpperCase();
    setRouteEditorOpen(true);
    setRouteEditorMode(editMode);
    setRouteSourceProductCode(normalized);
    setRouteTargetProductCode(normalized);
    setRouteStepsDraft(
      buildRouteStepsDraft(routeStepsByProduct, normalized, normalizeDependencyType, createEmptyRouteStep),
    );
    setRouteMessage("");
    setRouteError("");
  }

  function openCopyRouteEditor(productCode) {
    const normalized = String(productCode || "").trim().toUpperCase();
    setRouteEditorOpen(true);
    setRouteEditorMode(copyMode);
    setRouteSourceProductCode(normalized);
    setRouteTargetProductCode("");
    setRouteStepsDraft(
      buildRouteStepsDraft(routeStepsByProduct, normalized, normalizeDependencyType, createEmptyRouteStep),
    );
    setRouteMessage("");
    setRouteError("");
  }

  function closeRouteEditor() {
    setRouteEditorOpen(false);
    setRouteSaving(false);
    setRouteError("");
  }

  function updateRouteStep(index, field, value) {
    setRouteStepsDraft((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
  }

  function addRouteStep() {
    setRouteStepsDraft((prev) => [...prev, createEmptyRouteStep()]);
  }

  function removeRouteStep(index) {
    setRouteStepsDraft((prev) => {
      const next = prev.filter((_, i) => i !== index);
      return next.length > 0 ? next : [createEmptyRouteStep()];
    });
  }

  async function submitRouteEditor() {
    setRouteMessage("");
    setRouteError("");
    setRouteSaving(true);
    try {
      await saveMasterdataRouteByMode({
        mode: routeEditorMode,
        sourceProductCode: routeSourceProductCode,
        targetProductCode: routeTargetProductCode,
        steps: normalizedRouteStepPayload(routeStepsDraft, normalizeDependencyType),
      });
      await refreshMasterdata();
      setRouteMessage("工艺路线已保存。");
      setRouteEditorOpen(false);
    } catch (error) {
      setRouteError(error.message || "工艺路线保存失败");
    } finally {
      setRouteSaving(false);
    }
  }

  async function deleteRoute(productCode) {
    const normalized = String(productCode || "").trim().toUpperCase();
    if (!normalized) {
      return;
    }
    const confirmed = window.confirm(`确认删除产品 ${normalized} 的工艺路线吗？`);
    if (!confirmed) {
      return;
    }
    setRouteMessage("");
    setRouteError("");
    setRouteSaving(true);
    try {
      await deleteMasterdataRouteByProductCode(normalized);
      await refreshMasterdata();
      setRouteMessage("工艺路线已删除。");
    } catch (error) {
      setRouteError(error.message || "删除失败");
    } finally {
      setRouteSaving(false);
    }
  }

  return {
    routeEditorOpen,
    routeEditorMode,
    routeSourceProductCode,
    routeTargetProductCode,
    setRouteTargetProductCode,
    routeStepsDraft,
    routeSaving,
    routeMessage,
    routeError,
    openCreateRouteEditor,
    openEditRouteEditor,
    openCopyRouteEditor,
    closeRouteEditor,
    updateRouteStep,
    addRouteStep,
    removeRouteStep,
    submitRouteEditor,
    deleteRoute,
  };
}

