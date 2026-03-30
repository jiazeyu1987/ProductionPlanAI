import { describe, expect, it } from "vitest";
import { buildMasterdataConfigSavePayload, normalizeLineTopologyRowsForSave } from "./saveConfigUtils";

const PROCESS_OPTIONS = [{ code: "W010" }, { code: "W020" }];

function buildValidRow(overrides = {}) {
  return {
    company_code: "company-main",
    workshop_code: "ws-a",
    line_code: "line-a",
    line_name: "Line A",
    process_code: "w010",
    capacity_per_shift: "10",
    required_workers: "2",
    required_machines: "1",
    enabled_flag: 1,
    ...overrides
  };
}

describe("saveConfigUtils", () => {
  it("throws when process options are empty", () => {
    expect(() => normalizeLineTopologyRowsForSave([buildValidRow()], [])).toThrow(
      "No available process options. Please maintain process config first."
    );
  });

  it("throws when workshop/line/process is empty", () => {
    expect(() =>
      normalizeLineTopologyRowsForSave(
        [buildValidRow({ workshop_code: "", line_code: "", process_code: "" })],
        PROCESS_OPTIONS
      )
    ).toThrow("Workshop/line/process cannot be empty in line topology.");
  });

  it("throws when process code is not in valid process options", () => {
    expect(() =>
      normalizeLineTopologyRowsForSave([buildValidRow({ process_code: "W999" })], PROCESS_OPTIONS)
    ).toThrow("Line topology contains unknown process code: W999");
  });

  it("throws when duplicate topology rows exist after normalization", () => {
    expect(() =>
      normalizeLineTopologyRowsForSave(
        [
          buildValidRow(),
          buildValidRow({ workshop_code: "WS-A", line_code: " LINE-A ", process_code: "W010" })
        ],
        PROCESS_OPTIONS
      )
    ).toThrow("Line topology has duplicate row: WS-A / LINE-A / W010");
  });

  it("throws when capacity/workers/machines are not positive", () => {
    expect(() =>
      normalizeLineTopologyRowsForSave([buildValidRow({ capacity_per_shift: 0 })], PROCESS_OPTIONS)
    ).toThrow("Line LINE-A / process W010: capacity_per_shift must be > 0.");

    expect(() =>
      normalizeLineTopologyRowsForSave([buildValidRow({ required_workers: 0 })], PROCESS_OPTIONS)
    ).toThrow("Line LINE-A / process W010: required_workers must be > 0.");

    expect(() =>
      normalizeLineTopologyRowsForSave([buildValidRow({ required_machines: 0 })], PROCESS_OPTIONS)
    ).toThrow("Line LINE-A / process W010: required_machines must be > 0.");
  });

  it("normalizes rows and builds payload", () => {
    const payload = buildMasterdataConfigSavePayload(
      [
        buildValidRow({
          company_code: "company-main",
          workshop_code: " ws-a ",
          line_code: " line-a ",
          process_code: " w010 ",
          enabled_flag: "0"
        })
      ],
      PROCESS_OPTIONS
    );
    expect(payload).toEqual({
      line_topology: [
        {
          company_code: "COMPANY-MAIN",
          workshop_code: "WS-A",
          line_code: "LINE-A",
          line_name: "Line A",
          process_code: "W010",
          capacity_per_shift: 10,
          required_workers: 2,
          required_machines: 1,
          enabled_flag: 0
        }
      ]
    });
  });

  it("throws when normalized line topology rows are empty", () => {
    expect(() => normalizeLineTopologyRowsForSave([], PROCESS_OPTIONS)).toThrow(
      "At least one line topology row is required."
    );
  });
});
