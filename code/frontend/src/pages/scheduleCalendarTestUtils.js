import { cleanup, render } from "@testing-library/react";
import { createElement } from "react";
import { vi } from "vitest";
import { loadList, postContract, postLegacy } from "../services/api";
import ScheduleCalendarPage from "./ScheduleCalendarPage";

export function setupDefaultApiMocks() {
  vi.clearAllMocks();
  postContract.mockResolvedValue({ ok: true });
  postLegacy.mockResolvedValue({ version_no: "V999" });
}

export function teardownTest() {
  cleanup();
}

export function renderPage() {
  return render(createElement(ScheduleCalendarPage));
}

export function mockApis({ versions, config, rules, tasksByVersion }) {
  loadList.mockImplementation((path) => {
    if (path === "/internal/v1/internal/schedule-versions") {
      return Promise.resolve({ items: versions || [] });
    }
    if (path === "/internal/v1/internal/masterdata/config") {
      return Promise.resolve(config || {});
    }
    if (path === "/internal/v1/internal/schedule-calendar/rules") {
      return Promise.resolve(rules || config || {});
    }
    const match = String(path).match(/\/schedule-versions\/([^/]+)\/tasks$/);
    if (match) {
      const versionNo = decodeURIComponent(match[1]);
      return Promise.resolve({ items: tasksByVersion?.[versionNo] || [] });
    }
    return Promise.resolve({ items: [] });
  });
}
