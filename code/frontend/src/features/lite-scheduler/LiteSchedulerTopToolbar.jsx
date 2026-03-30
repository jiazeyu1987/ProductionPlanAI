export function LiteSchedulerTopToolbar({
  scenario,
  planningModeOptions,
  weekendRestModeOptions,
  holidayYearHintText,
  onHorizonStartChange,
  onSwitchPlanningMode,
  onSkipHolidaysChange,
  onWeekendModeChange,
  onAdvanceOneDay,
  onReplanFromToday,
  onOpenSnapshotSave,
  onOpenSnapshotLoad,
  onResetScenario,
}) {
  return (
    <div className="toolbar lite-top-toolbar">
      <label className="lite-toolbar-field">
        <span className="lite-toolbar-label">排产起始日</span>
        <input
          type="date"
          data-testid="horizon-start-input"
          value={scenario.horizonStart}
          onChange={(e) => onHorizonStartChange(e.target.value)}
        />
      </label>

      <div className="toolbar-choice-group" data-testid="planning-mode-group">
        <span className="hint lite-toolbar-subtitle">排产模式</span>
        {planningModeOptions.map((option) => (
          <label
            className={`toolbar-check lite-pill-check lite-mode-option ${scenario.planningMode === option.value ? "is-active" : ""}`}
            key={option.value}
          >
            <input
              type="checkbox"
              data-testid={option.testId}
              checked={scenario.planningMode === option.value}
              onChange={(e) => {
                if (!e.target.checked) {
                  return;
                }
                onSwitchPlanningMode(option.value);
              }}
            />
            {option.label}
          </label>
        ))}
      </div>

      <label
        className={`toolbar-check lite-pill-check ${scenario.skipStatutoryHolidays ? "is-active" : ""}`}
      >
        <input
          type="checkbox"
          data-testid="skip-holidays-toggle"
          checked={scenario.skipStatutoryHolidays === true}
          onChange={(e) => onSkipHolidaysChange(e.target.checked)}
        />
        跳过法定节假日
      </label>

      <div className="toolbar-choice-group" data-testid="weekend-rest-mode-group">
        <span className="hint lite-toolbar-subtitle">周末模式</span>
        {weekendRestModeOptions.map((option) => (
          <label
            className={`toolbar-check lite-pill-check lite-mode-option ${scenario.weekendRestMode === option.value ? "is-active" : ""}`}
            key={option.value}
          >
            <input
              type="checkbox"
              data-testid={`weekend-mode-${option.value.toLowerCase()}`}
              checked={scenario.weekendRestMode === option.value}
              onChange={(e) => {
                if (!e.target.checked) {
                  return;
                }
                onWeekendModeChange(option);
              }}
            />
            {option.label}
          </label>
        ))}
      </div>

      <span className="hint lite-year-hint" data-testid="holiday-years-hint">
        内置法定节假日年份：{holidayYearHintText}
      </span>

      <div className="lite-toolbar-actions">
        <button
          className="lite-action-primary"
          data-testid="advance-day-btn"
          onClick={onAdvanceOneDay}
        >
          推进1天
        </button>
        <button
          className="lite-action-secondary"
          data-testid="replan-today-btn"
          onClick={onReplanFromToday}
        >
          从今天重排
        </button>
        <button
          className="lite-action-soft"
          data-testid="save-snapshot-btn"
          onClick={onOpenSnapshotSave}
        >
          保存场景
        </button>
        <button
          className="lite-action-soft"
          data-testid="load-snapshot-btn"
          onClick={onOpenSnapshotLoad}
        >
          读取场景
        </button>
        <button className="btn-danger-text lite-action-danger" onClick={onResetScenario}>
          重置默认
        </button>
      </div>
    </div>
  );
}
