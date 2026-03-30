import { formatDateTimeByField } from "../../../utils/datetime";
import { keyByOrder } from "../planReportsUtils";

export default function PlanTable({ title, columns, rows, groupHeaders }) {
  return (
    <div className="plan-table-wrap">
      <table className="plan-table">
        <thead>
          <tr className="plan-title-row">
            <th colSpan={columns.length}>{title}</th>
          </tr>
          {groupHeaders ? (
            <tr>
              {groupHeaders.map((group) => (
                <th key={group.title} colSpan={group.span}>
                  {group.title}
                </th>
              ))}
            </tr>
          ) : null}
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
              <tr key={`${keyByOrder(row)}-${index}`}>
                {columns.map((column) => (
                  <td key={column.key}>
                    {column.render
                      ? column.render(row[column.key], row)
                      : formatDateTimeByField(row[column.key], column.key) ?? "-"}
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

