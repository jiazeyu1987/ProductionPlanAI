import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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

  it("renders workshop-line-order details in selected day", async () => {
    mockApis({
      versions: [{ version_no: "V100", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: [
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "L1",
            line_name: "浜х嚎1",
            process_code: "P1",
            enabled_flag: 1
          },
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "L2",
            line_name: "浜х嚎2",
            process_code: "P1",
            enabled_flag: 1
          }
        ]
      },
      tasksByVersion: {
        V100: [
          {
            order_no: "ORD-001",
            process_code: "P1",
            process_name_cn: "鍒囧壊",
            calendar_date: "2026-03-10",
            shift_code: "DAY",
            plan_qty: 12
          },
          {
            order_no: "ORD-002",
            process_code: "P1",
            process_name_cn: "鍒囧壊",
            calendar_date: "2026-03-10",
            shift_code: "NIGHT",
            plan_qty: 8
          }
        ]
      }
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V100");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-10"));

    const workshopCard = await screen.findByTestId("schedule-calendar-workshop-WS-A");
    expect(within(workshopCard).getByText("浜х嚎1")).toBeInTheDocument();
    expect(within(workshopCard).getByText("浜х嚎2")).toBeInTheDocument();
    expect(within(workshopCard).getByText("ORD-001")).toBeInTheDocument();
    expect(within(workshopCard).getByText("ORD-002")).toBeInTheDocument();
  });
});

