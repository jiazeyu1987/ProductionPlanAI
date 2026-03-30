import { describe, expect, it } from "vitest";
import {
  commandLabel,
  isForcedUnfinishedOrder,
  isInProgressStatus,
  isOrderCompleted,
  materialSupplyTypeName,
  normalizeOrderNoKey,
  orderStatusText,
  progressText,
  toIntText,
} from "./formatters";

describe("orders-pool formatters", () => {
  it("formats int-like text safely", () => {
    expect(toIntText("12.7")).toBe("13");
    expect(toIntText("x")).toBe("-");
  });

  it("resolves command label", () => {
    expect(commandLabel("LOCK")).toBe("锁单");
    expect(commandLabel("OTHER")).toBe("OTHER");
  });

  it("checks in-progress status", () => {
    expect(isInProgressStatus("in_progress")).toBe(true);
    expect(isInProgressStatus("done")).toBe(false);
  });

  it("builds progress text by explicit rate or derived rate", () => {
    expect(
      progressText({
        order_qty: 100,
        completed_qty: 30,
        progress_rate: 33.3333,
      }),
    ).toBe("30 / 100（33.33%）");
    expect(
      progressText({
        order_qty: 5,
        completed_qty: 3,
      }),
    ).toBe("3 / 5（60%）");
  });

  it("maps order number normalization and forced unfinished logic", () => {
    expect(normalizeOrderNoKey("881-M0091041")).toBe("881MO091041");
    expect(isForcedUnfinishedOrder({ order_no: "881M0091041" })).toBe(true);
    expect(isForcedUnfinishedOrder({ order_no: "X" })).toBe(false);
  });

  it("resolves order status text by business rule", () => {
    expect(orderStatusText({ order_no: "OTHER", order_qty: 10, completed_qty: 10 })).toBe("已完成");
    expect(orderStatusText({ order_no: "OTHER", status_name_cn: "已完成" })).toBe("已完成");
    expect(orderStatusText({ order_no: "OTHER", status: "DONE" })).toBe("已完成");
    expect(orderStatusText({ order_no: "OTHER", order_qty: 10, completed_qty: 0, status_name_cn: "延期" })).toBe(
      "未完成（延期）",
    );
    expect(orderStatusText({ order_no: "881M0091041", status_name_cn: "B" })).toBe("未完成（审核中）");
    expect(orderStatusText({ order_no: "881M0091041", status: "D" })).toBe("未完成（重新审核）");
    expect(orderStatusText({ order_no: "881M0091041" })).toBe("未完成");
  });

  it("detects order completion by qty", () => {
    expect(isOrderCompleted({ order_no: "X", order_qty: 10, completed_qty: 10 })).toBe(true);
    expect(isOrderCompleted({ order_no: "X", order_qty: 10, completed_qty: 9.9 })).toBe(false);
    expect(isOrderCompleted({ order_no: "X", order_qty: 10, remaining_qty: 0 })).toBe(true);
    expect(isOrderCompleted({ order_no: "X", order_qty: 10, remaining_qty: 1 })).toBe(false);
  });

  it("renders material supply type name", () => {
    expect(materialSupplyTypeName({ child_material_supply_type_name_cn: "外购件" })).toBe("外购件");
    expect(materialSupplyTypeName({ child_material_supply_type: "SELF_MADE" })).toBe("自制");
    expect(materialSupplyTypeName({ child_material_supply_type: "UNKNOWN" })).toBe("未知");
  });
});
