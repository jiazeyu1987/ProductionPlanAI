import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";

const ROUTE_TAB = "route";
const EQUIPMENT_TAB = "equipment";
const CONFIG_TAB = "config";
const CONFIG_SUB_WINDOW = "window";
const CONFIG_SUB_PROCESS = "process";
const CONFIG_SUB_TOPOLOGY = "topology";
const CONFIG_SUB_LEADER = "leader";
const CONFIG_SUB_RESOURCE = "resource";
const CONFIG_SUB_CARRYOVER = "carryover";
const CONFIG_SUB_MATERIAL = "material";
const ROUTE_EDITOR_CREATE = "create";
const ROUTE_EDITOR_EDIT = "edit";
const ROUTE_EDITOR_COPY = "copy";
const SHIFT_OPTIONS = [
  { code: "DAY", name: "白班" },
  { code: "NIGHT", name: "夜班" }
];

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function routeGroupKey(row) {
  return [
    row.route_no || "",
    row.route_name_cn || "",
    row.product_code || "",
    row.product_name_cn || ""
  ].join("|");
}

function normalizeShiftCode(shiftCode) {
  const normalized = String(shiftCode || "").trim().toUpperCase();
  if (normalized === "D") {
    return "DAY";
  }
  if (normalized === "N") {
    return "NIGHT";
  }
  return normalized;
}

function shiftSortIndex(shiftCode) {
  const normalized = normalizeShiftCode(shiftCode);
  if (normalized === "DAY") {
    return 0;
  }
  if (normalized === "NIGHT") {
    return 1;
  }
  return 9;
}

function normalizeProcessCode(value) {
  return String(value || "").trim().toUpperCase();
}

function normalizeProductCode(value) {
  return String(value || "").trim().toUpperCase();
}

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

function normalizeDependencyType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized === "SS" ? "SS" : "FS";
}

function createEmptyRouteStep() {
  return {
    process_code: "",
    dependency_type: "FS"
  };
}

function parseConfigResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  const processConfigs = Array.isArray(body.process_configs) ? body.process_configs : [];
  const lineTopology = Array.isArray(body.line_topology) ? body.line_topology : [];
  const sectionLeaderBindings = Array.isArray(body.section_leader_bindings) ? body.section_leader_bindings : [];
  const resourcePool = Array.isArray(body.resource_pool) ? body.resource_pool : [];
  const initialCarryoverOccupancy = Array.isArray(body.initial_carryover_occupancy) ? body.initial_carryover_occupancy : [];
  const materialAvailability = Array.isArray(body.material_availability) ? body.material_availability : [];
  return {
    processConfigs,
    lineTopology,
    sectionLeaderBindings,
    resourcePool,
    initialCarryoverOccupancy,
    materialAvailability,
    horizonStartDate: body.horizon_start_date || "",
    horizonDays: toInt(body.horizon_days, 0),
    shiftsPerDay: toInt(body.shifts_per_day, 2)
  };
}

function firstDateFromRows(rows) {
  const dates = unique(rows.map((row) => String(row.calendar_date || "").trim()).filter(Boolean)).sort();
  return dates.length > 0 ? dates[0] : "";
}

function firstDateFromConfig(configData) {
  return firstDateFromRows([
    ...(configData.resourcePool || []),
    ...(configData.initialCarryoverOccupancy || []),
    ...(configData.materialAvailability || [])
  ]);
}

