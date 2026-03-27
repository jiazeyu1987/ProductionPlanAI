import { formatDateTimeByField } from "../utils/datetime";

export default function SimpleTable({ columns, rows, className = "", rowKey }) {
  function resolveRowKey(row, index) {
    if (typeof rowKey === "function") {
      return rowKey(row, index);
    }
    if (typeof rowKey === "string" && row[rowKey] !== undefined && row[rowKey] !== null) {
      return row[rowKey];
    }
    return row.id || row.request_id || row.order_no || `${index}`;
  }

  return (
    <div className={`table-wrap ${className}`.trim()}>
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.title}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>暂无数据</td>
            </tr>
          ) : (
            rows.map((row, index) => (
              <tr key={resolveRowKey(row, index)}>
                {columns.map((column) => (
                  <td key={column.key}>
                    {column.render
                      ? column.render(row[column.key], row)
                      : String(formatDateTimeByField(row[column.key], column.key) ?? "-")}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
