import { contractGet, contractPost } from "../shared/api/contractClient";
import { downloadContractFile as downloadContractFileImpl } from "../shared/api/fileClient";
import { legacyPost, legacyRequest } from "../shared/api/legacyClient";
import { requestJson } from "../shared/api/requestCore";

export function apiRequest(path, options = {}) {
  const { contract = true, ...rest } = options;
  if (contract) {
    return requestJson(path, { ...rest, authMode: "contract" });
  }
  return requestJson(path, { ...rest, authMode: "none" });
}

export function loadList(path) {
  return contractGet(path);
}

export function postContract(path, body) {
  return contractPost(path, body);
}

export function postLegacy(path, body) {
  return legacyPost(path, body);
}

export function legacyApiRequest(path, options = {}) {
  return legacyRequest(path, options);
}

export function downloadContractFile(path, fallbackFileName) {
  return downloadContractFileImpl(path, fallbackFileName);
}
