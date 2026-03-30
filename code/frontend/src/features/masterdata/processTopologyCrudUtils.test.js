import { describe, expect, it } from "vitest";
import {
  buildProcessConfigUpdateResult,
  resolveNextProcessConfigCode,
  buildNewProcessConfigRow,
  resolveDefaultResourceCapacity,
  buildNewResourcePoolRow,
  buildNewMaterialAvailabilityRow,
  resolveAddResourceRowDraft,
  resolveAddMaterialRowDraft,
} from "./processTopologyCrudUtils";

describe("processTopologyCrudUtils", () => {
  it("buildProcessConfigUpdateResult updates non-process field only in processConfigs", () => {
    const prev = {
      processConfigs: [{ process_code: "W010", process_name_cn: "A" }],
      lineTopology: [{ process_code: "W010" }],
      resourcePool: [{ process_code: "W010" }],
      materialAvailability: [{ process_code: "W010" }],
    };
    const next = buildProcessConfigUpdateResult(prev, 0, "process_name_cn", "B");
    expect(next).toEqual({
      processConfigs: [{ process_code: "W010", process_name_cn: "B" }],
      lineTopology: [{ process_code: "W010" }],
      resourcePool: [{ process_code: "W010" }],
      materialAvailability: [{ process_code: "W010" }],
    });
  });

  it("buildProcessConfigUpdateResult cascades process_code update to related rows", () => {
    const prev = {
      processConfigs: [{ process_code: "w010", process_name_cn: "A" }],
      lineTopology: [{ process_code: "W010" }, { process_code: "W020" }],
      resourcePool: [{ process_code: " w010 " }, { process_code: "W030" }],
      materialAvailability: [{ process_code: "W010" }, { process_code: "W040" }],
    };
    const next = buildProcessConfigUpdateResult(prev, 0, "process_code", "w099");
    expect(next).toEqual({
      processConfigs: [{ process_code: "W099", process_name_cn: "A" }],
      lineTopology: [{ process_code: "W099" }, { process_code: "W020" }],
      resourcePool: [{ process_code: "W099" }, { process_code: "W030" }],
      materialAvailability: [{ process_code: "W099" }, { process_code: "W040" }],
    });
  });

  it("resolveNextProcessConfigCode picks first unused option then falls back to PROC_NEW_n", () => {
    expect(
      resolveNextProcessConfigCode(
        [{ process_code: "W010" }],
        [{ code: "W010" }, { code: "W020" }],
      ),
    ).toBe("W020");

    expect(
      resolveNextProcessConfigCode(
        [{ process_code: "PROC_NEW_2" }, { process_code: "PROC_NEW_3" }],
        [],
      ),
    ).toBe("PROC_NEW_4");
  });

  it("buildNewProcessConfigRow sets defaults and resolves process name fallback", () => {
    const row = buildNewProcessConfigRow(
      [{ process_code: "W010" }],
      [{ code: "W010" }, { code: "W020" }],
      new Map([["W020", "包装"]]),
    );
    expect(row).toEqual({
      process_code: "W020",
      process_name_cn: "包装",
      capacity_per_shift: 1,
      required_workers: 1,
      required_machines: 1,
    });
  });

  it("resolveDefaultResourceCapacity calculates non-negative defaults from process params", () => {
    const defaults = resolveDefaultResourceCapacity(
      new Map([["W010", { required_workers: "2", required_machines: "3" }]]),
      "W010",
    );
    expect(defaults).toEqual({
      workersAvailable: 2,
      machinesAvailable: 3,
    });

    const fallbackDefaults = resolveDefaultResourceCapacity(new Map(), "W099");
    expect(fallbackDefaults).toEqual({
      workersAvailable: 1,
      machinesAvailable: 1,
    });
  });

  it("buildNewResourcePoolRow builds row with normalized shift display", () => {
    expect(
      buildNewResourcePoolRow({
        date: "2026-03-29",
        shiftCode: "DAY",
        processCode: "W010",
        processName: "包装",
        workersAvailable: 2,
        machinesAvailable: 1,
      }),
    ).toEqual({
      calendar_date: "2026-03-29",
      shift_code: "DAY",
      shift_name_cn: "DAY",
      process_code: "W010",
      process_name_cn: "包装",
      open_flag: 1,
      workers_available: 2,
      machines_available: 1,
    });
  });

  it("buildNewMaterialAvailabilityRow builds row with default available_qty", () => {
    expect(
      buildNewMaterialAvailabilityRow({
        date: "2026-03-29",
        shiftCode: "NIGHT",
        productCode: "P001",
        productName: "产品A",
        processCode: "W020",
        processName: "全检",
      }),
    ).toEqual({
      calendar_date: "2026-03-29",
      shift_code: "NIGHT",
      shift_name_cn: "NIGHT",
      product_code: "P001",
      product_name_cn: "产品A",
      process_code: "W020",
      process_name_cn: "全检",
      available_qty: 0,
    });
  });

  it("resolveAddResourceRowDraft validates and builds row", () => {
    expect(
      resolveAddResourceRowDraft({
        selectedConfigDate: "",
        newResourceShift: "DAY",
        newResourceProcess: "W010",
        resourcePool: [],
        processNameByCode: new Map(),
        processDefaultParamByCode: new Map(),
      }),
    ).toEqual({ error: "Please select a calendar date." });

    expect(
      resolveAddResourceRowDraft({
        selectedConfigDate: "2026-03-29",
        newResourceShift: "DAY",
        newResourceProcess: "W010",
        resourcePool: [{ calendar_date: "2026-03-29", shift_code: "DAY", process_code: "W010" }],
        processNameByCode: new Map(),
        processDefaultParamByCode: new Map(),
      }),
    ).toEqual({ error: "Resource record already exists." });

    const result = resolveAddResourceRowDraft({
      selectedConfigDate: "2026-03-29",
      newResourceShift: "DAY",
      newResourceProcess: "W010",
      resourcePool: [],
      processNameByCode: new Map([["W010", "Process 10"]]),
      processDefaultParamByCode: new Map([["W010", { required_workers: 2, required_machines: 1 }]]),
    });
    expect(result.error).toBe("");
    expect(result.row).toEqual({
      calendar_date: "2026-03-29",
      shift_code: "DAY",
      shift_name_cn: "DAY",
      process_code: "W010",
      process_name_cn: "Process 10",
      open_flag: 1,
      workers_available: 2,
      machines_available: 1,
    });
  });

  it("resolveAddMaterialRowDraft validates and builds row", () => {
    expect(
      resolveAddMaterialRowDraft({
        selectedConfigDate: "2026-03-29",
        newMaterialShift: "DAY",
        newMaterialProcess: "",
        newMaterialProduct: "P001",
        materialAvailability: [],
        processNameByCode: new Map(),
        productOptions: [],
      }),
    ).toEqual({ error: "Please select shift, process and product." });

    expect(
      resolveAddMaterialRowDraft({
        selectedConfigDate: "2026-03-29",
        newMaterialShift: "DAY",
        newMaterialProcess: "W010",
        newMaterialProduct: "P001",
        materialAvailability: [{ calendar_date: "2026-03-29", shift_code: "DAY", process_code: "W010", product_code: "P001" }],
        processNameByCode: new Map(),
        productOptions: [],
      }),
    ).toEqual({ error: "Material record already exists." });

    const result = resolveAddMaterialRowDraft({
      selectedConfigDate: "2026-03-29",
      newMaterialShift: "NIGHT",
      newMaterialProcess: "W020",
      newMaterialProduct: "P002",
      materialAvailability: [],
      processNameByCode: new Map([["W020", "Process 20"]]),
      productOptions: [{ code: "P002", name: "Product 2" }],
    });
    expect(result.error).toBe("");
    expect(result.row).toEqual({
      calendar_date: "2026-03-29",
      shift_code: "NIGHT",
      shift_name_cn: "NIGHT",
      product_code: "P002",
      product_name_cn: "Product 2",
      process_code: "W020",
      process_name_cn: "Process 20",
      available_qty: 0,
    });
  });
});
