export const TASK_BORDER_CLASS_NAMES = [
  "lite-cal-task-border-0",
  "lite-cal-task-border-1",
  "lite-cal-task-border-2",
  "lite-cal-task-border-3",
  "lite-cal-task-border-4",
  "lite-cal-task-border-5",
];

export function resolveTaskColorIndex(taskId) {
  const text = String(taskId || "").trim();
  if (!text) {
    return 0;
  }
  let hash = 0;
  for (let idx = 0; idx < text.length; idx += 1) {
    hash = (hash * 31 + text.charCodeAt(idx)) % 2147483647;
  }
  return Math.abs(hash) % TASK_BORDER_CLASS_NAMES.length;
}

export function resolveTaskBorderClass(taskId) {
  const safeIndex = resolveTaskColorIndex(taskId);
  return TASK_BORDER_CLASS_NAMES[safeIndex];
}
