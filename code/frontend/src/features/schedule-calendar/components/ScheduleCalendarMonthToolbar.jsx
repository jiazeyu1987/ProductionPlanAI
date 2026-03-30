export function ScheduleCalendarMonthToolbar({
  calendarMonth,
  loadingInit,
  loadingTasks,
  onMoveCalendarMonth,
  onSelectCalendarMonth
}) {
  return (
    <div className="toolbar schedule-calendar-month-toolbar">
      <button
        type="button"
        data-testid="schedule-calendar-prev-month-btn"
        onClick={() => onMoveCalendarMonth(-1)}
      >
        上个月
      </button>
      <label>
        月份
        <input
          data-testid="schedule-calendar-month-input"
          type="month"
          value={calendarMonth}
          onChange={(e) => onSelectCalendarMonth(e.target.value)}
        />
      </label>
      <button
        type="button"
        data-testid="schedule-calendar-next-month-btn"
        onClick={() => onMoveCalendarMonth(1)}
      >
        下个月
      </button>
      {loadingInit || loadingTasks ? <span className="hint">正在加载...</span> : null}
    </div>
  );
}

