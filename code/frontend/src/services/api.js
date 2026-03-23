import { toChineseError } from "../utils/i18n";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:5931";

function makeRequestId() {
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export async function apiRequest(path, options = {}) {
  const {
    method = "GET",
    body,
    contract = true,
    headers = {}
  } = options;

  const requestId = body?.request_id || makeRequestId();
  const finalBody =
    method === "GET" || method === "HEAD"
      ? undefined
      : JSON.stringify({ ...body, request_id: requestId });

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...(contract ? { Authorization: "Bearer mvp-dev-token" } : {}),
        ...headers
      },
      body: finalBody
    });
  } catch (error) {
    throw new Error(toChineseError(error?.message));
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = data?.message || data?.error?.message || `Request failed: ${response.status}`;
    throw new Error(toChineseError(message, response.status));
  }
  return data;
}

export function loadList(path) {
  return apiRequest(path, { method: "GET", contract: true });
}

export function postContract(path, body) {
  return apiRequest(path, { method: "POST", body, contract: true });
}

export function postLegacy(path, body) {
  return apiRequest(path, { method: "POST", body, contract: false });
}

export async function downloadContractFile(path, fallbackFileName) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: "GET",
      headers: {
        Authorization: "Bearer mvp-dev-token"
      }
    });
  } catch (error) {
    throw new Error(toChineseError(error?.message));
  }

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const message = data?.message || data?.error?.message || `Request failed: ${response.status}`;
    throw new Error(toChineseError(message, response.status));
  }

  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const match = disposition.match(/filename\*?=(?:UTF-8''|")?([^\";]+)\"?/i);
  const fileName = match?.[1] ? decodeURIComponent(match[1]) : fallbackFileName || "report.dat";
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
