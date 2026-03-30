import { legacyApiRequest, postLegacy } from "../../services/api";

export function createLegacyReporting(payload) {
  return postLegacy("/api/reportings", payload);
}

export function deleteLegacyReporting(reportId) {
  return legacyApiRequest(`/api/reportings/${encodeURIComponent(reportId)}`, {
    method: "DELETE",
    body: {},
  });
}

