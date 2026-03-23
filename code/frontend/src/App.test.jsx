import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("App routing shell", () => {
  it("renders sidebar and key navigation entries", () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText("自动排产平台")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "生产订单" })).toHaveAttribute("href", "/orders/pool");
    expect(screen.getByRole("link", { name: "排产历史" })).toHaveAttribute("href", "/schedule/versions");
    expect(screen.getByRole("link", { name: "计划报表" })).toHaveAttribute("href", "/reports/plans");
    expect(screen.getByRole("link", { name: "仿真" })).toHaveAttribute("href", "/simulation");
    expect(screen.getByRole("link", { name: "报工" })).toHaveAttribute("href", "/execution/wip");
    expect(screen.getByRole("link", { name: "说明页" })).toHaveAttribute("href", "/guide");
  });
});
