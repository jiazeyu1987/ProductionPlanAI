import { requestBlob } from "./requestCore";

export async function downloadContractFile(path, fallbackFileName) {
  const response = await requestBlob(path, { method: "GET", authMode: "contract" });
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

