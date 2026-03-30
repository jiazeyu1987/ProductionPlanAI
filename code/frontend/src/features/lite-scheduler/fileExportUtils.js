function escapeExcelCell(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function buildExcelTableHtml(headers, rows) {
  const headHtml = headers
    .map((header) => `<th>${escapeExcelCell(header)}</th>`)
    .join("");
  const bodyHtml = rows
    .map(
      (row) =>
        `<tr>${row.map((cell) => `<td>${escapeExcelCell(cell)}</td>`).join("")}</tr>`,
    )
    .join("");
  return `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
  </head>
  <body>
    <table border="1">
      <thead><tr>${headHtml}</tr></thead>
      <tbody>${bodyHtml}</tbody>
    </table>
  </body>
</html>`;
}

export function downloadTextFile(text, fileName, mimeType) {
  const blob = new Blob([text], { type: mimeType });
  const urlApi = typeof window !== "undefined" && window.URL ? window.URL : URL;
  const link = document.createElement("a");
  link.download = fileName;
  if (urlApi && typeof urlApi.createObjectURL === "function") {
    const blobUrl = urlApi.createObjectURL(blob);
    link.href = blobUrl;
    document.body.appendChild(link);
    link.click();
    link.remove();
    if (typeof urlApi.revokeObjectURL === "function") {
      urlApi.revokeObjectURL(blobUrl);
    }
    return;
  }
  link.href = `data:${mimeType};charset=utf-8,${encodeURIComponent(text)}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
}
