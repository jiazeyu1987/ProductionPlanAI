import { shiftDateKey, todayDateKey } from "../dashboardFormatters";

export function DashboardDateToolbar({ selectedDateKey, onChangeDateKey }) {
  return (
    <div className="toolbar">
      <button type="button" onClick={() => onChangeDateKey(shiftDateKey(selectedDateKey, -1))}>
        上一天
      </button>
      <input
        type="date"
        value={selectedDateKey}
        onChange={(e) => onChangeDateKey(e.target.value ? e.target.value : todayDateKey())}
      />
      <button type="button" onClick={() => onChangeDateKey(shiftDateKey(selectedDateKey, 1))}>
        下一天
      </button>
    </div>
  );
}

