import { describe, expect, it } from "vitest";
import {
  applyLineTopologyWithSectionLeaderPrune,
  buildLineTopologyProcessRow,
  buildTopologyLineProcessCodeSet,
  buildTopologyLineProcessRow,
  filterOutLineTopologyRows,
  filterOutTopologyLineProcessRows,
  filterOutTopologyLineRows,
  filterOutTopologyWorkshopRows,
  filterOutSectionLeaderBindingRows,
  matchesSectionLeaderBindingRow,
  resolveSelectedTopologyLineCodeOnLineEdit,
  resolveTopologyLineProcessToggleAction,
  updateLineTopologyConfigRows,
  updateTopologyLineRows,
  updateTopologyWorkshopRows,
} from "./lineTopologyUtils";

describe("lineTopologyUtils section leader matching", () => {
  it("matches by normalized leader_id and line_code", () => {
    const row = {
      leader_id: " leader-001 ",
      line_code: " line-a "
    };
    const target = {
      leader_id: "LEADER-001",
      line_code: "LINE-A"
    };
    expect(matchesSectionLeaderBindingRow(row, target)).toBe(true);
  });

  it("returns false when leader_id or line_code is different", () => {
    const row = {
      leader_id: "LEADER-001",
      line_code: "LINE-A"
    };
    expect(
      matchesSectionLeaderBindingRow(row, {
        leader_id: "LEADER-002",
        line_code: "LINE-A"
      }),
    ).toBe(false);
    expect(
      matchesSectionLeaderBindingRow(row, {
        leader_id: "LEADER-001",
        line_code: "LINE-B"
      }),
    ).toBe(false);
  });

  it("filterOutSectionLeaderBindingRows removes the matched normalized row only", () => {
    const rows = [
      { leader_id: "LEADER-001", line_code: "LINE-A" },
      { leader_id: "leader-001", line_code: " line-b " },
      { leader_id: "LEADER-002", line_code: "LINE-A" },
    ];
    const next = filterOutSectionLeaderBindingRows(rows, {
      leader_id: " leader-001 ",
      line_code: "line-a",
    });
    expect(next).toEqual([
      { leader_id: "leader-001", line_code: " line-b " },
      { leader_id: "LEADER-002", line_code: "LINE-A" },
    ]);
  });

  it("applyLineTopologyWithSectionLeaderPrune keeps only section leaders with valid line_code", () => {
    const prev = {
      lineTopology: [{ line_code: "LINE-A" }],
      sectionLeaderBindings: [
        { leader_id: "LEADER-001", line_code: "line-a" },
        { leader_id: "LEADER-002", line_code: "LINE-B" },
      ],
      resourcePool: [{ id: "R1" }],
    };
    const nextLineTopology = [{ line_code: "line-b" }, { line_code: "LINE-C" }];
    const next = applyLineTopologyWithSectionLeaderPrune(prev, nextLineTopology);
    expect(next).toEqual({
      lineTopology: [{ line_code: "line-b" }, { line_code: "LINE-C" }],
      sectionLeaderBindings: [{ leader_id: "LEADER-002", line_code: "LINE-B" }],
      resourcePool: [{ id: "R1" }],
    });
  });

  it("filterOutLineTopologyRows removes the matched normalized topology row only", () => {
    const next = filterOutLineTopologyRows(
      [
        { company_code: "company-main", workshop_code: "ws-a", line_code: "line-a", process_code: "w010" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W020" },
      ],
      { company_code: "COMPANY-MAIN", workshop_code: " WS-A ", line_code: " LINE-A ", process_code: " w010 " },
    );
    expect(next).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W020" },
    ]);
  });

  it("filterOutTopologyWorkshopRows removes all rows in the workshop", () => {
    const next = filterOutTopologyWorkshopRows(
      [
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A" },
        { company_code: "company-main", workshop_code: "ws-a", line_code: "LINE-B" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-B", line_code: "LINE-C" },
      ],
      { companyCode: " company-main ", workshopCode: " ws-a " },
    );
    expect(next).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-B", line_code: "LINE-C" },
    ]);
  });

  it("filterOutTopologyLineRows removes rows of the target line only", () => {
    const next = filterOutTopologyLineRows(
      [
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W010" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W020" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", process_code: "W010" },
      ],
      { companyCode: "COMPANY-MAIN", workshopCode: "WS-A", lineCode: "line-a" },
    );
    expect(next).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", process_code: "W010" },
    ]);
  });

  it("updateTopologyLineRows updates line_name and line_code for the target line", () => {
    const rows = [
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", line_name: "Line A", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", line_name: "Line B", process_code: "W010" },
    ];
    const line = { companyCode: "company-main", workshopCode: "ws-a", lineCode: "line-a" };

    const withName = updateTopologyLineRows(rows, line, "line_name", "  New Name  ");
    expect(withName).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", line_name: "New Name", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", line_name: "Line B", process_code: "W010" },
    ]);

    const withCode = updateTopologyLineRows(rows, line, "line_code", " line-z ");
    expect(withCode).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-Z", line_name: "Line A", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", line_name: "Line B", process_code: "W010" },
    ]);
  });

  it("updateTopologyWorkshopRows updates company_code and workshop_code for target workshop", () => {
    const rows = [
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-B", line_code: "LINE-B", process_code: "W010" },
    ];
    const workshop = { companyCode: "company-main", workshopCode: "ws-a" };

    const withCompany = updateTopologyWorkshopRows(rows, workshop, "company_code", "company-x");
    expect(withCompany).toEqual([
      { company_code: "COMPANY-X", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-B", line_code: "LINE-B", process_code: "W010" },
    ]);

    const withWorkshop = updateTopologyWorkshopRows(rows, workshop, "workshop_code", " ws-z ");
    expect(withWorkshop).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "ws-z", line_code: "LINE-A", process_code: "W010" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-B", line_code: "LINE-B", process_code: "W010" },
    ]);
  });

  it("updateLineTopologyConfigRows updates target row with field-specific normalization", () => {
    const rows = [
      {
        company_code: "COMPANY-MAIN",
        workshop_code: "WS-A",
        line_code: "LINE-A",
        process_code: "W010",
        capacity_per_shift: "1",
        enabled_flag: 1,
      },
      {
        company_code: "COMPANY-MAIN",
        workshop_code: "WS-A",
        line_code: "LINE-B",
        process_code: "W010",
        capacity_per_shift: "2",
        enabled_flag: 1,
      },
    ];
    const target = { company_code: "company-main", workshop_code: " ws-a ", line_code: " line-a ", process_code: "w010" };

    expect(updateLineTopologyConfigRows(rows, target, "company_code", "company-x")[0].company_code).toBe("COMPANY-X");
    expect(updateLineTopologyConfigRows(rows, target, "line_code", " line-z ")[0].line_code).toBe("LINE-Z");
    expect(updateLineTopologyConfigRows(rows, target, "enabled_flag", "0")[0].enabled_flag).toBe(0);
    expect(updateLineTopologyConfigRows(rows, target, "capacity_per_shift", "9")[0].capacity_per_shift).toBe("9");
    expect(updateLineTopologyConfigRows(rows, target, "line_name", " Name ")[0].line_name).toBe(" Name ");
    expect(updateLineTopologyConfigRows(rows, target, "line_code", " line-z ")[1].line_code).toBe("LINE-B");
  });

  it("resolveSelectedTopologyLineCodeOnLineEdit returns next line code only when selected line is edited", () => {
    expect(
      resolveSelectedTopologyLineCodeOnLineEdit(
        " line-a ",
        { lineCode: "LINE-A" },
        " line-z ",
      ),
    ).toBe("LINE-Z");

    expect(
      resolveSelectedTopologyLineCodeOnLineEdit(
        "LINE-B",
        { lineCode: "LINE-A" },
        "LINE-Z",
      ),
    ).toBeNull();

    expect(
      resolveSelectedTopologyLineCodeOnLineEdit(
        "LINE-A",
        { lineCode: "" },
        "LINE-Z",
      ),
    ).toBeNull();
  });

  it("buildTopologyLineProcessRow builds normalized process row for topology line", () => {
    expect(
      buildTopologyLineProcessRow(
        {
          companyCode: "company-main",
          workshopCode: " ws-a ",
          lineCode: " line-a ",
          lineName: "Line A",
        },
        " w010 ",
      ),
    ).toEqual({
      company_code: "COMPANY-MAIN",
      workshop_code: "ws-a",
      line_code: "LINE-A",
      line_name: "Line A",
      process_code: "W010",
      capacity_per_shift: "",
      required_workers: "",
      required_machines: "",
      enabled_flag: 1,
    });
  });

  it("buildLineTopologyProcessRow builds normalized row from flat params", () => {
    expect(
      buildLineTopologyProcessRow({
        companyCode: "company-main",
        workshopCode: " ws-x ",
        lineCode: " line-x ",
        lineName: "Line X",
        processCode: " w030 ",
      }),
    ).toEqual({
      company_code: "COMPANY-MAIN",
      workshop_code: "ws-x",
      line_code: "LINE-X",
      line_name: "Line X",
      process_code: "W030",
      capacity_per_shift: "",
      required_workers: "",
      required_machines: "",
      enabled_flag: 1,
    });
  });

  it("filterOutTopologyLineProcessRows removes only the process row in target line", () => {
    const next = filterOutTopologyLineProcessRows(
      [
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W010" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W020" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", process_code: "W010" },
      ],
      { companyCode: "COMPANY-MAIN", workshopCode: "WS-A", lineCode: "line-a" },
      " w010 ",
    );
    expect(next).toEqual([
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W020" },
      { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", process_code: "W010" },
    ]);
  });

  it("buildTopologyLineProcessCodeSet collects normalized unique process codes for target line", () => {
    const codes = buildTopologyLineProcessCodeSet(
      [
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: " w010 " },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "W010" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-A", process_code: "w020" },
        { company_code: "COMPANY-MAIN", workshop_code: "WS-A", line_code: "LINE-B", process_code: "W030" },
      ],
      { companyCode: "company-main", workshopCode: "ws-a", lineCode: "line-a" },
    );
    expect([...codes]).toEqual(["W010", "W020"]);
  });

  it("resolveTopologyLineProcessToggleAction returns expected decision", () => {
    const currentCodes = new Set(["W010", "W020"]);
    expect(resolveTopologyLineProcessToggleAction(currentCodes, "w010", true)).toBe("noop");
    expect(resolveTopologyLineProcessToggleAction(currentCodes, "w030", true)).toBe("add");
    expect(resolveTopologyLineProcessToggleAction(currentCodes, "w020", false)).toBe("remove");
    expect(resolveTopologyLineProcessToggleAction(new Set(["W010"]), "w010", false)).toBe("block_remove_last");
    expect(resolveTopologyLineProcessToggleAction(currentCodes, "", true)).toBe("invalid");
  });
});
