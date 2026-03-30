import { requestJson } from "./requestCore";

export function legacyRequest(path, options = {}) {
  return requestJson(path, { ...options, authMode: "none" });
}

export function legacyPost(path, body) {
  return legacyRequest(path, { method: "POST", body });
}

