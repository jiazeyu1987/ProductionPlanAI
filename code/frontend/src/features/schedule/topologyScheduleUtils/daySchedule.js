import { shiftSortIndex } from "./shift";

function createDayDraft(date, workshopTemplates) {
  const workshops = workshopTemplates.map((workshop) => ({
    ...workshop,
    lines: workshop.lines.map((line) => ({
      ...line,
      items: []
    }))
  }));
  const lineByKey = new Map();
  for (const workshop of workshops) {
    for (const line of workshop.lines) {
      lineByKey.set(line.key, line);
    }
  }
  return {
    date,
    workshops,
    lineByKey,
    orderSet: new Set(),
    taskCount: 0
  };
}

function sortLineItems(items) {
  return items.slice().sort((a, b) => {
    const byShift = shiftSortIndex(a.shiftCode) - shiftSortIndex(b.shiftCode);
    if (byShift !== 0) {
      return byShift;
    }
    if (a.processCode !== b.processCode) {
      return String(a.processCode).localeCompare(String(b.processCode), "zh-Hans-CN");
    }
    return String(a.orderNo).localeCompare(String(b.orderNo), "zh-Hans-CN");
  });
}

function finalizeDayDraft(draft) {
  const workshops = draft.workshops.map((workshop) => {
    const lines = workshop.lines.map((line) => ({
      ...line,
      items: sortLineItems(line.items)
    }));
    const busyLineCount = lines.filter((line) => line.items.length > 0).length;
    const orderSet = new Set();
    let taskCount = 0;
    for (const line of lines) {
      taskCount += line.items.length;
      for (const item of line.items) {
        orderSet.add(item.orderNo);
      }
    }
    return {
      ...workshop,
      lines,
      busyLineCount,
      taskCount,
      orderCount: orderSet.size
    };
  });
  const busyWorkshopCount = workshops.filter((workshop) => workshop.busyLineCount > 0).length;
  const busyLineCount = workshops.reduce((sum, workshop) => sum + workshop.busyLineCount, 0);
  const taskCount = workshops.reduce((sum, workshop) => sum + workshop.taskCount, 0);
  const orderCount = draft.orderSet.size;
  return {
    date: draft.date,
    workshops,
    busyWorkshopCount,
    busyLineCount,
    taskCount,
    orderCount
  };
}

export function buildDayScheduleMap(taskRows, topology) {
  const map = {};
  if (!topology || !Array.isArray(topology.workshops)) {
    return map;
  }
  const processCursor = new Map();
  for (const task of Array.isArray(taskRows) ? taskRows : []) {
    const date = String(task?.date || "").trim();
    if (!date) {
      continue;
    }
    if (!map[date]) {
      map[date] = createDayDraft(date, topology.workshops);
    }
    const draft = map[date];
    const processCode = String(task.processCode || "").trim().toUpperCase();
    const candidates = topology.processToLines.get(processCode) || topology.allLineKeys;
    if (!Array.isArray(candidates) || candidates.length === 0) {
      continue;
    }
    const cursorKey = `${date}#${processCode}`;
    const cursor = processCursor.get(cursorKey) || 0;
    const lineKey = candidates[cursor % candidates.length];
    processCursor.set(cursorKey, cursor + 1);
    const line = draft.lineByKey.get(lineKey);
    if (!line) {
      continue;
    }
    line.items.push(task);
    draft.orderSet.add(task.orderNo);
    draft.taskCount += 1;
  }
  const out = {};
  for (const [date, draft] of Object.entries(map)) {
    out[date] = finalizeDayDraft(draft);
  }
  return out;
}

export function buildEmptyDaySchedule(date, workshops) {
  const base = createDayDraft(date, workshops || []);
  return finalizeDayDraft(base);
}

