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
    expect(screen.getByRole("heading", { level: 2 })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("open-order-modal"));
    expect(screen.getByTestId("order-modal")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^order-line-days-/).length).toBeGreaterThan(0);
  });

  it("adds an order through modal and advances one day", async () => {
    render(<LiteSchedulerPage />);
    const startInput = screen.getByTestId("horizon-start-input");
    const startBefore = startInput.value;

    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "TEST-001" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(screen.getByText("TEST-001", { selector: "td" })).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("advance-day-btn"));
    await screen.findByTestId("horizon-start-input");
    expect(screen.getByTestId("horizon-start-input")).not.toHaveValue(startBefore);
  });

  it("supports skipping statutory holidays and persists toggle state", () => {
    const view = render(<LiteSchedulerPage />);
    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2026-09-30" } });

    const toggle = screen.getByTestId("skip-holidays-toggle");
    expect(toggle).not.toBeChecked();
    fireEvent.click(toggle);
    expect(toggle).toBeChecked();

    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue("2026-10-08");

    view.unmount();
    render(<LiteSchedulerPage />);
    expect(screen.getByTestId("skip-holidays-toggle")).toBeChecked();
  });

  it("supports weekend mode NONE/SINGLE/DOUBLE", () => {
    render(<LiteSchedulerPage />);
    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2026-03-27" } });
    fireEvent.click(screen.getByTestId("skip-holidays-toggle"));

    fireEvent.click(screen.getByTestId("weekend-mode-none"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue("2026-03-28");

    fireEvent.click(screen.getByTestId("weekend-mode-single"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue("2026-03-30");

    fireEvent.change(screen.getByTestId("horizon-start-input"), {
      target: { value: "2026-03-27" },
    });
    fireEvent.click(screen.getByTestId("weekend-mode-double"));
    fireEvent.click(screen.getByTestId("advance-day-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue("2026-03-30");
  });

  it("supports clicking calendar to toggle rest/work mode", () => {
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
    ).toHaveClass("lite-cal-mode-btn-rest");
    fireEvent.click(within(day27Before).getByTestId("calendar-toggle-2026-03-27"));
    const day27After = screen.getByTestId("calendar-day-2026-03-27");
    expect(
      within(day27After).getByTestId("calendar-toggle-2026-03-27"),
    ).toHaveClass("lite-cal-mode-btn-work");
  });

  it("supports editing and deleting an order", () => {
    render(<LiteSchedulerPage />);
    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "EDIT-ME" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    const oldRow = screen.getByText("EDIT-ME", { selector: "td" }).closest("tr");
    expect(oldRow).not.toBeNull();
    fireEvent.click(within(oldRow).getAllByRole("button")[1]);
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "EDITED-ORDER" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));
    expect(screen.getByText("EDITED-ORDER", { selector: "td" })).toBeInTheDocument();

    const editedRow = screen
      .getByText("EDITED-ORDER", { selector: "td" })
      .closest("tr");
    expect(editedRow).not.toBeNull();
    fireEvent.click(within(editedRow).getAllByRole("button")[2]);
    expect(screen.queryByText("EDITED-ORDER", { selector: "td" })).toBeNull();
  });

  it("shows line-to-order assignments in calendar cells", () => {
    render(<LiteSchedulerPage />);
    fireEvent.click(screen.getByTestId("lite-tab-orders"));
    fireEvent.click(screen.getByTestId("open-order-modal"));
    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "CAL-001" },
    });
    fireEvent.change(screen.getByTestId("order-spec-input"), {
      target: { value: "12Fr" },
    });
    fireEvent.change(screen.getByTestId("order-batch-no-input"), {
      target: { value: "LOT-001" },
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

    fireEvent.click(screen.getByTestId("lite-tab-schedule"));
    fireEvent.change(screen.getByTestId("calendar-month-input"), {
      target: { value: "2026-03" },
    });
    const orderBtns = await screen.findAllByRole("button", { name: /DUR-001/ });
    fireEvent.click(orderBtns[0]);
    expect(screen.getByTestId("finish-modal")).toBeInTheDocument();
    const finishInput = screen.getByTestId("finish-date-input");
    const savedFinishDate = finishInput.value;
    fireEvent.change(finishInput, { target: { value: savedFinishDate } });
    fireEvent.click(screen.getByTestId("submit-finish-btn"));

    fireEvent.click(screen.getByTestId("lite-tab-orders"));
    expect(screen.getByText(savedFinishDate, { selector: "td" })).toBeInTheDocument();
    expect(screen.queryByTestId("finish-modal")).toBeNull();
  });

  it("supports one-click replan from today", () => {
    render(<LiteSchedulerPage />);
    const startInput = screen.getByTestId("horizon-start-input");
    fireEvent.change(startInput, { target: { value: "2020-01-01" } });

    const now = new Date();
    const utcYear = now.getUTCFullYear();
    const utcMonth = String(now.getUTCMonth() + 1).padStart(2, "0");
    const utcDay = String(now.getUTCDate()).padStart(2, "0");
    const todayIso = `${utcYear}-${utcMonth}-${utcDay}`;

    fireEvent.click(screen.getByTestId("replan-today-btn"));
    expect(screen.getByTestId("horizon-start-input")).toHaveValue(todayIso);
  });

  it("supports local snapshot save, rename, load and delete", () => {
    render(<LiteSchedulerPage />);
    const startInput = screen.getByTestId("horizon-start-input");
    const savedStart = startInput.value;

    fireEvent.click(screen.getByTestId("save-snapshot-btn"));
    fireEvent.change(screen.getByTestId("snapshot-name-input"), {
      target: { value: "snapshot-A" },
    });
    fireEvent.click(screen.getByTestId("snapshot-save-confirm-btn"));
    expect(screen.getAllByDisplayValue("snapshot-A").length).toBeGreaterThan(0);

    fireEvent.change(startInput, { target: { value: "2020-01-01" } });
    fireEvent.click(screen.getByTestId("load-snapshot-btn"));
    const nameInput = screen.getAllByDisplayValue("snapshot-A")[0];
    const row = nameInput.closest("tr");
    expect(row).not.toBeNull();

    fireEvent.change(nameInput, { target: { value: "snapshot-B" } });
    fireEvent.blur(nameInput);
    const renamedInput = screen.getAllByDisplayValue("snapshot-B")[0];
    const renamedRow = renamedInput.closest("tr");
    expect(renamedRow).not.toBeNull();
    fireEvent.click(within(renamedRow).getAllByRole("button")[0]);
    expect(screen.getByTestId("horizon-start-input")).toHaveValue(savedStart);

    fireEvent.click(screen.getByTestId("load-snapshot-btn"));
    const deletingInput = screen.getAllByDisplayValue("snapshot-B")[0];
    const deletingRow = deletingInput.closest("tr");
    expect(deletingRow).not.toBeNull();
    fireEvent.click(within(deletingRow).getAllByRole("button")[1]);
    expect(screen.queryByDisplayValue("snapshot-B")).toBeNull();
  });

  it("exports scheduled orders from calendar to excel", () => {
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
      expect(clickSpy).toHaveBeenCalled();
    } finally {
      clickSpy.mockRestore();
    }
  });
});
