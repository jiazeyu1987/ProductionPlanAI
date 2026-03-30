import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../services/api", () => ({
  loadList: vi.fn(),
  postContract: vi.fn(),
  postLegacy: vi.fn()
}));

import { loadList } from "../services/api";
import { mockApis, renderPage, setupDefaultApiMocks, teardownTest } from "./scheduleCalendarTestUtils";

describe("ScheduleCalendarPage", () => {
  beforeEach(() => {
    setupDefaultApiMocks();
  });

  afterEach(() => {
    teardownTest();
  });

  it("defaults to latest published version", async () => {
    mockApis({
      versions: [
        { version_no: "V001", status: "DRAFT" },
        { version_no: "V002", status: "PUBLISHED" },
        { version_no: "V003", status: "DRAFT" }
      ],
      config: {
        skip_statutory_holidays: false,
        weekend_rest_mode: "DOUBLE",
        date_shift_mode_by_date: {},
        line_topology: []
      },
      tasksByVersion: {
        V002: []
      }
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("schedule-calendar-version-select")).toHaveValue("V002");
    });
    await waitFor(() => {
      expect(loadList).toHaveBeenCalledWith("/internal/v1/internal/schedule-versions/V002/tasks");
    });
    expect(screen.queryByTestId("schedule-calendar-holiday-years")).toBeNull();
    expect(screen.queryByText(/规则保存仅更新主数据配置/)).toBeNull();
    expect(screen.queryByTestId("schedule-calendar-detail-set-rest")).toBeNull();
  });
});
