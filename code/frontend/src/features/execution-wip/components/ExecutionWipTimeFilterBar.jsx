export function ExecutionWipTimeFilterBar({
  startTime,
  endTime,
  onChangeStartTime,
  onChangeEndTime,
  onApply,
  onClear
}) {
  return (
    <div className="toolbar">
      <input
        type="datetime-local"
        value={startTime}
        onChange={(e) => onChangeStartTime(e.target.value)}
        aria-label="start-time-filter"
      />
      <input
        type="datetime-local"
        value={endTime}
        onChange={(e) => onChangeEndTime(e.target.value)}
        aria-label="end-time-filter"
      />
      <button onClick={onApply}>按时间过滤</button>
      <button onClick={onClear}>清空时间</button>
    </div>
  );
}

