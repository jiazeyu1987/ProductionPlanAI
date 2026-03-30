import { WEEKEND_REST_MODE } from "../../../utils/liteSchedulerEngine";

export const ROUTE_TAB = "route";
export const EQUIPMENT_TAB = "equipment";
export const CONFIG_TAB = "config";
export const DEVICE_CONFIG_TAB = "device_config";

export const CONFIG_SUB_WINDOW = "window";
export const CONFIG_SUB_PROCESS = "process";
export const CONFIG_SUB_TOPOLOGY = "topology";
export const CONFIG_SUB_LEADER = "leader";
export const CONFIG_SUB_RESOURCE = "resource";
export const CONFIG_SUB_MATERIAL = "material";

export const ROUTE_EDITOR_CREATE = "create";
export const ROUTE_EDITOR_EDIT = "edit";
export const ROUTE_EDITOR_COPY = "copy";

export const SHIFT_OPTIONS = [
  { code: "DAY", name: "白班" },
  { code: "NIGHT", name: "夜班" },
];

export const WEEKEND_REST_MODE_OPTIONS = [
  { value: WEEKEND_REST_MODE.DOUBLE, label: "双休" },
  { value: WEEKEND_REST_MODE.SINGLE, label: "单休" },
  { value: WEEKEND_REST_MODE.NONE, label: "无休" },
];

export const ROUTE_PRODUCT_NAME_BY_CODE = Object.freeze({
  "A006.034.10191": "一次性使用造影导管 Multipurpose-A1 5F",
  "YXN.009.020.1047": "一次性使用造影导管Simmons3 5F",
  "YXN.044.02.1028": "亲水涂层造影导管 HAC-MPA1-06125",
  "YXN.067.005.1006": "亲水涂层血管造影导管 HVAC5F125-Simmons2",
  "A006.034.6104": "一次性使用造影导管 5F C0BRA2",
});

export const HIDDEN_ROUTE_PRODUCT_CODES = new Set([
  "PROD_ANGIO_CATH",
  "PROD_BALLOON",
  "PROD_CATH",
  "PROD_STENT",
]);

