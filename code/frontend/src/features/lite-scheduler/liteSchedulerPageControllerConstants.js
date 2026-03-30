import {
  PLANNING_MODE,
  WEEKEND_REST_MODE,
} from "../../utils/liteSchedulerEngine";

export const WEEKEND_REST_MODE_OPTIONS = [
  { value: WEEKEND_REST_MODE.NONE, label: "无休" },
  { value: WEEKEND_REST_MODE.SINGLE, label: "单休" },
  { value: WEEKEND_REST_MODE.DOUBLE, label: "双休" },
];

export const PLANNING_MODE_OPTIONS = [
  {
    value: PLANNING_MODE.QTY_CAPACITY,
    label: "按数量排产",
    testId: "planning-mode-qty",
  },
  {
    value: PLANNING_MODE.DURATION_MANUAL_FINISH,
    label: "按天数排产",
    testId: "planning-mode-duration",
  },
];

export const DATE_WORK_MODE = {
  REST: "REST",
  WORK: "WORK",
};

