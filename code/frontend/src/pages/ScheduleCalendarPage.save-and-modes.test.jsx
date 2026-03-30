import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../services/api", () => ({
  loadList: vi.fn(),
  postContract: vi.fn(),
  postLegacy: vi.fn()
}));

import { postContract, postLegacy } from "../services/api";
import { mockApis, renderPage, setupDefaultApiMocks, teardownTest } from "./scheduleCalendarTestUtils";

describe("ScheduleCalendarPage", () => {
  beforeEach(() => {
    setupDefaultApiMocks();
  });

  afterEach(() => {
    teardownTest();
  });

  it("saves manual date mode in masterdata config payload", async () => {
    mockApis({
      versions: [{ version_no: "V200", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: true,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: []
      },
      tasksByVersion: {
        V200: [
          {
            order_no: "ORD-888",
            process_code: "P2",
            process_name_cn: "缁勮",
            calendar_date: "2026-03-12",
            shift_code: "DAY",
            plan_qty: 5
          }
        ]
      }
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V200");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-12"));
    fireEvent.click(screen.getByTestId("schedule-calendar-set-rest-2026-03-12"));
    fireEvent.click(screen.getByTestId("schedule-calendar-save-rules-btn"));

    await waitFor(() =>
      expect(postContract).toHaveBeenCalledWith(
        "/internal/v1/internal/schedule-calendar/rules",
        expect.objectContaining({
          skip_statutory_holidays: true,
          weekend_rest_mode: "DOUBLE",
          date_shift_mode_by_date: expect.objectContaining({
            "2026-03-12": "REST"
          })
        })
      )
    );
  });

  it("supports save and replan in calendar page", async () => {
    mockApis({
      versions: [{ version_no: "V300", status: "PUBLISHED", strategy_code: "KEY_ORDER_FIRST" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: []
      },
      tasksByVersion: {
        V300: [],
        V999: []
      }
    });
    postLegacy.mockResolvedValue({ version_no: "V999" });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V300");
    });

    fireEvent.click(screen.getByTestId("schedule-calendar-save-rules-btn"));

    await waitFor(() => {
      expect(postLegacy).toHaveBeenCalledWith(
        "/api/schedules/generate",
        expect.objectContaining({
          base_version_no: "V300",
          strategy_code: "KEY_ORDER_FIRST"
        })
      );
    });
  });

  it("supports combining day and night mode and hides clear button", async () => {
    mockApis({
      versions: [{ version_no: "V350", status: "PUBLISHED" }],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: []
      },
      tasksByVersion: {
        V350: []
      }
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V350");
    });
    fireEvent.change(screen.getByTestId("schedule-calendar-month-input"), {
      target: { value: "2026-03" }
    });
    fireEvent.click(screen.getByTestId("schedule-calendar-day-2026-03-16"));
    fireEvent.click(screen.getByTestId("schedule-calendar-set-night-2026-03-16"));

    expect(screen.getByTestId("schedule-calendar-set-day-2026-03-16").className).toContain(
      "schedule-calendar-mode-btn-both"
    );
    expect(screen.getByTestId("schedule-calendar-set-night-2026-03-16").className).toContain(
      "schedule-calendar-mode-btn-both"
    );
    expect(screen.queryByTestId("schedule-calendar-detail-clear")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("schedule-calendar-save-rules-btn"));
    await waitFor(() =>
      expect(postContract).toHaveBeenCalledWith(
        "/internal/v1/internal/schedule-calendar/rules",
        expect.objectContaining({
          date_shift_mode_by_date: expect.objectContaining({
            "2026-03-16": "BOTH"
          })
        })
      )
    );
  });
});

