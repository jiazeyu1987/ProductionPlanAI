import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SimulationPage from "./SimulationPage";

vi.mock("../services/api", () => ({
  loadList: vi.fn(),
  postContract: vi.fn()
}));

import { loadList, postContract } from "../services/api";

function mockRefreshData() {
  loadList.mockImplementation((path) => {
    if (path.includes("/simulation/state")) {
      return Promise.resolve({
        current_sim_date: "2026-03-22",
        sales_order_total: 0,
        production_order_total: 3,
        latest_version_no: "V001",
        last_run_summary: {}
      });
    }
    if (path.includes("/simulation/events")) {
      return Promise.resolve({ items: [] });
    }
    return Promise.resolve({ items: [] });
  });
}

describe("SimulationPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRefreshData();
    postContract.mockResolvedValue({
      request_id: "req-test",
      state: {
        current_sim_date: "2026-03-22",
        sales_order_total: 0,
        production_order_total: 3,
        latest_version_no: "V001",
        last_run_summary: {}
      }
    });
  });

  it("calls manual add production order endpoint", async () => {
    render(<SimulationPage />);
    await waitFor(() => expect(loadList).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "虚增生产订单" }));

    await waitFor(() =>
      expect(postContract).toHaveBeenCalledWith(
        "/internal/v1/internal/simulation/manual/add-production-order",
        {}
      )
    );
  });

  it("hides batch simulation entry points", async () => {
    render(<SimulationPage />);
    await waitFor(() => expect(loadList).toHaveBeenCalled());

    expect(screen.queryByRole("tab", { name: /批量/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /快进/ })).toBeNull();
  });
});
