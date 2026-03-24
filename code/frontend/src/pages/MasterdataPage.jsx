import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import SimpleTable from "../components/SimpleTable";
import { loadList, postContract } from "../services/api";

const ROUTE_TAB = "route";
const EQUIPMENT_TAB = "equipment";
const CONFIG_TAB = "config";
const CONFIG_SUB_WINDOW = "window";
const CONFIG_SUB_PROCESS = "process";
const CONFIG_SUB_RESOURCE = "resource";
const CONFIG_SUB_CARRYOVER = "carryover";
const CONFIG_SUB_MATERIAL = "material";

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

function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function toInt(value, fallback = 0) {
  return Math.max(0, Math.round(toNumber(value, fallback)));
}

function parseConfigResponse(raw) {
  const body = raw?.data ?? raw ?? {};
  const processConfigs = Array.isArray(body.process_configs) ? body.process_configs : [];
  const resourcePool = Array.isArray(body.resource_pool) ? body.resource_pool : [];
  const initialCarryoverOccupancy = Array.isArray(body.initial_carryover_occupancy) ? body.initial_carryover_occupancy : [];
  const materialAvailability = Array.isArray(body.material_availability) ? body.material_availability : [];
  return {
    processConfigs,
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
    resourcePool: [],
    initialCarryoverOccupancy: [],
    materialAvailability: [],
    horizonStartDate: "",
    horizonDays: 0,
    shiftsPerDay: 2
  });
  const [selectedConfigDate, setSelectedConfigDate] = useState("");
  const [selectedCarryoverProcess, setSelectedCarryoverProcess] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [saveError, setSaveError] = useState("");
  const [activeConfigSubTab, setActiveConfigSubTab] = useState(CONFIG_SUB_WINDOW);

  useEffect(() => {
    Promise.all([
      loadList("/v1/mes/process-routes"),
      loadList("/v1/mes/equipments"),
      loadList("/internal/v1/internal/masterdata/config").catch(() => null)
    ])
      .then(([routeRes, equipmentRes, configRes]) => {
        setRoutes(routeRes.items ?? []);
        setEquipments(equipmentRes.items ?? []);
        if (configRes) {
          const parsed = parseConfigResponse(configRes);
          setConfigData(parsed);
          setSelectedConfigDate((prev) => prev || firstDateFromConfig(parsed));
        }
      })
      .catch(() => {});
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

    const carryoverByKey = new Map();
    for (const row of configData.initialCarryoverOccupancy) {
      const rowDate = String(row.calendar_date || "").trim();
      const processCode = String(row.process_code || "").trim().toUpperCase();
      if (rowDate !== date || processCode !== selectedProcessCode) {
        continue;
      }
      const shiftCode = normalizeShiftCode(row.shift_code);
      const key = `${rowDate}#${shiftCode}#${processCode}`;
      carryoverByKey.set(key, {
        ...row,
        calendar_date: rowDate,
        shift_code: shiftCode,
        process_code: processCode,
        occupied_workers: toInt(row.occupied_workers, 0),
        occupied_machines: toInt(row.occupied_machines, 0)
      });
    }

    const rows = [];
    const usedKeys = new Set();
    for (const row of configData.resourcePool) {
      const rowDate = String(row.calendar_date || "").trim();
      const processCode = String(row.process_code || "").trim().toUpperCase();
      if (rowDate !== date || processCode !== selectedProcessCode) {
        continue;
      }
      const shiftCode = normalizeShiftCode(row.shift_code);
      const key = `${rowDate}#${shiftCode}#${processCode}`;
      const carryover = carryoverByKey.get(key);
      rows.push({
        calendar_date: rowDate,
        shift_code: shiftCode,
        shift_name_cn: row.shift_name_cn || row.shift_name || "-",
        process_code: processCode,
        process_name_cn: row.process_name_cn || row.process_name || processCode,
        occupied_workers: carryover ? toInt(carryover.occupied_workers, 0) : 0,
        occupied_machines: carryover ? toInt(carryover.occupied_machines, 0) : 0
      });
      usedKeys.add(key);
    }

    for (const [key, row] of carryoverByKey.entries()) {
      if (usedKeys.has(key)) {
        continue;
      }
      rows.push({
        ...row,
        occupied_workers: toInt(row.occupied_workers, 0),
        occupied_machines: toInt(row.occupied_machines, 0)
      });
    }

    rows.sort((a, b) => {
      const byShift = shiftSortIndex(a.shift_code) - shiftSortIndex(b.shift_code);
      if (byShift !== 0) {
        return byShift;
      }
      return String(a.process_code || "").localeCompare(String(b.process_code || ""), "zh-Hans-CN");
    });
    return rows;
  }, [configData.initialCarryoverOccupancy, configData.resourcePool, selectedConfigDate, selectedCarryoverProcess]);

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

  function updateProcessConfig(processCode, field, value) {
    setConfigData((prev) => ({
      ...prev,
      processConfigs: prev.processConfigs.map((row) =>
        String(row.process_code || "") === String(processCode || "")
          ? { ...row, [field]: value }
          : row
      )
    }));
  }

  function updateResourceRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      resourcePool: prev.resourcePool.map((row) => {
        const isMatch =
          String(row.calendar_date || "") === String(target.calendar_date || "")
          && normalizeShiftCode(row.shift_code) === normalizeShiftCode(target.shift_code)
          && String(row.process_code || "") === String(target.process_code || "");
        return isMatch ? { ...row, [field]: value } : row;
      })
    }));
  }

  function updateMaterialRow(target, field, value) {
    setConfigData((prev) => ({
      ...prev,
      materialAvailability: prev.materialAvailability.map((row) => {
        const isMatch =
          String(row.calendar_date || "") === String(target.calendar_date || "")
          && normalizeShiftCode(row.shift_code) === normalizeShiftCode(target.shift_code)
          && String(row.product_code || "") === String(target.product_code || "")
          && String(row.process_code || "") === String(target.process_code || "");
        return isMatch ? { ...row, [field]: value } : row;
      })
    }));
  }

  function updateCarryoverRow(target, field, value) {
    setConfigData((prev) => {
      const targetDate = String(target.calendar_date || "").trim();
      const targetShiftCode = normalizeShiftCode(target.shift_code);
      const targetProcessCode = String(target.process_code || "").trim().toUpperCase();
      if (!targetDate || !targetShiftCode || !targetProcessCode) {
        return prev;
      }

      let matched = false;
      const nextRows = prev.initialCarryoverOccupancy.map((row) => {
        const isMatch =
          String(row.calendar_date || "").trim() === targetDate
          && normalizeShiftCode(row.shift_code) === targetShiftCode
          && String(row.process_code || "").trim().toUpperCase() === targetProcessCode;
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
      const payload = {
        horizon_start_date: configData.horizonStartDate || "",
        horizon_days: Math.max(1, toInt(configData.horizonDays, 1)),
        shifts_per_day: Math.max(1, Math.min(2, toInt(configData.shiftsPerDay, 2))),
        process_configs: configData.processConfigs.map((row) => ({
          process_code: row.process_code,
          capacity_per_shift: toNumber(row.capacity_per_shift, 0),
          required_workers: toInt(row.required_workers, 0),
          required_machines: toInt(row.required_machines, 0)
        })),
        resource_pool: configData.resourcePool.map((row) => ({
          calendar_date: row.calendar_date,
          shift_code: normalizeShiftCode(row.shift_code),
          process_code: row.process_code,
          workers_available: toInt(row.workers_available, 0),
          machines_available: toInt(row.machines_available, 0),
          open_flag: toInt(row.open_flag, 1) === 1 ? 1 : 0
        })),
        initial_carryover_occupancy: configData.initialCarryoverOccupancy.map((row) => ({
          calendar_date: row.calendar_date,
          shift_code: normalizeShiftCode(row.shift_code),
          process_code: row.process_code,
          occupied_workers: toInt(row.occupied_workers, 0),
          occupied_machines: toInt(row.occupied_machines, 0)
        })),
        material_availability: configData.materialAvailability.map((row) => ({
          calendar_date: row.calendar_date,
          shift_code: normalizeShiftCode(row.shift_code),
          product_code: row.product_code,
          process_code: row.process_code,
          available_qty: toNumber(row.available_qty, 0)
        }))
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
                  </tr>
                </thead>
                <tbody>
                  {groupedRouteRows.length === 0 ? (
                    <tr>
                      <td colSpan={4}>暂无数据</td>
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
              <h3>工序参数（可编辑）</h3>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>工序</th>
                      <th>每组产能/班</th>
                      <th>每组需人</th>
                      <th>每组需机</th>
                    </tr>
                  </thead>
                  <tbody>
                    {configData.processConfigs.length === 0 ? (
                      <tr>
                        <td colSpan={4}>暂无数据</td>
                      </tr>
                    ) : (
                      configData.processConfigs.map((row) => (
                        <tr key={row.process_code || row.process_name_cn}>
                          <td>
                            <Link className="table-link" to={`/masterdata?process_code=${encodeURIComponent(row.process_code || "")}`}>
                              {row.process_name_cn || row.process_code || "-"}
                            </Link>
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="0.01"
                              value={row.capacity_per_shift ?? ""}
                              onChange={(e) => updateProcessConfig(row.process_code, "capacity_per_shift", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.required_workers ?? ""}
                              onChange={(e) => updateProcessConfig(row.process_code, "required_workers", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={row.required_machines ?? ""}
                              onChange={(e) => updateProcessConfig(row.process_code, "required_machines", e.target.value)}
                            />
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
                    </tr>
                  </thead>
                  <tbody>
                    {resourceRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={5}>暂无数据</td>
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
                    </tr>
                  </thead>
                  <tbody>
                    {carryoverRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={8}>当前日期和工艺暂无可编辑班次，请先检查“班次资源”是否已配置。</td>
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
                    </tr>
                  </thead>
                  <tbody>
                    {materialRowsForDate.length === 0 ? (
                      <tr>
                        <td colSpan={4}>暂无数据</td>
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
