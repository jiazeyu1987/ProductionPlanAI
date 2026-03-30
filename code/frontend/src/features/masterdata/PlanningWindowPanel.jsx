export default function PlanningWindowPanel({
  horizonStartDate,
  horizonDays,
  onPlanningWindowChange,
  skipStatutoryHolidays,
  onSkipStatutoryHolidaysChange,
  weekendOptions,
  selectedWeekendRestMode,
  onWeekendRestModeChange,
  holidayYearHintText,
}) {
  return (
    <div className="panel">
      <h3>排程窗口（起始日/天数）</h3>
      <div className="toolbar">
        <span className="hint">起始日</span>
        <input type="date" value={horizonStartDate || ""} onChange={(e) => onPlanningWindowChange("horizonStartDate", e.target.value)} />
        <span className="hint">天数</span>
        <input
          type="number"
          min="1"
          max="90"
          step="1"
          value={horizonDays ?? ""}
          onChange={(e) => onPlanningWindowChange("horizonDays", e.target.value)}
        />
        <span className="hint">班次</span>
        <span>固定双班（白班/夜班）</span>
        <label className={`toolbar-check lite-pill-check ${skipStatutoryHolidays ? "is-active" : ""}`}>
          <input
            type="checkbox"
            checked={skipStatutoryHolidays === true}
            onChange={(e) => onSkipStatutoryHolidaysChange(e.target.checked)}
          />
          跳过节假日
        </label>
        <div className="toolbar-choice-group">
          <span className="hint lite-toolbar-subtitle">周末模式</span>
          {weekendOptions.map((option) => (
            <label
              key={option.value}
              className={`toolbar-check lite-pill-check lite-mode-option ${selectedWeekendRestMode === option.value ? "is-active" : ""}`}
            >
              <input
                type="checkbox"
                checked={selectedWeekendRestMode === option.value}
                onChange={(e) => {
                  if (!e.target.checked) {
                    return;
                  }
                  onWeekendRestModeChange(option.value);
                }}
              />
              {option.label}
            </label>
          ))}
        </div>
        <span className="hint lite-year-hint">法定节假日年份：{holidayYearHintText}</span>
      </div>
      <p className="hint">保存后会影响后续新生成的排产版本；具体某天排白班/夜班请在下方日历设置。</p>
    </div>
  );
}
