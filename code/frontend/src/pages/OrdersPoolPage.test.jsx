import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrdersPoolPage from "./OrdersPoolPage";

vi.mock("../features/order-execution/ordersPoolService", () => ({
  fetchOrdersPoolSnapshot: vi.fn(),
}));

vi.mock("../features/order-execution/ordersPoolClient", () => ({
  listOrderPoolMaterials: vi.fn(),
  listMaterialChildrenByParentCode: vi.fn(),
}));

const { fetchOrdersPoolSnapshot } = await import(
  "../features/order-execution/ordersPoolService"
);

describe("OrdersPoolPage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
  });

  it("persists unfinished-only filter across remounts", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "MO-OPEN",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 10,
          completed_qty: 0,
          remaining_qty: 10,
          product_code: "P-OPEN",
          product_name_cn: "开放导管",
          product_name: "开放导管",
        },
        {
          order_no: "MO-DONE",
          order_status: "DONE",
          status: "DONE",
          order_qty: 10,
          completed_qty: 10,
          remaining_qty: 0,
          product_code: "P-DONE",
          product_name_cn: "完成导管",
          product_name: "完成导管",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });

    const view = render(
      <MemoryRouter initialEntries={["/orders/pool"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("MO-OPEN")).toBeInTheDocument();
    expect(screen.getByText("MO-DONE")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("checkbox"));

    await waitFor(() => {
      expect(screen.queryByText("MO-DONE")).not.toBeInTheDocument();
    });

    view.unmount();

    render(
      <MemoryRouter initialEntries={["/orders/pool"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("MO-OPEN")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByText("MO-DONE")).not.toBeInTheDocument();
    });
    expect(screen.getByRole("checkbox")).toBeChecked();
  });
});
