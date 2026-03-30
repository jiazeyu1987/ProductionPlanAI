import { loadList } from "../../services/api";

export function listMesReportingsByPath(path) {
  return loadList(path);
}

