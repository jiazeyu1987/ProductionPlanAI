import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import LiteSchedulerPage from "./LiteSchedulerPage";

describe("LiteSchedulerPage", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders and opens order modal with all configured lines", () => {
    render(<LiteSchedulerPage />);
    expect(
      screen.getByRole("heading", { name: "璞慧排产" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("open-order-modal")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("open-order-modal"));
    expect(screen.getByTestId("order-modal")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^order-line-days-/).length).toBeGreaterThan(
      0,
    );
  });

  it("adds an order through modal and supports one-day advance", async () => {
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "TEST-001" },
    });

    const firstLineInput = screen.getAllByTestId(/^order-line-days-/)[0];
    fireEvent.change(firstLineInput, {
      target: { value: "1" },
    });

    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(
      screen.getByText("TEST-001", { selector: "td" }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(await screen.findByText(/已推进到/)).toBeInTheDocument();
  });

  it("supports skipping statutory holidays and persists toggle state", async () => {
    const view = render(<LiteSchedulerPage />);

    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2026-09-30" } });
    expect(startInput).toHaveValue("2026-09-30");

    const toggle = screen.getByTestId("skip-holidays-toggle");
    expect(toggle).not.toBeChecked();
    fireEvent.click(toggle);
    expect(toggle).toBeChecked();
    const hint = screen.getByTestId("holiday-years-hint");
    expect(hint).toHaveTextContent("2026");
    expect(hint).toHaveTextContent("2027");
    expect(hint).toHaveTextContent("2028");
    expect(hint).not.toHaveTextContent("2024");
    expect(hint).not.toHaveTextContent("2025");

    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(await screen.findByText(/已推进到 2026-10-08/)).toBeInTheDocument();
    expect(screen.getByTestId("horizon-start-input")).toHaveValue("2026-10-08");

    view.unmount();
    render(<LiteSchedulerPage />);
    expect(screen.getByTestId("skip-holidays-toggle")).toBeChecked();
  });

  it("supports weekend mode NONE/SINGLE/DOUBLE", async () => {
    render(<LiteSchedulerPage />);

    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2026-03-27" } });
    expect(startInput).toHaveValue("2026-03-27");

    fireEvent.click(screen.getByTestId("skip-holidays-toggle"));

    fireEvent.click(screen.getByTestId("weekend-mode-none"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(await screen.findByText(/已推进到 2026-03-28/)).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("weekend-mode-single"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(await screen.findByText(/已推进到 2026-03-30/)).toBeInTheDocument();

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-27" },
    });
    fireEvent.click(screen.getByTestId("weekend-mode-double"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(await screen.findByText(/已推进到 2026-03-30/)).toBeInTheDocument();
  });

  it("supports clicking calendar to set rest or schedule", async () => {
    render(<LiteSchedulerPage />);

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-27" },
    });
    fireEvent.click(screen.getByTestId("skip-holidays-toggle"));
    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });

    const day27Before = screen.getByTestId("calendar-day-2026-03-27");
    expect(
      within(day27Before).getByTestId("calendar-toggle-2026-03-27"),
    ).toHaveTextContent("休息");
    fireEvent.click(
      within(day27Before).getByTestId("calendar-toggle-2026-03-27"),
    );
    expect(
      await screen.findByText(/2026-03-27 已设为休息/),
    ).toBeInTheDocument();

    const day27After = screen.getByTestId("calendar-day-2026-03-27");
    expect(
      within(day27After).getByTestId("calendar-toggle-2026-03-27"),
    ).toHaveTextContent("排产");

    const day28Before = screen.getByTestId("calendar-day-2026-03-28");
    expect(
      within(day28Before).getByTestId("calendar-toggle-2026-03-28"),
    ).toHaveTextContent("排产");
    fireEvent.click(
      within(day28Before).getByTestId("calendar-toggle-2026-03-28"),
    );
    expect(
      await screen.findByText(/2026-03-28 已设为排产/),
    ).toBeInTheDocument();

    const day28After = screen.getByTestId("calendar-day-2026-03-28");
    expect(
      within(day28After).getByTestId("calendar-toggle-2026-03-28"),
    ).toHaveTextContent("休息");
  });

  it("supports editing and deleting an order", () => {
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "EDIT-ME" },
    });
    fireEvent.change(screen.getByTestId("order-product-name-input"), {
      target: { value: "Prod-A" },
    });
    fireEvent.change(screen.getByTestId("order-spec-input"), {
      target: { value: "Spec-A" },
    });
    fireEvent.change(screen.getByTestId("order-batch-no-input"), {
      target: { value: "Batch-A" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(screen.getByText("Prod-A", { selector: "td" })).toBeInTheDocument();
    expect(screen.getByText("Spec-A", { selector: "td" })).toBeInTheDocument();
    expect(screen.getByText("Batch-A", { selector: "td" })).toBeInTheDocument();

    const oldRow = screen
      .getByText("EDIT-ME", { selector: "td" })
      .closest("tr");
    expect(oldRow).not.toBeNull();
    fireEvent.click(within(oldRow).getByRole("button", { name: "编辑" }));
    expect(screen.getByTestId("order-product-name-input")).toHaveValue("Prod-A");
    expect(screen.getByTestId("order-spec-input")).toHaveValue("Spec-A");
    expect(screen.getByTestId("order-batch-no-input")).toHaveValue("Batch-A");

    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "EDITED-ORDER" },
    });
    fireEvent.change(screen.getByTestId("order-product-name-input"), {
      target: { value: "Prod-B" },
    });
    fireEvent.change(screen.getByTestId("order-spec-input"), {
      target: { value: "Spec-B" },
    });
    fireEvent.change(screen.getByTestId("order-batch-no-input"), {
      target: { value: "Batch-B" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1.5" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    expect(
      screen.getByText("EDITED-ORDER", { selector: "td" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("EDIT-ME", { selector: "td" })).toBeNull();
    expect(screen.getByText("Prod-B", { selector: "td" })).toBeInTheDocument();
    expect(screen.getByText("Spec-B", { selector: "td" })).toBeInTheDocument();
    expect(screen.getByText("Batch-B", { selector: "td" })).toBeInTheDocument();
    expect(screen.queryByText("Prod-A", { selector: "td" })).toBeNull();
    expect(screen.queryByText("Spec-A", { selector: "td" })).toBeNull();
    expect(screen.queryByText("Batch-A", { selector: "td" })).toBeNull();

    const editedRow = screen
      .getByText("EDITED-ORDER", { selector: "td" })
      .closest("tr");
    expect(editedRow).not.toBeNull();
    fireEvent.click(within(editedRow).getByRole("button", { name: "删除" }));

    expect(screen.queryByText("EDITED-ORDER", { selector: "td" })).toBeNull();
  });

  it("auto-generates order numbers in sequence", () => {
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(screen.getByText("PO-0001", { selector: "td" })).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(screen.getByText("PO-0002", { selector: "td" })).toBeInTheDocument();
  });

  it("supports selecting month and renders only the selected month dates", () => {
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("lite-tab-schedule"));

    const startInput = screen.getByTestId("horizon-start-input");
    const startBefore = startInput.value;
    const monthInput = screen.getByTestId("calendar-month-input");
    fireEvent.change(monthInput, { target: { value: "2026-02" } });
    expect(monthInput).toHaveValue("2026-02");
    expect(startInput).toHaveValue(startBefore);
    expect(screen.getByTestId("calendar-day-2026-02-01")).toBeInTheDocument();
    expect(screen.getByTestId("calendar-day-2026-02-28")).toBeInTheDocument();
    expect(screen.queryByTestId("calendar-day-2026-02-29")).toBeNull();

    fireEvent.change(monthInput, { target: { value: "2024-02" } });
    expect(screen.getByTestId("calendar-day-2024-02-29")).toBeInTheDocument();
    expect(screen.queryByTestId("calendar-day-2024-03-01")).toBeNull();

    fireEvent.click(screen.getByTestId("calendar-next-month-btn"));
    expect(monthInput).toHaveValue("2024-03");
    expect(screen.getByTestId("calendar-day-2024-03-31")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("calendar-prev-month-btn"));
    expect(monthInput).toHaveValue("2024-02");
  });

  it("shows line to order assignments in calendar cells", () => {
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });

    fireEvent.click(screen.getByTestId("lite-tab-orders"));
    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "CAL-001" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });
    const matches = screen.getAllByText(/CAL-001/);
    expect(matches.some((node) => node.closest(".lite-cal-line-orders"))).toBe(
      true,
    );
  });

  it("uses max planned days across lines in duration mode order table", () => {
    window.localStorage.setItem(
      "liteScheduler.scenario.v1",
      JSON.stringify({
        schemaVersion: 1,
        nextOrderSeq: 1,
        planningMode: "DURATION_MANUAL_FINISH",
        horizonStart: "2026-03-27",
        horizonDays: 30,
        skipStatutoryHolidays: false,
        weekendRestMode: "DOUBLE",
        dateWorkModeByDate: {},
        manualFinishByLineOrder: {},
        lines: [
          {
            id: "L1",
            name: "导管产线",
            baseCapacity: 300,
            capacityOverrides: {},
            enabled: true,
          },
          {
            id: "L2",
            name: "导丝产线",
            baseCapacity: 300,
            capacityOverrides: {},
            enabled: true,
          },
        ],
        orders: [],
        locks: [],
        simulationLogs: [],
      }),
    );
    render(<LiteSchedulerPage />);

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "MAX-001" },
    });
    const inputs = screen.getAllByTestId(/^order-line-plan-days-/);
    fireEvent.change(inputs[0], {
      target: { value: "7" },
    });
    fireEvent.change(inputs[1], {
      target: { value: "6" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    const row = screen.getByText("MAX-001", { selector: "td" }).closest("tr");
    expect(row).not.toBeNull();
    expect(within(row).getByText("7.0")).toBeInTheDocument();
    expect(within(row).queryByText("13.0")).toBeNull();
  });

  it("assigns different colors for adjacent orders in calendar", () => {
    render(<LiteSchedulerPage />);

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-27" },
    });
    fireEvent.click(screen.getByTestId("planning-mode-duration"));

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "CLR-001" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-plan-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "CLR-002" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-plan-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });

    const firstBtn = screen.getByRole("button", { name: /CLR-001/ });
    const secondBtn = screen.getByRole("button", { name: /CLR-002/ });
    const firstColorClass = [...firstBtn.classList].find((cls) =>
      cls.startsWith("lite-cal-task-border-"),
    );
    const secondColorClass = [...secondBtn.classList].find((cls) =>
      cls.startsWith("lite-cal-task-border-"),
    );
    expect(firstColorClass).toBeTruthy();
    expect(secondColorClass).toBeTruthy();
    expect(firstColorClass).not.toBe(secondColorClass);
  });

  it("exports scheduled orders from calendar to excel", async () => {
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    try {
      render(<LiteSchedulerPage />);

      fireEvent.click(screen.getByTestId("open-order-modal"));
      fireEvent.change(screen.getByTestId("order-no-input"), {
        target: { value: "EXP-001" },
      });
      fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
        target: { value: "1" },
      });
      fireEvent.click(screen.getByTestId("submit-order-modal"));

      fireEvent.click(screen.getByTestId("lite-tab-schedule"));
      fireEvent.click(screen.getByTestId("calendar-export-excel-btn"));

      expect(await screen.findByText(/已导出排产订单：/)).toBeInTheDocument();
    } finally {
      clickSpy.mockRestore();
    }
  });

  it("supports duration mode and manual finish backfill from calendar", async () => {
    render(<LiteSchedulerPage />);

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-27" },
    });
    fireEvent.click(screen.getByTestId("planning-mode-duration"));

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "DUR-001" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-plan-days-/)[0], {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "DUR-002" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-plan-days-/)[0], {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-29" },
    });
    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });

    const day29Before = screen.getByTestId("calendar-day-2026-03-29");
    fireEvent.click(within(day29Before).getByRole("button", { name: /DUR-001/ }));
    expect(screen.getByTestId("finish-modal")).toBeInTheDocument();
    const finishInput = screen.getByTestId("finish-date-input");
    const savedFinishDate = finishInput.value;
    fireEvent.change(finishInput, {
      target: { value: savedFinishDate },
    });
    fireEvent.click(screen.getByTestId("submit-finish-btn"));

    expect(
      await screen.findByText(
        new RegExp(`已结束，实际结束时间：${savedFinishDate}`),
      ),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("lite-tab-orders"));
    expect(screen.queryByText("已排天数(天)")).toBeNull();
    expect(screen.queryByText("剩余天数(天)")).toBeNull();
    expect(screen.queryByText(/还需天数/)).toBeNull();
    expect(screen.getByText("结束状态")).toBeInTheDocument();
    expect(screen.getByText("实际结束时间")).toBeInTheDocument();
    expect(screen.getByText("已结束", { selector: "td" })).toBeInTheDocument();
    expect(
      screen.getByText(savedFinishDate, { selector: "td" }),
    ).toBeInTheDocument();
  });

  it("supports one-click replan from today", () => {
    render(<LiteSchedulerPage />);

    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2020-01-01" } });
    expect(startInput).toHaveValue("2020-01-01");

    const now = new Date();
    const utcYear = now.getUTCFullYear();
    const utcMonth = String(now.getUTCMonth() + 1).padStart(2, "0");
    const utcDay = String(now.getUTCDate()).padStart(2, "0");
    const todayIso = `${utcYear}-${utcMonth}-${utcDay}`;

    fireEvent.click(screen.getByTestId("replan-today-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue(todayIso);
    expect(
      screen.getByText(new RegExp(`已从今天 ${todayIso} 开始重排`)),
    ).toBeInTheDocument();
  });

  it("supports local snapshot save, rename, load and delete", () => {
    render(<LiteSchedulerPage />);

    const startInput = screen.getByTestId("horizon-start-input");
    const savedStart = startInput.value;

    fireEvent.click(screen.getByTestId("save-snapshot-btn"));
    expect(screen.getByTestId("snapshot-modal")).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("snapshot-name-input"), {
      target: { value: "测试场景A" },
    });
    fireEvent.click(screen.getByTestId("snapshot-save-confirm-btn"));
    expect(screen.getByText(/场景已保存：测试场景A/)).toBeInTheDocument();

    fireEvent.change(startInput, { target: { value: "2020-01-01" } });
    expect(startInput).toHaveValue("2020-01-01");

    fireEvent.click(screen.getByTestId("load-snapshot-btn"));
    const nameInput = screen.getAllByDisplayValue("测试场景A")[0];
    const row = nameInput.closest("tr");
    expect(row).not.toBeNull();

    fireEvent.change(nameInput, { target: { value: "测试场景B" } });
    fireEvent.blur(nameInput);
    expect(screen.getByText(/场景已改名：测试场景B/)).toBeInTheDocument();

    const renamedInput = screen.getAllByDisplayValue("测试场景B")[0];
    const renamedRow = renamedInput.closest("tr");
    expect(renamedRow).not.toBeNull();
    fireEvent.click(within(renamedRow).getByRole("button", { name: "读取" }));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue(savedStart);

    fireEvent.click(screen.getByTestId("load-snapshot-btn"));
    const deletingInput = screen.getAllByDisplayValue("测试场景B")[0];
    const deletingRow = deletingInput.closest("tr");
    expect(deletingRow).not.toBeNull();
    fireEvent.click(within(deletingRow).getByRole("button", { name: "删除" }));
    expect(screen.queryByDisplayValue("测试场景B")).toBeNull();
  });
});
