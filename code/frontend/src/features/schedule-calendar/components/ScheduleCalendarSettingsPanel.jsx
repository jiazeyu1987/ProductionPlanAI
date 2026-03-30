import { WEEKEND_REST_MODE_OPTIONS } from "../../schedule/calendarModeUtils";

export function ScheduleCalendarSettingsPanel({
  loadingInit,
  selectedVersionNo,
  versionOptions,
  onChangeVersion,
  onRefreshVersionsAndConfig,
  skipStatutoryHolidays,
  onToggleSkipStatutoryHolidays,
  weekendRestMode,
  onChangeWeekendRestMode,
  error,
  topology
}) {
  return (
    <div className="panel schedule-calendar-settings">
      <h3>设置</h3>
      <div className="toolbar schedule-calendar-controls schedule-calendar-controls-main">
        <label>
          排产版本
          <select
            data-testid="schedule-calendar-version-select"
            value={selectedVersionNo}
            onChange={(e) => onChangeVersion(e.target.value)}
            disabled={loadingInit}
          >
            <option value="">请选择版本</option>
            {versionOptions.map((item) => (
              <option key={item.versionNo} value={item.versionNo}>
                {item.versionNo} / {item.statusName}
              </option>
            ))}
          </select>
        </label>
        <button type="button" onClick={onRefreshVersionsAndConfig}>
          刷新配置
        </button>
      </div>
      <div className="toolbar schedule-calendar-controls schedule-calendar-controls-rules">
        <label className="schedule-calendar-inline-check">
          <input
            data-testid="schedule-calendar-skip-holidays-toggle"
            type="checkbox"
            checked={skipStatutoryHolidays}
            onChange={(e) => onToggleSkipStatutoryHolidays(e.target.checked)}
          />
          智能跳过法定节假日
        </label>
        <div className="toolbar-choice-group schedule-calendar-weekend-group">
          {WEEKEND_REST_MODE_OPTIONS.map((option) => (
            <label className="lite-mode-option" key={option.value}>
              <input
                type="radio"
                name="calendar-weekend-mode"
                checked={weekendRestMode === option.value}
                onChange={() => onChangeWeekendRestMode(option.value)}
                data-testid={`schedule-calendar-weekend-${option.value.toLowerCase()}`}
              />
              {option.label}
            </label>
          ))}
        </div>
      </div>
      {error ? <p className="error">{error}</p> : null}
      {topology?.isFallback ? (
        <p className="hint">
          当前未配置车间/产线拓扑，已使用默认展示：4 个车间，每个车间 4 条产线（仅用于页面展示，不会写回配置）。
        </p>
      ) : null}
    </div>
  );
}

