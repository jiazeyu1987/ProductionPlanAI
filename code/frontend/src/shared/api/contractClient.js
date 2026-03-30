import { requestJson } from "./requestCore";

export function contractRequest(path, options = {}) {
  return requestJson(path, { ...options, authMode: "contract" });
}

export function contractGet(path) {
  return contractRequest(path, { method: "GET" });
}

export function contractPost(path, body) {
  return contractRequest(path, { method: "POST", body });
}

