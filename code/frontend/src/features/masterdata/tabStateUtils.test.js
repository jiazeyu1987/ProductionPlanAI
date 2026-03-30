import { describe, expect, it } from "vitest";
import {
  resolveActiveTabFromQuery,
  resolveConfigSubTabForActiveTab,
  resolveConfigSubTabFromQuery,
} from "./tabStateUtils";

describe("tabStateUtils", () => {
  const constants = {
    routeTab: "route",
    configTab: "config",
    deviceConfigTab: "device_config",
    equipmentTab: "equipment",
    configSubTopology: "topology",
    configSubProcess: "process",
    configSubMaterial: "material",
  };

  it("prefers device tab when query sub-tab is topology/process", () => {
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "route",
        configSubParam: "topology",
      }),
    ).toBe("device_config");
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "config",
        configSubParam: "process",
      }),
    ).toBe("device_config");
  });

  it("maps equipment and device query tabs to device tab", () => {
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "equipment",
        configSubParam: "",
      }),
    ).toBe("device_config");
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "device_config",
        configSubParam: "",
      }),
    ).toBe("device_config");
  });

  it("keeps explicit route/config query tab", () => {
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "route",
        configSubParam: "",
      }),
    ).toBe("route");
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "config",
        configSubParam: "",
      }),
    ).toBe("config");
  });

  it("returns null for unsupported query tab", () => {
    expect(
      resolveActiveTabFromQuery({
        ...constants,
        tabParam: "unknown",
        configSubParam: "",
      }),
    ).toBeNull();
  });

  it("resolves config sub-tab from allow-list", () => {
    expect(resolveConfigSubTabFromQuery("material", ["material"])).toBe("material");
    expect(resolveConfigSubTabFromQuery("topology", ["material"])).toBeNull();
  });

  it("forces topology sub-tab under device tab and material sub-tab under config tab", () => {
    expect(
      resolveConfigSubTabForActiveTab({
        ...constants,
        activeTab: "device_config",
        activeConfigSubTab: "material",
      }),
    ).toBe("topology");

    expect(
      resolveConfigSubTabForActiveTab({
        ...constants,
        activeTab: "config",
        activeConfigSubTab: "topology",
      }),
    ).toBe("material");
  });

  it("returns null when no adjustment is needed", () => {
    expect(
      resolveConfigSubTabForActiveTab({
        ...constants,
        activeTab: "config",
        activeConfigSubTab: "material",
      }),
    ).toBeNull();
  });
});
