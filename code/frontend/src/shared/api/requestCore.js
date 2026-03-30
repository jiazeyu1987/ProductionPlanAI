import { toChineseError } from "../../utils/i18n";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:5931";
const CONTRACT_TOKEN = "Bearer mvp-dev-token";

function makeRequestId() {
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function resolveAuthHeaders(authMode) {
  if (authMode === "contract") {
    return { Authorization: CONTRACT_TOKEN };
  }
  return {};
}

function buildJsonBody(method, body) {
  if (method === "GET" || method === "HEAD") {
    return undefined;
  }
  const requestId = body?.request_id || makeRequestId();
  return JSON.stringify({
    ...(body ?? {}),
    request_id: requestId,
  });
}

export async function requestJson(path, options = {}) {
  const {
    method = "GET",
    body,
    headers = {},
    authMode = "contract",
  } = options;

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...resolveAuthHeaders(authMode),
        ...headers,
      },
      body: buildJsonBody(method, body),
    });
  } catch (error) {
    throw new Error(toChineseError(error?.message));
  }

  const rawText = await response.text().catch(() => "");
  let data = {};
  let parsed = false;
  if (rawText) {
    try {
      data = JSON.parse(rawText);
      parsed = true;
    } catch (error) {
      parsed = false;
    }
  }

  if (response.ok && rawText && !parsed) {
    const contentType = response.headers.get("content-type") || "";
    const preview = rawText.replace(/\s+/g, " ").slice(0, 160);
    throw new Error(
      toChineseError(
        `后端返回非 JSON 响应（${response.status}${contentType ? `, ${contentType}` : ""}）：${preview}`,
        response.status,
      ),
    );
  }

  if (!response.ok) {
    const message =
      (parsed ? (data?.message || data?.error?.message) : "") ||
      `Request failed: ${response.status}`;
    throw new Error(toChineseError(message, response.status));
  }
  return data;
}

export async function requestBlob(path, options = {}) {
  const {
    method = "GET",
    headers = {},
    authMode = "contract",
  } = options;

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        ...resolveAuthHeaders(authMode),
        ...headers,
      },
    });
  } catch (error) {
    throw new Error(toChineseError(error?.message));
  }

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const message = data?.message || data?.error?.message || `Request failed: ${response.status}`;
    throw new Error(toChineseError(message, response.status));
  }

  return response;
}
