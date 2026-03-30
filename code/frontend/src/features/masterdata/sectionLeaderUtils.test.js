import { describe, expect, it } from "vitest";
import {
  buildNewSectionLeaderBinding,
  buildSectionLeaderLineMetaMap,
  sortSectionLeaderBindingRows,
  updateSectionLeaderBindingField,
  updateSectionLeaderBindingRows,
} from "./sectionLeaderUtils";

describe("sectionLeaderUtils", () => {
  it("sortSectionLeaderBindingRows sorts by leader_id then line_code", () => {
    const sorted = sortSectionLeaderBindingRows([
      { leader_id: "LEADER-002", line_code: "LINE-A" },
      { leader_id: "LEADER-001", line_code: "LINE-B" },
      { leader_id: "LEADER-001", line_code: "LINE-A" },
    ]);
    expect(sorted).toEqual([
      { leader_id: "LEADER-001", line_code: "LINE-A" },
      { leader_id: "LEADER-001", line_code: "LINE-B" },
      { leader_id: "LEADER-002", line_code: "LINE-A" },
    ]);
  });

  it("buildSectionLeaderLineMetaMap normalizes line code and keeps first item", () => {
    const map = buildSectionLeaderLineMetaMap([
      { lineCode: " line-a ", lineName: "LINE-A-NAME", companyCode: "co-a", workshopCode: " ws-1 " },
      { lineCode: "LINE-A", lineName: "Ignored", companyCode: "co-b", workshopCode: "ws-2" },
    ]);

    expect(map.size).toBe(1);
    expect(map.get("LINE-A")).toEqual({
      lineCode: "LINE-A",
      lineName: "LINE-A-NAME",
      companyCode: "CO-A",
      workshopCode: "WS-1",
    });
  });

  it("updateSectionLeaderBindingField updates line relation fields by line_code", () => {
    const lineMetaByCode = buildSectionLeaderLineMetaMap([
      { lineCode: "LINE-B", lineName: "LINE-B-NAME", companyCode: "company-main", workshopCode: "ws-b" },
    ]);
    const next = updateSectionLeaderBindingField(
      {
        leader_id: "LEADER-001",
        line_code: "LINE-A",
        line_name: "LINE-A-NAME",
        company_code: "COMPANY-A",
        workshop_code: "WS-A",
      },
      "line_code",
      " line-b ",
      lineMetaByCode,
    );

    expect(next).toEqual({
      leader_id: "LEADER-001",
      line_code: "LINE-B",
      line_name: "LINE-B-NAME",
      company_code: "COMPANY-MAIN",
      workshop_code: "WS-B",
    });
  });

  it("updateSectionLeaderBindingField normalizes leader_id and active_flag", () => {
    const row = {
      leader_id: "LEADER-001",
      active_flag: 1,
    };
    expect(updateSectionLeaderBindingField(row, "leader_id", " leader-009 ")).toEqual({
      leader_id: "LEADER-009",
      active_flag: 1,
    });
    expect(updateSectionLeaderBindingField(row, "active_flag", "0")).toEqual({
      leader_id: "LEADER-001",
      active_flag: 0,
    });
  });

  it("updateSectionLeaderBindingRows updates only the matched row", () => {
    const lineMetaByCode = buildSectionLeaderLineMetaMap([
      { lineCode: "LINE-Z", lineName: "LINE-Z-NAME", companyCode: "company-main", workshopCode: "ws-z" },
    ]);
    const next = updateSectionLeaderBindingRows(
      [
        { leader_id: "LEADER-001", line_code: "LINE-A", line_name: "A", company_code: "CO-A", workshop_code: "WS-A", active_flag: 1 },
        { leader_id: "LEADER-001", line_code: "LINE-B", line_name: "B", company_code: "CO-B", workshop_code: "WS-B", active_flag: 1 },
      ],
      { leader_id: "leader-001", line_code: " line-a " },
      "line_code",
      "line-z",
      lineMetaByCode,
    );
    expect(next).toEqual([
      { leader_id: "LEADER-001", line_code: "LINE-Z", line_name: "LINE-Z-NAME", company_code: "COMPANY-MAIN", workshop_code: "WS-Z", active_flag: 1 },
      { leader_id: "LEADER-001", line_code: "LINE-B", line_name: "B", company_code: "CO-B", workshop_code: "WS-B", active_flag: 1 },
    ]);
  });

  it("buildNewSectionLeaderBinding builds default normalized row", () => {
    const built = buildNewSectionLeaderBinding(
      {
        lineCode: " line-x ",
        lineName: "LINE-X-NAME",
        companyCode: "company-main",
        workshopCode: " ws-x ",
      },
      "2",
    );

    expect(built).toMatchObject({
      leader_id: "LEADER-NEW-3",
      line_code: "LINE-X",
      line_name: "LINE-X-NAME",
      company_code: "COMPANY-MAIN",
      workshop_code: "WS-X",
      active_flag: 1,
    });
    expect(built.leader_name).toContain("?{prev.sectionLeaderBindings.length + 1}");
  });
});
