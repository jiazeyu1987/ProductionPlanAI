export function ScheduleCalendarPageHeader({
  savingRules,
  replanning,
  selectedVersionNo,
  onSave
}) {
  return (
    <div className="schedule-calendar-page-head">
      <h2>月历排期</h2>
      <button
        data-testid="schedule-calendar-save-rules-btn"
        type="button"
        className="btn-success-text"
        disabled={savingRules || replanning || !selectedVersionNo}
        onClick={onSave}
      >
        {savingRules ? "保存中..." : replanning ? "重排中..." : "保存"}
      </button>
    </div>
  );
}

