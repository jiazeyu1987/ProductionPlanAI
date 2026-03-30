import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../services/api", () => ({
  loadList: vi.fn(),
  postContract: vi.fn(),
  postLegacy: vi.fn()
}));

import { mockApis, renderPage, setupDefaultApiMocks, teardownTest } from "./scheduleCalendarTestUtils";

describe("ScheduleCalendarPage", () => {
  beforeEach(() => {
    setupDefaultApiMocks();
  });

  afterEach(() => {
    teardownTest();
  });

  it("shows workshop tabs and supports switching", async () => {
    mockApis({
      versions: [{ version_no: "V400", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: [
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "L1",
            line_name: "A-LINE",
            process_code: "P1",
            enabled_flag: 1
          },
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-B",
            line_code: "L2",
            line_name: "B-LINE",
            process_code: "P2",
            enabled_flag: 1
          }
        ]
      },
      tasksByVersion: {
        V400: [
          {
            order_no: "ORD-A",
            process_code: "P1",
            process_name_cn: "宸ュ簭A",
            calendar_date: "2026-03-15",
            shift_code: "DAY",
            plan_qty: 3
          },
          {
            order_no: "ORD-B",
            process_code: "P2",
            process_name_cn: "宸ュ簭B",
            calendar_date: "2026-03-15",
            shift_code: "DAY",
            plan_qty: 4
          }
        ]
      }
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V400");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-15"));

    expect(screen.getByTestId("schedule-calendar-tab-WS-A")).toBeInTheDocument();
    expect(screen.getByTestId("schedule-calendar-tab-WS-B")).toBeInTheDocument();
    expect(screen.getByText("ORD-A")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("schedule-calendar-tab-WS-B"));
    expect(screen.getByText("ORD-B")).toBeInTheDocument();
  });

  it("pads workshop tabs to four when topology has fewer workshops", async () => {
    mockApis({
      versions: [{ version_no: "V500", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: [
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-PRODUCTION",
            line_code: "L1",
            line_name: "浜х嚎1",
            process_code: "P1",
            enabled_flag: 1
          },
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-STERILE",
            line_code: "L2",
            line_name: "浜х嚎2",
            process_code: "P2",
            enabled_flag: 1
          }
        ]
      },
      tasksByVersion: {
        V500: [
          {
            order_no: "ORD-500",
            process_code: "P1",
            process_name_cn: "宸ュ簭A",
            calendar_date: "2026-03-20",
            shift_code: "DAY",
            plan_qty: 2
          }
        ]
      }
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V500");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-20"));

    const tabs = screen.getAllByRole("tab");
    expect(tabs.length).toBe(4);
    expect(screen.getByTestId("schedule-calendar-tab-WS-PRODUCTION")).toBeInTheDocument();
    expect(screen.getByTestId("schedule-calendar-tab-WS-STERILE")).toBeInTheDocument();
  });

  it("line link jumps to masterdata topology with location params", async () => {
    mockApis({
      versions: [{ version_no: "V600", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: [
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "L1",
            line_name: "绾夿体1",
            process_code: "P1",
            enabled_flag: 1
          }
        ]
      },
      tasksByVersion: {
        V600: [
          {
            order_no: "ORD-600",
            process_code: "P1",
            process_name_cn: "宸ュ簭1",
            calendar_date: "2026-03-18",
            shift_code: "DAY",
            plan_qty: 5
          }
        ]
      }
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V600");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-18"));
    const link = screen.getByTestId("schedule-calendar-line-select-WS-A-L1");
    expect(link.getAttribute("href")).toContain("/masterdata?");
    expect(link.getAttribute("href")).toContain("tab=config");
    expect(link.getAttribute("href")).toContain("config_sub=topology");
    expect(link.getAttribute("href")).toContain("topology_view=friendly");
    expect(link.getAttribute("href")).toContain("company_code=COMPANY-MAIN");
    expect(link.getAttribute("href")).toContain("workshop_code=WS-A");
    expect(link.getAttribute("href")).toContain("line_code=L1");
  });
});