export default function MasterdataPage() {
  const [routes, setRoutes] = useState([]);
  const [equipments, setEquipments] = useState([]);
  const [activeTab, setActiveTab] = useState(ROUTE_TAB);
  const [searchParams, setSearchParams] = useSearchParams();
  const processCodeFilter = (searchParams.get("process_code") || "").trim();
  const productCodeFilter = (searchParams.get("product_code") || "").trim();

  const [configData, setConfigData] = useState({
    processConfigs: [],
    lineTopology: [],
    sectionLeaderBindings: [],
    resourcePool: [],
    initialCarryoverOccupancy: [],
    materialAvailability: [],
    horizonStartDate: "",
    horizonDays: 0,
    shiftsPerDay: 2
  });
  const [selectedConfigDate, setSelectedConfigDate] = useState("");
  const [selectedCarryoverProcess, setSelectedCarryoverProcess] = useState("");
  const [newResourceShift, setNewResourceShift] = useState("DAY");
  const [newResourceProcess, setNewResourceProcess] = useState("");
  const [newCarryoverShift, setNewCarryoverShift] = useState("DAY");
  const [newMaterialShift, setNewMaterialShift] = useState("DAY");
  const [newMaterialProcess, setNewMaterialProcess] = useState("");
  const [newMaterialProduct, setNewMaterialProduct] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [saveError, setSaveError] = useState("");
  const [activeConfigSubTab, setActiveConfigSubTab] = useState(CONFIG_SUB_WINDOW);
  const [routeEditorOpen, setRouteEditorOpen] = useState(false);
  const [routeEditorMode, setRouteEditorMode] = useState(ROUTE_EDITOR_CREATE);
  const [routeSourceProductCode, setRouteSourceProductCode] = useState("");
  const [routeTargetProductCode, setRouteTargetProductCode] = useState("");
  const [routeStepsDraft, setRouteStepsDraft] = useState([createEmptyRouteStep()]);
  const [routeSaving, setRouteSaving] = useState(false);
  const [routeMessage, setRouteMessage] = useState("");
  const [routeError, setRouteError] = useState("");

  async function refreshMasterdata() {
    const [routeRes, equipmentRes, configRes] = await Promise.all([
      loadList("/v1/mes/process-routes"),
      loadList("/v1/mes/equipments"),
      loadList("/internal/v1/internal/masterdata/config").catch(() => null)
    ]);
    setRoutes(routeRes.items ?? []);
    setEquipments(equipmentRes.items ?? []);
    if (configRes) {
      const parsed = parseConfigResponse(configRes);
      setConfigData(parsed);
      setSelectedConfigDate((prev) => prev || firstDateFromConfig(parsed));
    }
  }

  useEffect(() => {
    refreshMasterdata().catch(() => {});
  }, []);

  useEffect(() => {
    if (processCodeFilter || productCodeFilter) {
      setActiveTab(ROUTE_TAB);
    }
  }, [processCodeFilter, productCodeFilter]);

  const filteredRoutes = useMemo(() => {
    return routes
      .filter((row) => {
        const matchProcess = processCodeFilter
          ? String(row.process_code || "").toUpperCase() === processCodeFilter.toUpperCase()
          : true;
        const matchProduct = productCodeFilter
          ? String(row.product_code || "").toUpperCase() === productCodeFilter.toUpperCase()
          : true;
        return matchProcess && matchProduct;
      })
      .sort((a, b) => {
        const byRoute = String(a.route_no || "").localeCompare(String(b.route_no || ""), "zh-Hans-CN");
        if (byRoute !== 0) {
          return byRoute;
        }
        const byProduct = String(a.product_code || "").localeCompare(String(b.product_code || ""), "zh-Hans-CN");
        if (byProduct !== 0) {
          return byProduct;
        }
        return toNumber(a.sequence_no, 0) - toNumber(b.sequence_no, 0);
      });
  }, [routes, processCodeFilter, productCodeFilter]);

  const groupedRouteRows = useMemo(() => {
    if (filteredRoutes.length === 0) {
      return [];
    }

    const result = [];
    let index = 0;

    while (index < filteredRoutes.length) {
      const start = filteredRoutes[index];
      const groupKey = routeGroupKey(start);
      let span = 1;

      while (index + span < filteredRoutes.length && routeGroupKey(filteredRoutes[index + span]) === groupKey) {
        span += 1;
      }

      for (let offset = 0; offset < span; offset += 1) {
        result.push({
          ...filteredRoutes[index + offset],
          _routeProductRowSpan: offset === 0 ? span : 0
        });
      }

      index += span;
    }

    return result;
  }, [filteredRoutes]);

  const routeStepsByProduct = useMemo(() => {
    const map = new Map();
    for (const row of routes) {
      const productCode = String(row.product_code || "").trim().toUpperCase();
      if (!productCode) {
        continue;
      }
      const list = map.get(productCode) || [];
      list.push({
        process_code: String(row.process_code || "").trim().toUpperCase(),
        dependency_type: normalizeDependencyType(row.dependency_type),
        sequence_no: toNumber(row.sequence_no, list.length + 1)
      });
      map.set(productCode, list);
    }
    for (const [productCode, list] of map.entries()) {
      list.sort((a, b) => toNumber(a.sequence_no, 0) - toNumber(b.sequence_no, 0));
      map.set(productCode, list);
    }
    return map;
  }, [routes]);

  const processOptions = useMemo(() => {
    const byCode = new Map();
    for (const row of configData.processConfigs) {
      const processCode = String(row.process_code || "").trim().toUpperCase();
      if (!processCode) {
        continue;
      }
      byCode.set(processCode, row.process_name_cn || row.process_name || processCode);
    }
    for (const row of routes) {
      const processCode = String(row.process_code || "").trim().toUpperCase();
      if (!processCode || byCode.has(processCode)) {
        continue;
      }
      byCode.set(processCode, row.process_name_cn || row.process_name || processCode);
    }
    return [...byCode.entries()]
      .map(([code, name]) => ({ code, name: String(name || code).trim() || code }))
      .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-Hans-CN"));
  }, [configData.processConfigs, routes]);

  const processNameByCode = useMemo(() => {
    const map = new Map();
    for (const option of processOptions) {
      map.set(option.code, option.name);
    }
    return map;
  }, [processOptions]);

  const productOptions = useMemo(() => {
    const byCode = new Map();
    for (const row of configData.materialAvailability) {
      const productCode = normalizeProductCode(row.product_code);
      if (!productCode) {
        continue;
      }
      byCode.set(productCode, row.product_name_cn || row.product_name || productCode);
    }
    for (const row of routes) {
      const productCode = normalizeProductCode(row.product_code);
      if (!productCode || byCode.has(productCode)) {
        continue;
      }
      byCode.set(productCode, row.product_name_cn || row.product_name || productCode);
    }
    return [...byCode.entries()]
      .map(([code, name]) => ({ code, name: String(name || code).trim() || code }))
      .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-Hans-CN"));
  }, [configData.materialAvailability, routes]);

  const lineTopologyRows = useMemo(() => {
    return [...(configData.lineTopology || [])].sort((a, b) => {
      const byWorkshop = String(a.workshop_code || "").localeCompare(String(b.workshop_code || ""), "zh-Hans-CN");
      if (byWorkshop !== 0) {
        return byWorkshop;
      }
      const byLine = String(a.line_code || "").localeCompare(String(b.line_code || ""), "zh-Hans-CN");
      if (byLine !== 0) {
        return byLine;
      }
      return String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN");
    });
  }, [configData.lineTopology]);

  const lineOptions = useMemo(() => {
    const byLine = new Map();
    for (const row of configData.lineTopology || []) {
      const lineCode = String(row.line_code || "").trim().toUpperCase();
      if (!lineCode) {
        continue;
      }
      const existing = byLine.get(lineCode);
      if (existing) {
        continue;
      }
      byLine.set(lineCode, {
        lineCode,
        lineName: String(row.line_name || lineCode).trim() || lineCode,
        workshopCode: String(row.workshop_code || "").trim().toUpperCase()
      });
    }
    return [...byLine.values()].sort((a, b) => String(a.lineCode).localeCompare(String(b.lineCode), "zh-Hans-CN"));
  }, [configData.lineTopology]);

  const sectionLeaderRows = useMemo(() => {
    return [...(configData.sectionLeaderBindings || [])].sort((a, b) => {
      const byLeader = String(a.leader_id || "").localeCompare(String(b.leader_id || ""), "zh-Hans-CN");
      if (byLeader !== 0) {
        return byLeader;
      }
      return String(a.line_code || "").localeCompare(String(b.line_code || ""), "zh-Hans-CN");
    });
  }, [configData.sectionLeaderBindings]);

  const processDetails = useMemo(() => {
    if (!processCodeFilter) {
      return null;
    }
    const rows = routes.filter((row) => String(row.process_code || "").toUpperCase() === processCodeFilter.toUpperCase());
    if (rows.length === 0) {
      return null;
    }
    return {
      processCode: rows[0].process_code,
      processName: rows[0].process_name_cn || rows[0].process_code || "-",
      routeCount: rows.length,
      products: unique(rows.map((row) => row.product_name_cn || row.product_code)).join("、") || "-"
    };
  }, [routes, processCodeFilter]);

  const productDetails = useMemo(() => {
    if (!productCodeFilter) {
      return null;
    }
    const rows = routes.filter((row) => String(row.product_code || "").toUpperCase() === productCodeFilter.toUpperCase());
    if (rows.length === 0) {
      return null;
    }
    const orderedRows = [...rows].sort((a, b) => toNumber(a.sequence_no, 0) - toNumber(b.sequence_no, 0));
    return {
      productCode: rows[0].product_code || "-",
      productName: rows[0].product_name_cn || rows[0].product_code || "-",
      routeNo: rows[0].route_no || "-",
      routeName: rows[0].route_name_cn || rows[0].route_no || "-",
      stepCount: orderedRows.length,
      steps: orderedRows.map((row) => row.process_name_cn || row.process_code || "-").join(" -> ")
    };
  }, [routes, productCodeFilter]);

  const configDates = useMemo(() => {
    const dates = unique(
      [
        ...configData.resourcePool.map((row) => String(row.calendar_date || "").trim()),
        ...configData.initialCarryoverOccupancy.map((row) => String(row.calendar_date || "").trim()),
        ...configData.materialAvailability.map((row) => String(row.calendar_date || "").trim())
      ].filter(Boolean)
    ).sort();
    if (dates.length > 0) {
      return dates;
    }
    return configData.horizonStartDate ? [configData.horizonStartDate] : [];
  }, [configData.resourcePool, configData.initialCarryoverOccupancy, configData.materialAvailability, configData.horizonStartDate]);

  useEffect(() => {
    if (configDates.length === 0) {
      if (selectedConfigDate) {
        setSelectedConfigDate("");
      }
      return;
    }
    if (!selectedConfigDate || !configDates.includes(selectedConfigDate)) {
      setSelectedConfigDate(configDates[0]);
    }
  }, [configDates, selectedConfigDate]);

  const carryoverProcessOptions = useMemo(() => {
    const date = selectedConfigDate || "";
    const byProcess = new Map();

    function collect(row) {
      const rowDate = String(row.calendar_date || "").trim();
      if (date && rowDate && rowDate !== date) {
        return;
      }
      const processCode = String(row.process_code || "").trim().toUpperCase();
      if (!processCode) {
        return;
      }
      const processName = String(row.process_name_cn || row.process_name || processCode).trim() || processCode;
      const existing = byProcess.get(processCode);
      if (!existing || existing === processCode) {
        byProcess.set(processCode, processName);
      }
    }

    configData.resourcePool.forEach(collect);
    configData.initialCarryoverOccupancy.forEach(collect);

    if (byProcess.size === 0) {
      for (const row of configData.processConfigs) {
        const processCode = String(row.process_code || "").trim().toUpperCase();
        if (!processCode) {
          continue;
        }
        const processName = String(row.process_name_cn || row.process_name || processCode).trim() || processCode;
        byProcess.set(processCode, processName);
      }
    }

    return [...byProcess.entries()]
      .map(([code, name]) => ({ code, name }))
      .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-Hans-CN"));
  }, [selectedConfigDate, configData.resourcePool, configData.initialCarryoverOccupancy, configData.processConfigs]);

  useEffect(() => {
    if (carryoverProcessOptions.length === 0) {
      if (selectedCarryoverProcess) {
        setSelectedCarryoverProcess("");
      }
      return;
    }
    const exists = carryoverProcessOptions.some((item) => item.code === selectedCarryoverProcess);
    if (!exists) {
      setSelectedCarryoverProcess(carryoverProcessOptions[0].code);
    }
  }, [carryoverProcessOptions, selectedCarryoverProcess]);

  useEffect(() => {
    if (processOptions.length === 0) {
      if (newResourceProcess) {
        setNewResourceProcess("");
      }
      return;
    }
    if (!processOptions.some((item) => item.code === newResourceProcess)) {
      setNewResourceProcess(processOptions[0].code);
    }
  }, [processOptions, newResourceProcess]);

  useEffect(() => {
    if (processOptions.length === 0) {
      if (newMaterialProcess) {
        setNewMaterialProcess("");
      }
      return;
    }
    if (!processOptions.some((item) => item.code === newMaterialProcess)) {
      setNewMaterialProcess(processOptions[0].code);
    }
  }, [processOptions, newMaterialProcess]);

  useEffect(() => {
    if (productOptions.length === 0) {
      if (newMaterialProduct) {
        setNewMaterialProduct("");
      }
      return;
    }
    if (!productOptions.some((item) => item.code === newMaterialProduct)) {
      setNewMaterialProduct(productOptions[0].code);
    }
  }, [productOptions, newMaterialProduct]);

  const resourceRowsForDate = useMemo(() => {
    const date = selectedConfigDate || "";
    return configData.resourcePool
      .filter((row) => String(row.calendar_date || "").trim() === date)
      .slice()
      .sort((a, b) => {
        const byShift = shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code);
        if (byShift !== 0) {
          return byShift;
        }
        return String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN");
      });
  }, [configData.resourcePool, selectedConfigDate]);

  const materialRowsForDate = useMemo(() => {
    const date = selectedConfigDate || "";
    return configData.materialAvailability
      .filter((row) => String(row.calendar_date || "").trim() === date)
      .slice()
      .sort((a, b) => {
        const byShift = shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code);
        if (byShift !== 0) {
          return byShift;
        }
        const byProduct = String(a.product_code || "").localeCompare(String(b.product_code || ""), "zh-Hans-CN");
        if (byProduct !== 0) {
          return byProduct;
        }
        return String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN");
      });
  }, [configData.materialAvailability, selectedConfigDate]);

  const carryoverRowsForDate = useMemo(() => {
    const date = selectedConfigDate || "";
    const selectedProcessCode = String(selectedCarryoverProcess || "").trim().toUpperCase();
    if (!date || !selectedProcessCode) {
      return [];
    }

    return configData.initialCarryoverOccupancy
      .filter((row) => {
        const rowDate = String(row.calendar_date || "").trim();
        const processCode = normalizeProcessCode(row.process_code);
        return rowDate === date && processCode === selectedProcessCode;
      })
      .map((row) => ({
        ...row,
        calendar_date: String(row.calendar_date || "").trim(),
        shift_code: normalizeShiftCode(row.shift_code),
        shift_name_cn: row.shift_name_cn || row.shift_name || "-",
        process_code: normalizeProcessCode(row.process_code),
        process_name_cn: row.process_name_cn || row.process_name || processNameByCode.get(selectedProcessCode) || selectedProcessCode,
        occupied_workers: toInt(row.occupied_workers, 0),
        occupied_machines: toInt(row.occupied_machines, 0)
      }))
      .sort((a, b) => {
        const byShift = shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code);
        if (byShift !== 0) {
          return byShift;
        }
        return String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN");
      });
  }, [configData.initialCarryoverOccupancy, selectedConfigDate, selectedCarryoverProcess, processNameByCode]);

  const capacityByDateShiftProcess = useMemo(() => {
    const map = new Map();
    for (const row of configData.resourcePool) {
      const key = [
        String(row.calendar_date || "").trim(),
        normalizeShiftCode(row.shift_code),
        String(row.process_code || "").trim().toUpperCase()
      ].join("#");
      map.set(key, {
        workers: toInt(row.workers_available, 0),
        machines: toInt(row.machines_available, 0)
      });
    }
    return map;
  }, [configData.resourcePool]);

  function buildRouteStepsDraft(productCode) {
    const rows = routeStepsByProduct.get(String(productCode || "").trim().toUpperCase()) || [];
    if (rows.length === 0) {
      return [createEmptyRouteStep()];
    }
    return rows.map((row) => ({
      process_code: String(row.process_code || "").trim().toUpperCase(),
      dependency_type: normalizeDependencyType(row.dependency_type)
    }));
  }

  function openCreateRouteEditor() {
    setRouteEditorOpen(true);
    setRouteEditorMode(ROUTE_EDITOR_CREATE);
    setRouteSourceProductCode("");
    setRouteTargetProductCode("");
    setRouteStepsDraft([createEmptyRouteStep()]);
    setRouteMessage("");
    setRouteError("");
  }

  function openEditRouteEditor(productCode) {
    const normalized = String(productCode || "").trim().toUpperCase();
    setRouteEditorOpen(true);
    setRouteEditorMode(ROUTE_EDITOR_EDIT);
    setRouteSourceProductCode(normalized);
    setRouteTargetProductCode(normalized);
    setRouteStepsDraft(buildRouteStepsDraft(normalized));
    setRouteMessage("");
    setRouteError("");
  }

  function openCopyRouteEditor(productCode) {
    const normalized = String(productCode || "").trim().toUpperCase();
    setRouteEditorOpen(true);
    setRouteEditorMode(ROUTE_EDITOR_COPY);
    setRouteSourceProductCode(normalized);
    setRouteTargetProductCode("");
    setRouteStepsDraft(buildRouteStepsDraft(normalized));
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

  function normalizedRouteStepPayload() {
    return routeStepsDraft
      .map((row, index) => ({
        process_code: String(row.process_code || "").trim().toUpperCase(),
        dependency_type: normalizeDependencyType(row.dependency_type),
        sequence_no: index + 1
      }))
      .filter((row) => row.process_code);
  }

  async function submitRouteEditor() {
    setRouteMessage("");
    setRouteError("");
    setRouteSaving(true);
    try {
      const targetProductCode = String(routeTargetProductCode || "").trim().toUpperCase();
      const sourceProductCode = String(routeSourceProductCode || "").trim().toUpperCase();
      const steps = normalizedRouteStepPayload();
      if ((routeEditorMode === ROUTE_EDITOR_CREATE || routeEditorMode === ROUTE_EDITOR_COPY) && !targetProductCode) {
        throw new Error("请先填写目标产品编码。");
      }
      if ((routeEditorMode === ROUTE_EDITOR_EDIT || routeEditorMode === ROUTE_EDITOR_COPY) && !sourceProductCode) {
        throw new Error("请先选择来源产品编码。");
      }
      if (steps.length === 0) {
        throw new Error("至少需要配置一个工序步骤。");
      }

      if (routeEditorMode === ROUTE_EDITOR_CREATE) {
        await postContract("/internal/v1/internal/masterdata/routes/create", {
          product_code: targetProductCode,
          steps
        });
      } else if (routeEditorMode === ROUTE_EDITOR_EDIT) {
        await postContract("/internal/v1/internal/masterdata/routes/update", {
          product_code: sourceProductCode,
          steps
        });
      } else if (routeEditorMode === ROUTE_EDITOR_COPY) {
        await postContract("/internal/v1/internal/masterdata/routes/copy", {
          source_product_code: sourceProductCode,
          target_product_code: targetProductCode,
          steps
        });
      } else {
        throw new Error("未知的工艺路线操作。");
      }

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
      await postContract("/internal/v1/internal/masterdata/routes/delete", {
        product_code: normalized
      });
      await refreshMasterdata();
      setRouteMessage("工艺路线已删除。");
    } catch (error) {
      setRouteError(error.message || "删除失败");
    } finally {
      setRouteSaving(false);
    }
  }

  function updateProcessConfig(index, field, value) {
    setConfigData((prev) => {
      if (index < 0 || index >= prev.processConfigs.length) {
        return prev;
      }
      const nextProcessConfigs = prev.processConfigs.map((row, rowIndex) =>
        rowIndex === index
          ? {
            ...row,
            [field]: field === "process_code" ? normalizeProcessCode(value) : value
          }
          : row
      );
      if (field !== "process_code") {
        return {
          ...prev,
          processConfigs: nextProcessConfigs
        };
      }

      const previousCode = normalizeProcessCode(prev.processConfigs[index]?.process_code);
      const nextCode = normalizeProcessCode(value);
      if (!previousCode || !nextCode || previousCode === nextCode) {
        return {
          ...prev,
          processConfigs: nextProcessConfigs
        };
      }

      function replaceProcessCode(row) {
        const processCode = normalizeProcessCode(row.process_code);
        return processCode === previousCode ? { ...row, process_code: nextCode } : row;
      }

      return {
        ...prev,
        processConfigs: nextProcessConfigs,
        lineTopology: prev.lineTopology.map(replaceProcessCode),
        resourcePool: prev.resourcePool.map(replaceProcessCode),
        initialCarryoverOccupancy: prev.initialCarryoverOccupancy.map(replaceProcessCode),
        materialAvailability: prev.materialAvailability.map(replaceProcessCode)
      };
    });
  }

  function addProcessConfigRow() {
    setConfigData((prev) => {
      const existingCodes = new Set(prev.processConfigs.map((row) => normalizeProcessCode(row.process_code)).filter(Boolean));
      const candidate = processOptions.find((option) => !existingCodes.has(option.code));
      let nextCode = candidate?.code || "";
      if (!nextCode) {
        let serial = prev.processConfigs.length + 1;
        do {
          nextCode = `PROC_NEW_${serial}`;
          serial += 1;
        } while (existingCodes.has(nextCode));
      }
      return {
        ...prev,
        processConfigs: [
          ...prev.processConfigs,
          {
            process_code: nextCode,
            process_name_cn: processNameByCode.get(nextCode) || nextCode,
            capacity_per_shift: 1,
            required_workers: 1,
            required_machines: 1
          }
        ]
      };
    });
  }

  function removeProcessConfigRow(index) {
    const confirmed = window.confirm("确认删除该工序参数吗？");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => {
      if (index < 0 || index >= prev.processConfigs.length) {
        return prev;
      }
      const removedCode = normalizeProcessCode(prev.processConfigs[index]?.process_code);
      const nextProcessConfigs = prev.processConfigs.filter((_, rowIndex) => rowIndex !== index);
      if (!removedCode) {
        return {
          ...prev,
          processConfigs: nextProcessConfigs
        };
      }
      return {
        ...prev,
        processConfigs: nextProcessConfigs,
        lineTopology: prev.lineTopology.filter((row) => normalizeProcessCode(row.process_code) !== removedCode),
        resourcePool: prev.resourcePool.filter((row) => normalizeProcessCode(row.process_code) !== removedCode),
        initialCarryoverOccupancy: prev.initialCarryoverOccupancy.filter((row) => normalizeProcessCode(row.process_code) !== removedCode),
        materialAvailability: prev.materialAvailability.filter((row) => normalizeProcessCode(row.process_code) !== removedCode)
      };
    });
  }

  function updateLineTopologyRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      lineTopology: prev.lineTopology.map((row) => {
        const isMatch =
          String(row.workshop_code || "").trim().toUpperCase() === String(target.workshop_code || "").trim().toUpperCase()
          && String(row.line_code || "").trim().toUpperCase() === String(target.line_code || "").trim().toUpperCase()
          && String(row.process_code || "").trim().toUpperCase() === String(target.process_code || "").trim().toUpperCase();
        if (!isMatch) {
          return row;
        }
        const nextValue = field === "enabled_flag" ? toInt(value, 1) : value;
        if (field === "workshop_code" || field === "line_code" || field === "process_code") {
          return { ...row, [field]: String(nextValue || "").trim().toUpperCase() };
        }
        return { ...row, [field]: nextValue };
      })
    }));
  }

  function addLineTopologyRow() {
    const defaultProcess = processOptions[0]?.code || "";
    setConfigData((prev) => ({
      ...prev,
      lineTopology: [
        ...prev.lineTopology,
        {
          workshop_code: "WS-PRODUCTION",
          line_code: `LINE-NEW-${prev.lineTopology.length + 1}`,
          line_name: `新产线${prev.lineTopology.length + 1}`,
          process_code: defaultProcess,
          enabled_flag: 1
        }
      ]
    }));
  }

  function removeLineTopologyRow(target) {
    const confirmed = window.confirm("确认删除该产线-工序关系吗？");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => {
      const nextLineTopology = prev.lineTopology.filter((row) => {
        const isMatch =
          String(row.workshop_code || "").trim().toUpperCase() === String(target.workshop_code || "").trim().toUpperCase()
          && String(row.line_code || "").trim().toUpperCase() === String(target.line_code || "").trim().toUpperCase()
          && String(row.process_code || "").trim().toUpperCase() === String(target.process_code || "").trim().toUpperCase();
        return !isMatch;
      });
      const validLineCodes = new Set(nextLineTopology.map((row) => String(row.line_code || "").trim().toUpperCase()).filter(Boolean));
      const nextSectionLeaderBindings = prev.sectionLeaderBindings.filter((row) =>
        validLineCodes.has(String(row.line_code || "").trim().toUpperCase())
      );
      return {
        ...prev,
        lineTopology: nextLineTopology,
        sectionLeaderBindings: nextSectionLeaderBindings
      };
    });
  }

  function updateSectionLeaderRow(target, field, value) {
    const lineMeta = new Map(lineOptions.map((line) => [line.lineCode, line]));
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: prev.sectionLeaderBindings.map((row) => {
        const isMatch =
          String(row.leader_id || "").trim().toUpperCase() === String(target.leader_id || "").trim().toUpperCase()
          && String(row.line_code || "").trim().toUpperCase() === String(target.line_code || "").trim().toUpperCase();
        if (!isMatch) {
          return row;
        }
        if (field === "line_code") {
          const nextLineCode = String(value || "").trim().toUpperCase();
          const meta = lineMeta.get(nextLineCode);
          return {
            ...row,
            line_code: nextLineCode,
            line_name: meta?.lineName || nextLineCode,
            workshop_code: meta?.workshopCode || ""
          };
        }
        if (field === "leader_id") {
          return { ...row, leader_id: String(value || "").trim().toUpperCase() };
        }
        if (field === "active_flag") {
          return { ...row, active_flag: toInt(value, 1) };
        }
        return { ...row, [field]: value };
      })
    }));
  }

  function addSectionLeaderRow() {
    if (lineOptions.length === 0) {
      setSaveError("请先维护产线拓扑，再绑定工段长。");
      return;
    }
    const firstLine = lineOptions[0];
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: [
        ...prev.sectionLeaderBindings,
        {
          leader_id: `LEADER-NEW-${prev.sectionLeaderBindings.length + 1}`,
          leader_name: `工段长${prev.sectionLeaderBindings.length + 1}`,
          line_code: firstLine.lineCode,
          line_name: firstLine.lineName,
          workshop_code: firstLine.workshopCode,
          active_flag: 1
        }
      ]
    }));
  }

  function removeSectionLeaderRow(target) {
    const confirmed = window.confirm("确认删除该工段长与产线绑定吗？");
    if (!confirmed) {
      return;
    }
    setConfigData((prev) => ({
      ...prev,
      sectionLeaderBindings: prev.sectionLeaderBindings.filter((row) => {
        const isMatch =
          String(row.leader_id || "").trim().toUpperCase() === String(target.leader_id || "").trim().toUpperCase()
          && String(row.line_code || "").trim().toUpperCase() === String(target.line_code || "").trim().toUpperCase();
        return !isMatch;
      })
    }));
  }

  function resourceRowKey(row) {
    return [
      String(row.calendar_date || "").trim(),
      normalizeShiftCode(row.shift_code),
      normalizeProcessCode(row.process_code)
    ].join("#");
  }

  function carryoverRowKey(row) {
    return [
      String(row.calendar_date || "").trim(),
      normalizeShiftCode(row.shift_code),
      normalizeProcessCode(row.process_code)
    ].join("#");
  }

  function materialRowKey(row) {
    return [
      String(row.calendar_date || "").trim(),
      normalizeShiftCode(row.shift_code),
      normalizeProductCode(row.product_code),
      normalizeProcessCode(row.process_code)
    ].join("#");
  }

  function updateResourceRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      resourcePool: prev.resourcePool.map((row) => {
        const isMatch = resourceRowKey(row) === resourceRowKey(target);
        return isMatch ? { ...row, [field]: value } : row;
      })
    }));
  }

  function addResourceRow() {
    const date = String(selectedConfigDate || "").trim();
    const shiftCode = normalizeShiftCode(newResourceShift);
    const processCode = normalizeProcessCode(newResourceProcess);
    if (!date) {
      setSaveError("请先选择日期，再新增班次资源。");
      return;
    }
    if (!shiftCode || !processCode) {
      setSaveError("请先选择班次和工序，再新增班次资源。");
      return;
    }
    const targetKey = `${date}#${shiftCode}#${processCode}`;
    if (configData.resourcePool.some((row) => resourceRowKey(row) === targetKey)) {
      setSaveError("该日期/班次/工序的资源记录已存在。");
      return;
    }
    const processName = processNameByCode.get(processCode) || processCode;
    const processConfig = configData.processConfigs.find((row) => normalizeProcessCode(row.process_code) === processCode);
    const defaultWorkers = Math.max(0, toInt(processConfig?.required_workers, 1));
    const defaultMachines = Math.max(0, toInt(processConfig?.required_machines, 1));
    setSaveError("");
    setConfigData((prev) => ({
      ...prev,
      resourcePool: [
        ...prev.resourcePool,
        {
          calendar_date: date,
          shift_code: shiftCode,
          shift_name_cn: shiftCode === "DAY" ? "白班" : shiftCode === "NIGHT" ? "夜班" : shiftCode,
          process_code: processCode,
          process_name_cn: processName,
          open_flag: 1,
          workers_available: defaultWorkers,
          machines_available: defaultMachines
        }
      ]
    }));
  }

  function removeResourceRow(target) {
    const confirmed = window.confirm("确认删除该班次资源记录吗？");
    if (!confirmed) {
      return;
    }
    const targetKey = resourceRowKey(target);
    setConfigData((prev) => ({
      ...prev,
      resourcePool: prev.resourcePool.filter((row) => resourceRowKey(row) !== targetKey),
      initialCarryoverOccupancy: prev.initialCarryoverOccupancy.filter((row) => carryoverRowKey(row) !== targetKey)
    }));
  }

  function updateMaterialRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      materialAvailability: prev.materialAvailability.map((row) => {
        const isMatch = materialRowKey(row) === materialRowKey(target);
        return isMatch ? { ...row, [field]: value } : row;
      })
    }));
  }

  function addMaterialRow() {
    const date = String(selectedConfigDate || "").trim();
    const shiftCode = normalizeShiftCode(newMaterialShift);
    const processCode = normalizeProcessCode(newMaterialProcess);
    const productCode = normalizeProductCode(newMaterialProduct);
    if (!date) {
      setSaveError("请先选择日期，再新增物料可用量。");
      return;
    }
    if (!shiftCode || !processCode || !productCode) {
      setSaveError("请先选择班次、产品和工序，再新增物料可用量。");
      return;
    }
    const targetKey = `${date}#${shiftCode}#${productCode}#${processCode}`;
    if (configData.materialAvailability.some((row) => materialRowKey(row) === targetKey)) {
      setSaveError("该日期/班次/产品/工序的物料记录已存在。");
      return;
    }
    const processName = processNameByCode.get(processCode) || processCode;
    const productName = productOptions.find((option) => option.code === productCode)?.name || productCode;
    setSaveError("");
    setConfigData((prev) => ({
      ...prev,
      materialAvailability: [
        ...prev.materialAvailability,
        {
          calendar_date: date,
          shift_code: shiftCode,
          shift_name_cn: shiftCode === "DAY" ? "白班" : shiftCode === "NIGHT" ? "夜班" : shiftCode,
          product_code: productCode,
          product_name_cn: productName,
          process_code: processCode,
          process_name_cn: processName,
          available_qty: 0
        }
      ]
    }));
  }

  function removeMaterialRow(target) {
    const confirmed = window.confirm("确认删除该物料可用量记录吗？");
    if (!confirmed) {
      return;
    }
    const targetKey = materialRowKey(target);
    setConfigData((prev) => ({
      ...prev,
      materialAvailability: prev.materialAvailability.filter((row) => materialRowKey(row) !== targetKey)
    }));
  }

  function updateCarryoverRow(target, field, value) {
    setConfigData((prev) => {
      const targetDate = String(target.calendar_date || "").trim();
      const targetShiftCode = normalizeShiftCode(target.shift_code);
      const targetProcessCode = normalizeProcessCode(target.process_code);
      if (!targetDate || !targetShiftCode || !targetProcessCode) {
        return prev;
      }

      let matched = false;
      const nextRows = prev.initialCarryoverOccupancy.map((row) => {
        const isMatch = carryoverRowKey(row) === `${targetDate}#${targetShiftCode}#${targetProcessCode}`;
        if (!isMatch) {
          return row;
        }
        matched = true;
        return {
          ...row,
          calendar_date: targetDate,
          shift_code: targetShiftCode,
          process_code: targetProcessCode,
          [field]: value
        };
      });

      if (!matched) {
        nextRows.push({
          calendar_date: targetDate,
          shift_code: targetShiftCode,
          process_code: targetProcessCode,
          process_name_cn: target.process_name_cn || targetProcessCode,
          shift_name_cn: target.shift_name_cn || targetShiftCode,
          occupied_workers: field === "occupied_workers" ? value : toInt(target.occupied_workers, 0),
          occupied_machines: field === "occupied_machines" ? value : toInt(target.occupied_machines, 0)
        });
      }

      return {
        ...prev,
        initialCarryoverOccupancy: nextRows
      };
    });
  }

  function addCarryoverRow() {
    const date = String(selectedConfigDate || "").trim();
    const shiftCode = normalizeShiftCode(newCarryoverShift);
    const processCode = normalizeProcessCode(selectedCarryoverProcess);
    if (!date) {
      setSaveError("请先选择日期，再新增初始遗留占用。");
      return;
    }
    if (!shiftCode || !processCode) {
      setSaveError("请先选择工序与班次，再新增初始遗留占用。");
      return;
    }
    const targetKey = `${date}#${shiftCode}#${processCode}`;
    if (configData.initialCarryoverOccupancy.some((row) => carryoverRowKey(row) === targetKey)) {
      setSaveError("该日期/班次/工序的遗留占用记录已存在。");
      return;
    }
    const processName = processNameByCode.get(processCode) || processCode;
    setSaveError("");
    setConfigData((prev) => ({
      ...prev,
      initialCarryoverOccupancy: [
        ...prev.initialCarryoverOccupancy,
        {
          calendar_date: date,
          shift_code: shiftCode,
          shift_name_cn: shiftCode === "DAY" ? "白班" : shiftCode === "NIGHT" ? "夜班" : shiftCode,
          process_code: processCode,
          process_name_cn: processName,
          occupied_workers: 0,
          occupied_machines: 0
        }
      ]
    }));
  }

  function removeCarryoverRow(target) {
    const confirmed = window.confirm("确认删除该初始遗留占用记录吗？");
    if (!confirmed) {
      return;
    }
    const targetKey = carryoverRowKey(target);
    setConfigData((prev) => ({
      ...prev,
      initialCarryoverOccupancy: prev.initialCarryoverOccupancy.filter((row) => carryoverRowKey(row) !== targetKey)
    }));
  }

  function updatePlanningWindow(field, value) {
    setConfigData((prev) => ({
      ...prev,
      [field]: value
    }));
  }

  async function saveConfig() {
    setSaveMessage("");
    setSaveError("");
    setSaving(true);
    try {
      const normalizedProcessConfigs = [];
      const processSeen = new Set();
      for (const row of configData.processConfigs) {
        const processCode = normalizeProcessCode(row.process_code);
        if (!processCode) {
          throw new Error("工序参数中存在空工序编码，请先补全。");
        }
        if (processSeen.has(processCode)) {
          throw new Error(`工序参数存在重复工序编码：${processCode}`);
        }
        processSeen.add(processCode);

        const capacityPerShift = toNumber(row.capacity_per_shift, 0);
        const requiredWorkers = toInt(row.required_workers, 0);
        const requiredMachines = toInt(row.required_machines, 0);
        if (capacityPerShift <= 0) {
          throw new Error(`工序 ${processCode} 的每组产能/班必须大于 0。`);
        }
        if (requiredWorkers <= 0) {
          throw new Error(`工序 ${processCode} 的每组需人必须大于 0。`);
        }
        if (requiredMachines <= 0) {
          throw new Error(`工序 ${processCode} 的每组需机必须大于 0。`);
        }
        normalizedProcessConfigs.push({
          process_code: processCode,
          capacity_per_shift: capacityPerShift,
          required_workers: requiredWorkers,
          required_machines: requiredMachines
        });
      }
      if (normalizedProcessConfigs.length === 0) {
        throw new Error("至少保留一条工序参数。");
      }
      const validProcessCodes = new Set(normalizedProcessConfigs.map((row) => row.process_code));

      const normalizedResourcePool = [];
      const resourceSeen = new Set();
      for (const row of configData.resourcePool) {
        const date = String(row.calendar_date || "").trim();
        const shiftCode = normalizeShiftCode(row.shift_code);
        const processCode = normalizeProcessCode(row.process_code);
        if (!date || !shiftCode || !processCode) {
          throw new Error("班次资源中存在空日期/班次/工序，请先补全。");
        }
        if (!validProcessCodes.has(processCode)) {
          throw new Error(`班次资源引用了不存在的工序：${processCode}`);
        }
        const key = `${date}#${shiftCode}#${processCode}`;
        if (resourceSeen.has(key)) {
          throw new Error(`班次资源存在重复记录：${date} / ${shiftCode} / ${processCode}`);
        }
        resourceSeen.add(key);
        normalizedResourcePool.push({
          calendar_date: date,
          shift_code: shiftCode,
          process_code: processCode,
          workers_available: toInt(row.workers_available, 0),
          machines_available: toInt(row.machines_available, 0),
          open_flag: toInt(row.open_flag, 1) === 1 ? 1 : 0
        });
      }

      const normalizedCarryover = [];
      const carryoverSeen = new Set();
      for (const row of configData.initialCarryoverOccupancy) {
        const date = String(row.calendar_date || "").trim();
        const shiftCode = normalizeShiftCode(row.shift_code);
        const processCode = normalizeProcessCode(row.process_code);
        if (!date || !shiftCode || !processCode) {
          throw new Error("初始遗留占用中存在空日期/班次/工序，请先补全。");
        }
        if (!validProcessCodes.has(processCode)) {
          throw new Error(`初始遗留占用引用了不存在的工序：${processCode}`);
        }
        const key = `${date}#${shiftCode}#${processCode}`;
        if (carryoverSeen.has(key)) {
          throw new Error(`初始遗留占用存在重复记录：${date} / ${shiftCode} / ${processCode}`);
        }
        carryoverSeen.add(key);
        normalizedCarryover.push({
          calendar_date: date,
          shift_code: shiftCode,
          process_code: processCode,
          occupied_workers: toInt(row.occupied_workers, 0),
          occupied_machines: toInt(row.occupied_machines, 0)
        });
      }

      const normalizedMaterial = [];
      const materialSeen = new Set();
      for (const row of configData.materialAvailability) {
        const date = String(row.calendar_date || "").trim();
        const shiftCode = normalizeShiftCode(row.shift_code);
        const productCode = normalizeProductCode(row.product_code);
        const processCode = normalizeProcessCode(row.process_code);
        if (!date || !shiftCode || !productCode || !processCode) {
          throw new Error("物料可用量中存在空日期/班次/产品/工序，请先补全。");
        }
        if (!validProcessCodes.has(processCode)) {
          throw new Error(`物料可用量引用了不存在的工序：${processCode}`);
        }
        const key = `${date}#${shiftCode}#${productCode}#${processCode}`;
        if (materialSeen.has(key)) {
          throw new Error(`物料可用量存在重复记录：${date} / ${shiftCode} / ${productCode} / ${processCode}`);
        }
        materialSeen.add(key);
        normalizedMaterial.push({
          calendar_date: date,
          shift_code: shiftCode,
          product_code: productCode,
          process_code: processCode,
          available_qty: Math.max(0, toNumber(row.available_qty, 0))
        });
      }

      const payload = {
        horizon_start_date: configData.horizonStartDate || "",
        horizon_days: Math.max(1, toInt(configData.horizonDays, 1)),
        shifts_per_day: Math.max(1, Math.min(2, toInt(configData.shiftsPerDay, 2))),
        process_configs: normalizedProcessConfigs,
        line_topology: configData.lineTopology.map((row) => ({
          workshop_code: String(row.workshop_code || "").trim().toUpperCase(),
          line_code: String(row.line_code || "").trim().toUpperCase(),
          line_name: String(row.line_name || "").trim(),
          process_code: String(row.process_code || "").trim().toUpperCase(),
          enabled_flag: toInt(row.enabled_flag, 1) === 1 ? 1 : 0
        })),
        section_leader_bindings: configData.sectionLeaderBindings.map((row) => ({
          leader_id: String(row.leader_id || "").trim().toUpperCase(),
          leader_name: String(row.leader_name || "").trim(),
          line_code: String(row.line_code || "").trim().toUpperCase(),
          active_flag: toInt(row.active_flag, 1) === 1 ? 1 : 0
        })),
        resource_pool: normalizedResourcePool,
        initial_carryover_occupancy: normalizedCarryover,
        material_availability: normalizedMaterial
      };
      const res = await postContract("/internal/v1/internal/masterdata/config", payload);
      const parsed = parseConfigResponse(res);
      setConfigData(parsed);
      setSelectedConfigDate((prev) => {
        const allRows = [
          ...(parsed.resourcePool || []),
          ...(parsed.initialCarryoverOccupancy || []),
          ...(parsed.materialAvailability || [])
        ];
        if (prev && allRows.some((row) => String(row.calendar_date || "") === prev)) {
          return prev;
        }
        return firstDateFromConfig(parsed);
      });
      setSaveMessage("参数已保存，后续生成的新排产版本将使用最新配置。");
    } catch (error) {
      setSaveError(error.message || "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <h2>主数据管理</h2>
      <div className="report-tabs" role="tablist" aria-label="主数据页签">
        <button
          role="tab"
          aria-selected={activeTab === ROUTE_TAB}
          className={activeTab === ROUTE_TAB ? "active" : ""}
          onClick={() => setActiveTab(ROUTE_TAB)}
        >
          工艺路线
        </button>
        <button
          role="tab"
          aria-selected={activeTab === EQUIPMENT_TAB}
          className={activeTab === EQUIPMENT_TAB ? "active" : ""}
          onClick={() => setActiveTab(EQUIPMENT_TAB)}
        >
          设备能力
        </button>
        <button
          role="tab"
          aria-selected={activeTab === CONFIG_TAB}
          className={activeTab === CONFIG_TAB ? "active" : ""}
          onClick={() => setActiveTab(CONFIG_TAB)}
        >
          排产参数
        </button>
      </div>

      {activeTab === ROUTE_TAB ? (
        <>
          <div className="toolbar">
            <button disabled={routeSaving} onClick={openCreateRouteEditor}>
              新增路线
            </button>
            {processCodeFilter || productCodeFilter ? (
              <>
                {processCodeFilter ? <span className="hint">当前按工序定位：{processCodeFilter}</span> : null}
                {productCodeFilter ? <span className="hint">当前按产品定位：{productCodeFilter}</span> : null}
                <button
                  onClick={() => {
                    const next = new URLSearchParams(searchParams);
                    next.delete("process_code");
                    next.delete("product_code");
                    setSearchParams(next);
                  }}
                >
                  清除定位
                </button>
              </>
            ) : null}
          </div>
          {routeMessage ? <p className="notice">{routeMessage}</p> : null}
          {routeError ? <p className="error">{routeError}</p> : null}

          {routeEditorOpen ? (
            <div className="panel">
              <div className="panel-head">
                <h3>
                  {routeEditorMode === ROUTE_EDITOR_CREATE
                    ? "新增工艺路线"
                    : routeEditorMode === ROUTE_EDITOR_EDIT
                      ? "修改工艺路线"
                      : "复制工艺路线"}
                </h3>
                <div className="toolbar">
                  <button disabled={routeSaving} onClick={() => submitRouteEditor().catch(() => {})}>
                    {routeSaving ? "保存中..." : "保存路线"}
                  </button>
                  <button disabled={routeSaving} onClick={closeRouteEditor}>
                    取消
                  </button>
                </div>
              </div>
              <div className="table-wrap">
                <table>
                  <tbody>
                    {routeEditorMode !== ROUTE_EDITOR_CREATE ? (
                      <tr>
                        <th>来源产品</th>
                        <td>
                          <input value={routeSourceProductCode} disabled />
                        </td>
                      </tr>
                    ) : null}
                    <tr>
                      <th>目标产品</th>
                      <td>
                        <input
                          value={routeTargetProductCode}
                          disabled={routeEditorMode === ROUTE_EDITOR_EDIT}
                          placeholder="例如 PROD_CATH_NEW"
                          onChange={(e) => setRouteTargetProductCode(String(e.target.value || "").trim().toUpperCase())}
                        />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>顺序</th>
                      <th>工序</th>
                      <th>依赖类型</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {routeStepsDraft.map((row, index) => (
                      <tr key={`route-step-${index}`}>
                        <td>{index + 1}</td>
                        <td>
                          <select
                            value={row.process_code || ""}
                            onChange={(e) => updateRouteStep(index, "process_code", e.target.value)}
                          >
                            <option value="">请选择工序</option>
                            {processOptions.map((option) => (
                              <option key={option.code} value={option.code}>
                                {option.name}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td>
                          <select
                            value={normalizeDependencyType(row.dependency_type)}
                            onChange={(e) => updateRouteStep(index, "dependency_type", normalizeDependencyType(e.target.value))}
                          >
                            <option value="FS">FS（完成后开始）</option>
                            <option value="SS">SS（开始后开始）</option>
                          </select>
                        </td>
                        <td>
                          <button disabled={routeSaving} onClick={() => removeRouteStep(index)}>
                            删除步骤
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="toolbar">
                <button disabled={routeSaving} onClick={addRouteStep}>
                  新增步骤
                </button>
              </div>
            </div>
          ) : null}

          {processDetails ? (
            <div className="panel">
              <h3>工序详情</h3>
              <div className="table-wrap">
                <table>
                  <tbody>
                    <tr>
                      <th>工序编码</th>
                      <td>{processDetails.processCode}</td>
                      <th>工序名称</th>
                      <td>{processDetails.processName}</td>
                    </tr>
                    <tr>
                      <th>涉及产品</th>
                      <td>{processDetails.products}</td>
                      <th>工艺步骤数</th>
                      <td>{processDetails.routeCount}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {productDetails ? (
            <div className="panel">
              <h3>产品工艺路线详情</h3>
              <div className="table-wrap">
                <table>
                  <tbody>
                    <tr>
                      <th>产品编码</th>
                      <td>{productDetails.productCode}</td>
                      <th>产品名称</th>
                      <td>{productDetails.productName}</td>
                    </tr>
                    <tr>
                      <th>路线编码</th>
                      <td>{productDetails.routeNo}</td>
                      <th>路线名称</th>
                      <td>{productDetails.routeName}</td>
                    </tr>
                    <tr>
                      <th>工艺步骤数</th>
                      <td>{productDetails.stepCount}</td>
                      <th>工序列表</th>
                      <td>{productDetails.steps}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          <div className="panel">
            <h3>工艺路线</h3>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>路线</th>
                    <th>产品</th>
                    <th>工序</th>
                    <th>顺序</th>
                    <th>依赖</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {groupedRouteRows.length === 0 ? (
                    <tr>
                      <td colSpan={6}>暂无数据</td>
                    </tr>
                  ) : (
                    groupedRouteRows.map((row, rowIndex) => (
                      <tr
                        key={
                          row.id
                          || row.request_id
                          || `${row.route_no || "route"}-${row.process_code || "process"}-${row.sequence_no || rowIndex}-${rowIndex}`
                        }
                      >
                        {row._routeProductRowSpan > 0 ? (
                          <td rowSpan={row._routeProductRowSpan}>{row.route_name_cn || row.route_no || "-"}</td>
                        ) : null}
                        {row._routeProductRowSpan > 0 ? (
                          <td rowSpan={row._routeProductRowSpan}>
                            {row.product_code ? (
                              <Link className="table-link" to={`/masterdata?product_code=${encodeURIComponent(row.product_code)}`}>
                                {row.product_name_cn || row.product_code}
                              </Link>
                            ) : (
                              row.product_name_cn || "-"
                            )}
                          </td>
                        ) : null}
                        <td>
                          {row.process_code ? (
                            <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code)}`}>
                              {row.process_name_cn || row.process_code}
                            </Link>
                          ) : (
                            row.process_name_cn || "-"
                          )}
                        </td>
                        <td>{row.sequence_no ?? "-"}</td>
                        <td>{row.dependency_type_name_cn || row.dependency_type || "-"}</td>
                        {row._routeProductRowSpan > 0 ? (
                          <td rowSpan={row._routeProductRowSpan}>
                            <div className="toolbar">
                              <button disabled={routeSaving} onClick={() => openEditRouteEditor(row.product_code)}>
                                修改
                              </button>
                              <button disabled={routeSaving} onClick={() => openCopyRouteEditor(row.product_code)}>
                                复制
                              </button>
                              <button disabled={routeSaving} onClick={() => deleteRoute(row.product_code).catch(() => {})}>
                                删除
                              </button>
                            </div>
                          </td>
                        ) : null}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : null}

      {activeTab === EQUIPMENT_TAB ? (
        <div className="panel">
          <h3>设备能力</h3>
          <SimpleTable
            columns={[
              { key: "equipment_code", title: "设备编码" },
              {
                key: "process_code",
                title: "工序",
                render: (value, row) => row.process_name_cn || value || "-"
              },
              { key: "workshop_code", title: "车间" },
              { key: "line_code", title: "产线" },
              {
                key: "status",
                title: "状态",
                render: (value, row) => row.status_name_cn || value || "-"
              }
            ]}
            rows={equipments}
          />
        </div>
      ) : null}

      {activeTab === CONFIG_TAB ? (
        <>
          <div className="panel">
            <div className="panel-head">
              <h3>参数维护</h3>
              <button disabled={saving} onClick={() => saveConfig().catch(() => {})}>
                {saving ? "保存中..." : "保存参数"}
              </button>
            </div>
            <p className="hint">
              当前排程窗口：{configData.horizonStartDate || "-"}
              {configData.horizonDays ? ` 起，${configData.horizonDays} 天，${Math.max(1, Math.min(2, toInt(configData.shiftsPerDay, 2)))} 班/天` : ""}。
            </p>
            {saveMessage ? <p className="notice">{saveMessage}</p> : null}
            {saveError ? <p className="error">{saveError}</p> : null}
          </div>

          <div className="report-tabs" role="tablist" aria-label="排产参数子页签">
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_WINDOW}
              className={activeConfigSubTab === CONFIG_SUB_WINDOW ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_WINDOW)}
            >
              排程窗口
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_PROCESS}
              className={activeConfigSubTab === CONFIG_SUB_PROCESS ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_PROCESS)}
            >
              工序参数
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_TOPOLOGY}
              className={activeConfigSubTab === CONFIG_SUB_TOPOLOGY ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_TOPOLOGY)}
            >
              产线拓扑
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_LEADER}
              className={activeConfigSubTab === CONFIG_SUB_LEADER ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_LEADER)}
            >
              工段绑定
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_RESOURCE}
              className={activeConfigSubTab === CONFIG_SUB_RESOURCE ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_RESOURCE)}
            >
              班次资源
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_CARRYOVER}
              className={activeConfigSubTab === CONFIG_SUB_CARRYOVER ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_CARRYOVER)}
            >
              初始遗留占用
            </button>
            <button
              role="tab"
              aria-selected={activeConfigSubTab === CONFIG_SUB_MATERIAL}
              className={activeConfigSubTab === CONFIG_SUB_MATERIAL ? "active" : ""}
              onClick={() => setActiveConfigSubTab(CONFIG_SUB_MATERIAL)}
            >
              物料可用量
            </button>
          </div>

          {activeConfigSubTab === CONFIG_SUB_WINDOW ? (
            <div className="panel">
              <h3>排程窗口（起始日/天数/班次数）</h3>
              <div className="toolbar">
                <span className="hint">起始日</span>
                <input
                  type="date"
                  value={configData.horizonStartDate || ""}
                  onChange={(e) => updatePlanningWindow("horizonStartDate", e.target.value)}
                />
                <span className="hint">天数</span>
                <input
                  type="number"
                  min="1"
                  max="90"
                  step="1"
                  value={configData.horizonDays ?? ""}
                  onChange={(e) => updatePlanningWindow("horizonDays", e.target.value)}
                />
                <span className="hint">班次数</span>
                <select
                  value={Math.max(1, Math.min(2, toInt(configData.shiftsPerDay, 2)))}
                  onChange={(e) => updatePlanningWindow("shiftsPerDay", e.target.value)}
                >
                  <option value={1}>1 班</option>
                  <option value={2}>2 班</option>
                </select>
              </div>
              <p className="hint">保存后会影响后续新生成的排产版本。</p>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_PROCESS ? (
            <div className="panel">
              <div className="panel-head">
                <h3>工序参数（可编辑）</h3>
                <button onClick={addProcessConfigRow}>新增工序</button>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>工序编码</th>
                      <th>工序名称</th>
                      <th>每组产能/班</th>
                      <th>每组需人</th>
                      <th>每组需机</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {configData.processConfigs.length === 0 ? (
                      <tr>
                        <td colSpan={6}>暂无数据</td>
                      </tr>
                    ) : (
                      configData.processConfigs.map((row, index) => (
                        <tr key={`${row.process_code || "PROC"}-${index}`}>
                          <td>
                            <input
                              value={normalizeProcessCode(row.process_code)}
                              onChange={(e) => updateProcessConfig(index, "process_code", e.target.value)}
                            />
                          </td>
                          <td>
                            {normalizeProcessCode(row.process_code) ? (
                              <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(normalizeProcessCode(row.process_code))}`}>
                                {row.process_name_cn || processNameByCode.get(normalizeProcessCode(row.process_code)) || normalizeProcessCode(row.process_code)}
                              </Link>
                            ) : (
                              "-"
                            )}
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="0.01"
                              value={row.capacity_per_shift ?? ""}
                              onChange={(e) => updateProcessConfig(index, "capacity_per_shift", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.required_workers ?? ""}
                              onChange={(e) => updateProcessConfig(index, "required_workers", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.required_machines ?? ""}
                              onChange={(e) => updateProcessConfig(index, "required_machines", e.target.value)}
                            />
                          </td>
                          <td>
                            <button onClick={() => removeProcessConfigRow(index)}>删除</button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_TOPOLOGY ? (
            <div className="panel">
              <div className="panel-head">
                <h3>车间-产线-工序（可编辑）</h3>
                <button onClick={addLineTopologyRow}>新增产线工序</button>
              </div>
              <p className="hint">同一条产线可配置多个工序，表示该产线可承担这些工序任务。</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>车间编码</th>
                      <th>产线编码</th>
                      <th>产线名称</th>
                      <th>工序</th>
                      <th>启用</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lineTopologyRows.length === 0 ? (
                      <tr>
                        <td colSpan={6}>暂无数据</td>
                      </tr>
                    ) : (
                      lineTopologyRows.map((row) => (
                        <tr key={`${row.workshop_code}-${row.line_code}-${row.process_code}`}>
                          <td>
                            <input
                              value={row.workshop_code || ""}
                              onChange={(e) => updateLineTopologyRow(row, "workshop_code", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              value={row.line_code || ""}
                              onChange={(e) => updateLineTopologyRow(row, "line_code", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              value={row.line_name || ""}
                              onChange={(e) => updateLineTopologyRow(row, "line_name", e.target.value)}
                            />
                          </td>
                          <td>
                            <select
                              value={String(row.process_code || "").trim().toUpperCase()}
                              onChange={(e) => updateLineTopologyRow(row, "process_code", e.target.value)}
                            >
                              {processOptions.map((option) => (
                                <option key={option.code} value={option.code}>
                                  {option.name}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <select
                              value={toInt(row.enabled_flag, 1)}
                              onChange={(e) => updateLineTopologyRow(row, "enabled_flag", e.target.value)}
                            >
                              <option value={1}>启用</option>
                              <option value={0}>停用</option>
                            </select>
                          </td>
                          <td>
                            <button onClick={() => removeLineTopologyRow(row)}>删除</button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_LEADER ? (
            <div className="panel">
              <div className="panel-head">
                <h3>工段长-多产线绑定（可编辑）</h3>
                <button onClick={addSectionLeaderRow}>新增绑定</button>
              </div>
              <p className="hint">同一工段长可绑定多条产线，用于反映真实组织分工。</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>工段长ID</th>
                      <th>工段长姓名</th>
                      <th>车间</th>
                      <th>产线</th>
                      <th>启用</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sectionLeaderRows.length === 0 ? (
                      <tr>
                        <td colSpan={6}>暂无数据</td>
                      </tr>
                    ) : (
                      sectionLeaderRows.map((row) => (
                        <tr key={`${row.leader_id}-${row.line_code}`}>
                          <td>
                            <input
                              value={row.leader_id || ""}
                              onChange={(e) => updateSectionLeaderRow(row, "leader_id", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              value={row.leader_name || ""}
                              onChange={(e) => updateSectionLeaderRow(row, "leader_name", e.target.value)}
                            />
                          </td>
                          <td>{row.workshop_code || "-"}</td>
                          <td>
                            <select
                              value={String(row.line_code || "").trim().toUpperCase()}
                              onChange={(e) => updateSectionLeaderRow(row, "line_code", e.target.value)}
                            >
                              {lineOptions.map((line) => (
                                <option key={line.lineCode} value={line.lineCode}>
                                  {line.lineCode}（{line.lineName}）
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <select
                              value={toInt(row.active_flag, 1)}
                              onChange={(e) => updateSectionLeaderRow(row, "active_flag", e.target.value)}
                            >
                              <option value={1}>启用</option>
                              <option value={0}>停用</option>
                            </select>
                          </td>
                          <td>
                            <button onClick={() => removeSectionLeaderRow(row)}>删除</button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_RESOURCE ? (
            <div className="panel">
              <div className="panel-head">
                <h3>班次资源（可编辑）</h3>
                <div className="toolbar">
                  <span className="hint">日期</span>
                  <select value={selectedConfigDate} onChange={(e) => setSelectedConfigDate(e.target.value)}>
                    {configDates.map((date) => (
                      <option key={date} value={date}>
                        {date}
                      </option>
                    ))}
                  </select>
                  <span className="hint">班次</span>
                  <select value={newResourceShift} onChange={(e) => setNewResourceShift(e.target.value)}>
                    {SHIFT_OPTIONS.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                  <span className="hint">工序</span>
                  <select
                    value={newResourceProcess}
                    onChange={(e) => setNewResourceProcess(e.target.value)}
                    disabled={processOptions.length === 0}
                  >
                    {processOptions.length === 0 ? (
                      <option value="">暂无工序</option>
                    ) : (
                      processOptions.map((option) => (
                        <option key={option.code} value={option.code}>
                          {option.name}
                        </option>
                      ))
                    )}
                  </select>
                  <button onClick={addResourceRow} disabled={!selectedConfigDate || processOptions.length === 0}>
                    新增资源
                  </button>
                </div>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>班次</th>
                      <th>工序</th>
                      <th>班次开放</th>
                      <th>可用人力</th>
                      <th>可用设备</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resourceRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={6}>暂无数据</td>
                      </tr>
                    ) : (
                      resourceRowsForDate.map((row) => (
                        <tr key={`${row.calendar_date}-${row.shift_code}-${row.process_code}`}>
                          <td>{row.shift_name_cn || row.shift_code || "-"}</td>
                          <td>{row.process_name_cn || row.process_code || "-"}</td>
                          <td>
                            <select
                              value={toInt(row.open_flag, 1)}
                              onChange={(e) => updateResourceRow(row, "open_flag", toInt(e.target.value, 1))}
                            >
                              <option value={1}>开放</option>
                              <option value={0}>关闭</option>
                            </select>
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.workers_available ?? ""}
                              onChange={(e) => updateResourceRow(row, "workers_available", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.machines_available ?? ""}
                              onChange={(e) => updateResourceRow(row, "machines_available", e.target.value)}
                            />
                          </td>
                          <td>
                            <button onClick={() => removeResourceRow(row)}>删除</button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_CARRYOVER ? (
            <div className="panel">
              <div className="panel-head">
                <h3>初始遗留占用（可编辑）</h3>
                <div className="toolbar">
                  <span className="hint">日期</span>
                  <select value={selectedConfigDate} onChange={(e) => setSelectedConfigDate(e.target.value)}>
                    {configDates.map((date) => (
                      <option key={date} value={date}>
                        {date}
                      </option>
                    ))}
                  </select>
                  <span className="hint">工艺</span>
                  <select
                    value={selectedCarryoverProcess}
                    onChange={(e) => setSelectedCarryoverProcess(e.target.value)}
                    disabled={carryoverProcessOptions.length === 0}
                  >
                    {carryoverProcessOptions.length === 0 ? (
                      <option value="">暂无工艺</option>
                    ) : (
                      carryoverProcessOptions.map((item) => (
                        <option key={item.code} value={item.code}>
                          {item.name}
                        </option>
                      ))
                    )}
                  </select>
                  <span className="hint">班次</span>
                  <select value={newCarryoverShift} onChange={(e) => setNewCarryoverShift(e.target.value)}>
                    {SHIFT_OPTIONS.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                  <button
                    onClick={addCarryoverRow}
                    disabled={!selectedConfigDate || !selectedCarryoverProcess}
                  >
                    新增遗留
                  </button>
                </div>
              </div>
              <p className="hint">先选择工艺，再填写该工艺各班次的初始占用人力/设备，系统会自动扣减可用资源。</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>班次</th>
                      <th>工序</th>
                      <th>占用人力</th>
                      <th>占用设备</th>
                      <th>班次总人力</th>
                      <th>班次总设备</th>
                      <th>扣减后人力</th>
                      <th>扣减后设备</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {carryoverRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={9}>当前日期和工艺暂无遗留占用记录，请点击“新增遗留”。</td>
                      </tr>
                    ) : (
                      carryoverRowsForDate.map((row) => {
                        const key = [
                          String(row.calendar_date || "").trim(),
                          normalizeShiftCode(row.shift_code),
                          String(row.process_code || "").trim().toUpperCase()
                        ].join("#");
                        const capacity = capacityByDateShiftProcess.get(key) || { workers: 0, machines: 0 };
                        const occupiedWorkers = toInt(row.occupied_workers, 0);
                        const occupiedMachines = toInt(row.occupied_machines, 0);
                        const netWorkers = Math.max(0, capacity.workers - occupiedWorkers);
                        const netMachines = Math.max(0, capacity.machines - occupiedMachines);
                        return (
                          <tr key={`${row.calendar_date}-${row.shift_code}-${row.process_code}`}>
                            <td>{row.shift_name_cn || row.shift_code || "-"}</td>
                            <td>{row.process_name_cn || row.process_code || "-"}</td>
                            <td>
                              <input
                                type="number"
                                min="0"
                                step="1"
                                value={row.occupied_workers ?? ""}
                                onChange={(e) => updateCarryoverRow(row, "occupied_workers", e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                type="number"
                                min="0"
                                step="1"
                                value={row.occupied_machines ?? ""}
                                onChange={(e) => updateCarryoverRow(row, "occupied_machines", e.target.value)}
                              />
                            </td>
                            <td>{capacity.workers}</td>
                            <td>{capacity.machines}</td>
                            <td>{netWorkers}</td>
                            <td>{netMachines}</td>
                            <td>
                              <button onClick={() => removeCarryoverRow(row)}>删除</button>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {activeConfigSubTab === CONFIG_SUB_MATERIAL ? (
            <div className="panel">
              <div className="panel-head">
                <h3>物料可用量（可编辑）</h3>
                <div className="toolbar">
                  <span className="hint">日期</span>
                  <select value={selectedConfigDate} onChange={(e) => setSelectedConfigDate(e.target.value)}>
                    {configDates.map((date) => (
                      <option key={date} value={date}>
                        {date}
                      </option>
                    ))}
                  </select>
                  <span className="hint">班次</span>
                  <select value={newMaterialShift} onChange={(e) => setNewMaterialShift(e.target.value)}>
                    {SHIFT_OPTIONS.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                  <span className="hint">产品</span>
                  <select
                    value={newMaterialProduct}
                    onChange={(e) => setNewMaterialProduct(e.target.value)}
                    disabled={productOptions.length === 0}
                  >
                    {productOptions.length === 0 ? (
                      <option value="">暂无产品</option>
                    ) : (
                      productOptions.map((option) => (
                        <option key={option.code} value={option.code}>
                          {option.name}
                        </option>
                      ))
                    )}
                  </select>
                  <span className="hint">工序</span>
                  <select
                    value={newMaterialProcess}
                    onChange={(e) => setNewMaterialProcess(e.target.value)}
                    disabled={processOptions.length === 0}
                  >
                    {processOptions.length === 0 ? (
                      <option value="">暂无工序</option>
                    ) : (
                      processOptions.map((option) => (
                        <option key={option.code} value={option.code}>
                          {option.name}
                        </option>
                      ))
                    )}
                  </select>
                  <button
                    onClick={addMaterialRow}
                    disabled={!selectedConfigDate || productOptions.length === 0 || processOptions.length === 0}
                  >
                    新增物料
                  </button>
                </div>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>班次</th>
                      <th>产品</th>
                      <th>工序</th>
                      <th>可用量</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {materialRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={5}>暂无数据</td>
                      </tr>
                    ) : (
                      materialRowsForDate.map((row) => (
                        <tr key={`${row.calendar_date}-${row.shift_code}-${row.product_code}-${row.process_code}`}>
                          <td>{row.shift_name_cn || row.shift_code || "-"}</td>
                          <td>{row.product_name_cn || row.product_code || "-"}</td>
                          <td>{row.process_name_cn || row.process_code || "-"}</td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="0.01"
                              value={row.available_qty ?? ""}
                              onChange={(e) => updateMaterialRow(row, "available_qty", e.target.value)}
                            />
                          </td>
                          <td>
                            <button onClick={() => removeMaterialRow(row)}>删除</button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
