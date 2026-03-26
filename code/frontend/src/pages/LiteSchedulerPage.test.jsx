import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
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
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    const oldRow = screen
      .getByText("EDIT-ME", { selector: "td" })
      .closest("tr");
    expect(oldRow).not.toBeNull();
    fireEvent.click(within(oldRow).getByRole("button", { name: "编辑" }));

    fireEvent.change(screen.getByTestId("order-no-input"), {
      target: { value: "EDITED-ORDER" },
    });
    fireEvent.change(screen.getAllByTestId(/^order-line-days-/)[0], {
      target: { value: "1.5" },
    });
    fireEvent.click(screen.getByTestId("submit-order-modal"));

    expect(
      screen.getByText("EDITED-ORDER", { selector: "td" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("EDIT-ME", { selector: "td" })).toBeNull();

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
