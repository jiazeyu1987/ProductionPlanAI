import SimpleTable from "../../components/SimpleTable";
import { formatNumber, formatPercent } from "./numberFormatUtils";

export function LiteSchedulerLinePanel({
  active,
  lineForm,
  setLineForm,
  addLine,
  lineRows,
  updateLineName,
  updateLineBaseCapacity,
  removeLine,
}) {
  if (!active) {
    return null;
  }

  return (
    <div className="panel">
      <h3>产线管理</h3>
      <div className="toolbar">
        <label>
          产线名称
          <input
            placeholder="例如：导管产线2"
            value={lineForm.name}
            onChange={(e) =>
              setLineForm((prev) => ({ ...prev, name: e.target.value }))
            }
          />
        </label>
        <label>
          默认日产能（个/天）
          <input
            type="number"
            min="0"
            step="0.1"
            value={lineForm.baseCapacity}
            onChange={(e) =>
              setLineForm((prev) => ({
                ...prev,
                baseCapacity: e.target.value,
              }))
            }
          />
        </label>
        <button onClick={addLine}>新增产线</button>
      </div>

      <SimpleTable
        columns={[
          {
            key: "line_name",
            title: "产线",
            render: (value, row) => (
              <input
                defaultValue={String(value || "")}
                onBlur={(e) => updateLineName(row.id, e.target.value)}
              />
            ),
          },
          {
            key: "base_capacity",
            title: "默认日产能（个/天）",
            render: (value, row) => (
              <input
                type="number"
                min="0"
                step="0.1"
                defaultValue={String(value)}
                onBlur={(e) => updateLineBaseCapacity(row.id, e.target.value)}
              />
            ),
          },
          {
            key: "assigned_total",
            title: "周期内分配（个）",
            render: (value) => formatNumber(value),
          },
          {
            key: "capacity_total",
            title: "周期内最大产能（个）",
            render: (value) => formatNumber(value),
          },
          {
            key: "utilization",
            title: "利用率",
            render: (value) => formatPercent(value),
          },
          {
            key: "actions",
            title: "操作",
            render: (_, row) => (
              <button
                className="btn-danger-text"
                onClick={() => removeLine(row.id)}
              >
                删除
              </button>
            ),
          },
        ]}
        rows={lineRows}
      />
    </div>
  );
}
