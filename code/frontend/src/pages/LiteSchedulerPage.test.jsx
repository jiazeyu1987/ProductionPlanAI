import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import LiteSchedulerPage from "./LiteSchedulerPage";

describe("LiteSchedulerPage", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });
  afterEach(() => {
    cleanup();
  });

  it("renders with default catheter line", () => {
    render(<LiteSchedulerPage />);
    expect(screen.getByRole("heading", { name: "简版排产" })).toBeInTheDocument();
    expect(screen.getByText("导管产线-1", { selector: "td" })).toBeInTheDocument();
  });

  it("supports manual order input and one-day simulation advance", async () => {
    render(<LiteSchedulerPage />);

    const orderPanel = screen.getAllByRole("heading", { name: "订单录入" })[0]?.closest(".panel");
    expect(orderPanel).not.toBeNull();
    const scope = within(orderPanel);

    fireEvent.change(scope.getByPlaceholderText("手动输入"), { target: { value: "TEST-001" } });
    fireEvent.change(scope.getByLabelText("工作量(天)"), { target: { value: "1" } });
    fireEvent.click(scope.getByRole("button", { name: "新增订单" }));

    expect(screen.getByText("TEST-001", { selector: "td" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "推进1天" }));
    expect(await screen.findByText(/当日完成 1/)).toBeInTheDocument();
  });
});
