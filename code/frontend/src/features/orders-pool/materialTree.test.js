import { describe, expect, it } from "vitest";
import {
  buildMaterialTreeRows,
  collectSelfMadeCodes,
  collapseExpandedMaterialNodeKeys,
  hasOwn,
  normalizeMaterialCode,
} from "./materialTree";

describe("orders-pool material tree", () => {
  it("normalizes material code and hasOwn helper", () => {
    expect(normalizeMaterialCode("  A-1  ")).toBe("A-1");
    expect(hasOwn({ A: 1 }, "A")).toBe(true);
    expect(hasOwn({ A: 1 }, "B")).toBe(false);
  });

  it("collects self-made material codes", () => {
    expect(
      collectSelfMadeCodes([
        { child_material_code: "M1", child_material_supply_type: "SELF_MADE" },
        { child_material_code: "M2", child_material_supply_type: "PURCHASED" },
        { child_material_code: "  ", child_material_supply_type: "SELF_MADE" },
      ]),
    ).toEqual(["M1"]);
  });

  it("builds flattened tree rows with expand/collapse metadata", () => {
    const rows = buildMaterialTreeRows(
      [
        { child_material_code: "A", child_material_supply_type: "SELF_MADE" },
        { child_material_code: "B", child_material_supply_type: "PURCHASED" },
      ],
      {
        A: [{ child_material_code: "A1", child_material_supply_type: "PURCHASED" }],
      },
      new Set(["A:0"]),
      { A: false, A1: false },
      { A: "", A1: "" },
    );

    expect(rows.map((item) => item.child_material_code)).toEqual(["A", "A1", "B"]);
    expect(rows[0]._treeExpanded).toBe(true);
    expect(rows[1]._treeDepth).toBe(1);
  });

  it("collapses node keys and all descendants", () => {
    expect(
      collapseExpandedMaterialNodeKeys(
        ["A:0", "A:0>B:0", "A:0>B:0>C:0", "X:0"],
        "A:0>B:0",
      ),
    ).toEqual(["A:0", "X:0"]);
  });
});

