import { describe, expect, it } from "vitest";
import {
  buildLineTopologyPayloadForSave,
  resolveSelectedConfigDateAfterSave,
} from "./configSaveUtils";

describe("configSaveUtils", () => {
  it("builds normalized line_topology payload", () => {
    const payload = buildLineTopologyPayloadForSave(
      [
        {
          company_code: "company-main",
          workshop_code: " ws-a ",
          line_code: " line-a ",
          line_name: "Line A",
          process_code: " w010 ",
          capacity_per_shift: "2.5",
          required_workers: "3",
          required_machines: "1",
          enabled_flag: "1",
        },
      ],
      [{ code: "W010" }],
    );

    expect(payload).toEqual({
      line_topology: [
        {
          company_code: "COMPANY-MAIN",
          workshop_code: "WS-A",
          line_code: "LINE-A",
          line_name: "Line A",
          process_code: "W010",
          capacity_per_shift: 2.5,
          required_workers: 3,
          required_machines: 1,
          enabled_flag: 1,
        },
      ],
    });
  });

  it("throws when line_topology has duplicate rows", () => {
    expect(() =>
      buildLineTopologyPayloadForSave(
        [
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "LINE-A",
            line_name: "Line A",
            process_code: "W010",
            capacity_per_shift: 1,
            required_workers: 1,
            required_machines: 1,
          },
          {
            company_code: "COMPANY-MAIN",
            workshop_code: "WS-A",
            line_code: "LINE-A",
            line_name: "Line A",
            process_code: "W010",
            capacity_per_shift: 1,
            required_workers: 1,
            required_machines: 1,
          },
        ],
        [{ code: "W010" }],
      ),
    ).toThrow("duplicate");
  });

  it("keeps previous selected date when still valid after save", () => {
    const next = resolveSelectedConfigDateAfterSave("2026-04-02", {
      horizonStartDate: "2026-04-01",
      horizonDays: 3,
      resourcePool: [],
      materialAvailability: [],
    });
    expect(next).toBe("2026-04-02");
  });

  it("falls back to horizon first date when previous selected date is invalid", () => {
    const next = resolveSelectedConfigDateAfterSave("2026-04-10", {
      horizonStartDate: "2026-04-01",
      horizonDays: 3,
      resourcePool: [],
      materialAvailability: [],
    });
    expect(next).toBe("2026-04-01");
  });

  it("falls back to first date in config rows when horizon is empty", () => {
    const next = resolveSelectedConfigDateAfterSave("", {
      horizonStartDate: "",
      horizonDays: 0,
      resourcePool: [{ calendar_date: "2026-05-03" }],
      materialAvailability: [{ calendar_date: "2026-05-04" }],
    });
    expect(next).toBe("2026-05-03");
  });
});
