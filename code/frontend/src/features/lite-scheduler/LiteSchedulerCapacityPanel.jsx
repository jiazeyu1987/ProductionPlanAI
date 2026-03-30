import SimpleTable from "../../components/SimpleTable";
import { formatNumber } from "./numberFormatUtils";

export function LiteSchedulerCapacityPanel({
  active,
  isDurationMode,
  capacityForm,
  setCapacityForm,
  lines,
  saveDailyCapacity,
  lineDailyCapacityRows,
}) {
  if (!active) {
    return null;
  }

  return (
    <div className="panel">
      <h3>日产能调整</h3>
      {isDurationMode ? (
        <p className="hint">当前为按天数模式，日产能设置不会参与排程计算。</p>
      ) : null}
      <div className="toolbar">
        <label>
          产线
          <select
            value={capacityForm.lineId}
            onChange={(e) => {
              const nextLineId = e.target.value;
              const line = lines.find((row) => row.id === nextLineId);
              setCapacityForm((prev) => ({
                ...prev,
                lineId: nextLineId,
                capacity: String(line?.baseCapacity ?? 0),
              }));
            }}
          >
            {lines.map((line) => (
              <option key={line.id} value={line.id}>
                {line.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          产能(个/天)
          <input
            type="number"
            min="0"
            step="0.1"
            value={capacityForm.capacity}
            onChange={(e) =>
              setCapacityForm((prev) => ({
                ...prev,
                capacity: e.target.value,
              }))
            }
          />
        </label>

        <button className="btn-success-text" onClick={saveDailyCapacity}>
          保存
        </button>
      </div>

      <SimpleTable
        columns={[
          { key: "line_name", title: "产线" },
          {
            key: "daily_capacity",
            title: "日产能（个/天）",
            render: (value) => formatNumber(value),
          },
        ]}
        rows={lineDailyCapacityRows}
      />
    </div>
  );
}
