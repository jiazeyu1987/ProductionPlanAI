import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OrdersPoolPage from "./OrdersPoolPage";

vi.mock("../features/order-execution/ordersPoolService", () => ({
  fetchOrdersPoolSnapshot: vi.fn(),
}));

vi.mock("../features/order-execution", () => ({
  listOrderPoolMaterials: vi.fn(),
  listMaterialChildrenByParentCode: vi.fn(),
}));

vi.mock("../features/masterdata", () => ({
  listOrderMaterialAvailability: vi.fn(),
}));

const { fetchOrdersPoolSnapshot } = await import(
  "../features/order-execution/ordersPoolService"
);
const {
  listOrderPoolMaterials,
  listMaterialChildrenByParentCode,
} = await import("../features/order-execution");
const { listOrderMaterialAvailability } = await import("../features/masterdata");

describe("OrdersPoolPage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
    listOrderPoolMaterials.mockResolvedValue({ items: [] });
    listMaterialChildrenByParentCode.mockResolvedValue({ items: [] });
    listOrderMaterialAvailability.mockResolvedValue({ items: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it("persists order-status filter across remounts", async () => {
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
          product_name_cn: "Open Product",
          product_name: "Open Product",
        },
        {
          order_no: "MO-DONE",
          order_status: "DONE",
          status: "DONE",
          order_qty: 10,
          completed_qty: 10,
          remaining_qty: 0,
          product_code: "P-DONE",
          product_name_cn: "Done Product",
          product_name: "Done Product",
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

    fireEvent.change(screen.getByLabelText("订单状态过滤"), {
      target: { value: "OPEN" },
    });

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
    expect(screen.getByLabelText("订单状态过滤")).toHaveValue("OPEN");
  });

  it("loads material rows for database-backed order 881MO091048 and shows code name and spec model", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "881MO091048",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "YXN.041.011.100",
          product_name_cn: "测试产品",
          product_name: "测试产品",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });
    listOrderPoolMaterials.mockResolvedValue({
      items: [
        {
          order_no: "881MO091048",
          child_material_code: "A003.017.01.004",
          child_material_name_cn: "弯曲连接件",
          spec_model: "(83819) 本色",
          issue_qty: 1,
          required_qty: 1,
        },
        {
          order_no: "881MO091048",
          child_material_code: "A004.002.09.200",
          child_material_name_cn: "导管盘管 PTCA",
          spec_model: "4.4*5.72*1520mm",
          issue_qty: 2,
          required_qty: 2,
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={["/orders/pool?order_no=881MO091048"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("生产用料清单（子物料编码）")).toBeInTheDocument();
    expect(await screen.findByText("A003.017.01.004")).toBeInTheDocument();
    expect(await screen.findByText("弯曲连接件")).toBeInTheDocument();
    expect(await screen.findByText("(83819) 本色")).toBeInTheDocument();
    expect(await screen.findByText("应发数量")).toBeInTheDocument();
    expect(await screen.findByText("2")).toBeInTheDocument();
    expect(await screen.findByText("A004.002.09.200")).toBeInTheDocument();
    expect(await screen.findByText("导管盘管 PTCA")).toBeInTheDocument();
    expect(await screen.findByText("4.4*5.72*1520mm")).toBeInTheDocument();
    expect(listOrderPoolMaterials).toHaveBeenCalledWith("881MO091048");
    expect(screen.queryByLabelText("生产订单号查询")).not.toBeInTheDocument();
  });

  it("auto triggers fast material query once when entering a local order detail", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "MO-LOCAL-001",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "P-LOCAL-001",
          product_name_cn: "Local Product",
          product_name: "Local Product",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });
    listOrderPoolMaterials.mockResolvedValue({
      items: [
        {
          order_no: "MO-LOCAL-001",
          child_material_code: "A001.02.012.2005",
          child_material_name_cn: "Embryo Tube",
          spec_model: "SPEC-001",
          issue_qty: 1,
          required_qty: 1,
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={["/orders/pool?order_no=MO-LOCAL-001"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("A001.02.012.2005")).toBeInTheDocument();
    expect(await screen.findByText("Embryo Tube")).toBeInTheDocument();
    expect(listOrderPoolMaterials).toHaveBeenCalledTimes(1);
    expect(listOrderPoolMaterials).toHaveBeenCalledWith("MO-LOCAL-001");
  });

  it("refreshes only root material rows and does not auto-load child nodes", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "881MO091048",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "YXN.041.011.100",
          product_name_cn: "测试产品",
          product_name: "测试产品",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });
    listOrderPoolMaterials
      .mockResolvedValueOnce({
        items: [
          {
            order_no: "881MO091048",
            child_material_code: "A003.017.01.004",
            child_material_name_cn: "弯曲连接件",
            spec_model: "(83819) 本色",
            child_material_supply_type: "SELF_MADE",
            child_material_supply_type_name_cn: "自制",
          },
        ],
      })
      .mockResolvedValueOnce({
        items: [
          {
            order_no: "881MO091048",
            child_material_code: "A003.017.01.004",
            child_material_name_cn: "弯曲连接件",
            spec_model: "(83819) 本色",
            child_material_supply_type: "SELF_MADE",
            child_material_supply_type_name_cn: "自制",
          },
        ],
      });

    render(
      <MemoryRouter initialEntries={["/orders/pool?order_no=881MO091048"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("A003.017.01.004")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "刷新子物料" }));

    await waitFor(() => {
      expect(listOrderPoolMaterials).toHaveBeenNthCalledWith(2, "881MO091048", true);
    });
    expect(listMaterialChildrenByParentCode).not.toHaveBeenCalled();
  });

  it("refreshes self-made child materials only for self-made nodes", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "881MO091048",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "YXN.041.011.100",
          product_name_cn: "测试产品",
          product_name: "测试产品",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });
    listOrderPoolMaterials.mockResolvedValue({
      items: [
        {
          order_no: "881MO091048",
          child_material_code: "A003.017.01.004",
          child_material_name_cn: "弯曲连接件",
          spec_model: "(83819) 本色",
          child_material_supply_type: "SELF_MADE",
          child_material_supply_type_name_cn: "自制",
        },
        {
          order_no: "881MO091048",
          child_material_code: "A004.002.09.200",
          child_material_name_cn: "导管盘管 PTCA",
          spec_model: "4.4*5.72*1520mm",
          child_material_supply_type: "PURCHASED",
          child_material_supply_type_name_cn: "外购",
        },
      ],
    });
    listMaterialChildrenByParentCode.mockResolvedValue({ items: [] });

    render(
      <MemoryRouter initialEntries={["/orders/pool?order_no=881MO091048"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("A003.017.01.004")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "刷新自制" }));

    await waitFor(() => {
      expect(listMaterialChildrenByParentCode).toHaveBeenCalledWith("A003.017.01.004", true);
    });
    expect(listMaterialChildrenByParentCode).toHaveBeenCalledTimes(1);
    expect(listOrderMaterialAvailability).not.toHaveBeenCalled();
  });

  it("refreshes inventory cache for the current order", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "881MO091048",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "YXN.041.011.100",
          product_name_cn: "测试产品",
          product_name: "测试产品",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });
    listOrderPoolMaterials.mockResolvedValue({ items: [] });

    render(
      <MemoryRouter initialEntries={["/orders/pool?order_no=881MO091048"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("生产用料清单（子物料编码）")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "刷新库存" }));

    await waitFor(() => {
      expect(listOrderMaterialAvailability).toHaveBeenCalledWith(true);
    });
  });

  it("does not render order number toolbar controls", async () => {
    fetchOrdersPoolSnapshot.mockResolvedValue({
      allRows: [
        {
          order_no: "881MO091048",
          order_status: "OPEN",
          status: "OPEN",
          order_qty: 1,
          completed_qty: 0,
          remaining_qty: 1,
          product_code: "YXN.041.011.100",
          product_name_cn: "测试产品",
          product_name: "测试产品",
        },
      ],
      reportings: [],
      scheduledOrderNos: [],
    });

    render(
      <MemoryRouter initialEntries={["/orders/pool"]}>
        <OrdersPoolPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("881MO091048")).toBeInTheDocument();
    expect(screen.queryByLabelText("生产订单号查询")).not.toBeInTheDocument();
    expect(screen.queryByText(/显示\s+\d+\s*\/\s*\d+/)).not.toBeInTheDocument();
  });
});
