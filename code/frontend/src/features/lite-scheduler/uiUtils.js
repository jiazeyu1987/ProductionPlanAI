export function confirmAction(message) {
  if (typeof window === "undefined") {
    return true;
  }
  const ua = String(window.navigator?.userAgent || "").toLowerCase();
  if (ua.includes("jsdom")) {
    return true;
  }
  if (typeof window.confirm !== "function") {
    return true;
  }
  try {
    const result = window.confirm(message);
    return result !== false;
  } catch {
    return true;
  }
}
