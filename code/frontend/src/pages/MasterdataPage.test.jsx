import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MasterdataPage from "./MasterdataPage";

vi.mock("../services/api", () => ({
  loadList: vi.fn(),
  postContract: vi.fn()
}));

import { loadList, postContract } from "../services/api";

describe("MasterdataPage route list display", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    postContract.mockResolvedValue({ ok: true });
  });

  it("shows route code and product display name by the configured mapping rules", async () => {
    loadList.mockImplementation((path) => {
      if (path === "/v1/mes/process-routes") {
        return Promise.resolve({
          items: [
            {
              id: "route-1",
              route_no: "ROUTE-YXN.009.020.1047",
              route_name_cn: "Simmons3工艺路线",
              product_code: "YXN.009.020.1047",
              product_name_cn: "旧产品名",
              process_code: "W030",
              process_name_cn: "包装打包",
              sequence_no: 1,
              dependency_type: "FS"
            },
            {
              id: "route-2",
              route_no: "ROUTE-PROD_CUSTOM_X",
              route_name_cn: "自定义工艺路线",
              product_code: "PROD_CUSTOM_X",
              product_name_cn: "自定义产品A",
              process_code: "W160",
              process_name_cn: "全检导管",
              sequence_no: 1,
              dependency_type: "FS"
            },
            {
              id: "route-hidden-1",
              route_no: "ROUTE-PROD_CATH",
              route_name_cn: "导管工艺路线",
              product_code: "PROD_CATH",
              product_name_cn: "导管",
              process_code: "PROC_TUBE",
              process_name_cn: "制管",
              sequence_no: 1,
              dependency_type: "FS"
            }
          ]
        });
      }
      if (path === "/v1/mes/equipments") {
        return Promise.resolve({ items: [] });
      }
      if (path === "/internal/v1/internal/masterdata/config") {
        return Promise.resolve({});
      }
      if (path === "/api/schedules") {
        return Promise.resolve({ items: [] });
      }
      return Promise.resolve({ items: [] });
    });

    const { container } = render(
      <MemoryRouter initialEntries={["/masterdata"]}>
        <MasterdataPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(loadList).toHaveBeenCalledWith("/v1/mes/process-routes");
    });

    expect(screen.getByRole("columnheader", { name: "编号" })).toBeInTheDocument();

    expect(screen.getByText("YXN.009.020.1047")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "一次性使用造影导管Simmons3 5F" })).toHaveAttribute(
      "href",
      "/masterdata?product_code=YXN.009.020.1047"
    );

    expect(screen.getAllByText("自定义产品A").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("link", { name: "自定义产品A" })).toHaveAttribute(
      "href",
      "/masterdata?product_code=PROD_CUSTOM_X"
    );

    expect(screen.queryByText("Simmons3工艺路线")).toBeNull();
    expect(screen.queryByText("自定义工艺路线")).toBeNull();
    expect(screen.queryByText("导管")).toBeNull();
    expect(container.querySelectorAll("tr.route-product-stripe-alt").length).toBe(1);
  });

  it("defers order material loading until the plan-config tab is opened", async () => {
    loadList.mockImplementation((path) => {
      if (path === "/v1/mes/process-routes") {
        return Promise.resolve({ items: [] });
      }
      if (path === "/internal/v1/internal/masterdata/config") {
        return Promise.resolve({});
      }
      if (path === "/internal/v1/internal/schedule-calendar/rules") {
        return Promise.resolve({});
      }
      if (path === "/api/schedules") {
        return Promise.resolve({ items: [] });
      }
      if (path === "/internal/v1/internal/material-availability/orders") {
        return Promise.resolve({ items: [] });
      }
      return Promise.resolve({ items: [] });
    });

    render(
      <MemoryRouter initialEntries={["/masterdata"]}>
        <MasterdataPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(loadList).toHaveBeenCalledWith("/v1/mes/process-routes");
    });

    expect(loadList).not.toHaveBeenCalledWith("/internal/v1/internal/material-availability/orders");

    fireEvent.click(screen.getAllByRole("tab")[1]);

    await waitFor(() => {
      expect(loadList).toHaveBeenCalledWith("/internal/v1/internal/material-availability/orders");
    });
  });
});
