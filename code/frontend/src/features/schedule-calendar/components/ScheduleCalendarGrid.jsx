import {
  DATE_SHIFT_MODE,
  DATE_SHIFT_MODE_LABEL,
  dayModeButtonClass,
  nightModeButtonClass,
  resolveDateShiftMode
} from "../../schedule/calendarModeUtils";
import { WEEKDAY_LABELS } from "../scheduleCalendarConstants";

export function ScheduleCalendarGrid({
  calendarWeeks,
  dayScheduleByDate,
  selectedDate,
  skipStatutoryHolidays,
  weekendRestMode,
  dateShiftModeByDate,
  onSelectDate,
  onSetDateMode,
  restReasonForDate
}) {
  return (
    <div className="lite-calendar-wrap">
      <div className="lite-calendar-head">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="lite-calendar-weekday">
            {label}
          </div>
        ))}
      </div>
      <div className="lite-calendar-grid">
        {calendarWeeks.flatMap((week, weekIdx) =>
          week.map((date, dayIdx) => {
            if (!date) {
              return (
                <article
                  key={`empty-${weekIdx}-${dayIdx}`}
                  className="lite-calendar-cell lite-calendar-cell-empty"
                />
              );
            }
            const daySchedule = dayScheduleByDate[date];
            const effectiveMode = resolveDateShiftMode(
              date,
              skipStatutoryHolidays === true,
              weekendRestMode,
              dateShiftModeByDate
            );
            const isRest = effectiveMode === DATE_SHIFT_MODE.REST;
            const reason = restReasonForDate(date);
            return (
              <article
                key={`${weekIdx}-${date}`}
                data-testid={`schedule-calendar-day-${date}`}
                className={[
                  "lite-calendar-cell",
                  isRest ? "lite-calendar-cell-rest" : "",
                  date === selectedDate ? "lite-calendar-cell-selected" : "",
                  daySchedule?.taskCount ? "" : "lite-calendar-cell-out"
                ]
                  .filter(Boolean)
                  .join(" ")}
                onClick={() => onSelectDate(date)}
              >
                <div className="lite-calendar-date">{date.slice(8)}</div>
                {isRest ? (
                  <div className="lite-cal-mode-actions">
                    <button
                      type="button"
                      data-testid={`schedule-calendar-set-day-${date}`}
                      className={`lite-cal-mode-btn ${dayModeButtonClass(effectiveMode)}`}
                      onClick={(event) => {
                        event.stopPropagation();
                        onSetDateMode(date, DATE_SHIFT_MODE.DAY);
                      }}
                    >
                      上班
                    </button>
                    <button
                      type="button"
                      data-testid={`schedule-calendar-set-night-${date}`}
                      className={`lite-cal-mode-btn ${nightModeButtonClass(effectiveMode)}`}
                      onClick={(event) => {
                        event.stopPropagation();
                        onSetDateMode(date, DATE_SHIFT_MODE.NIGHT);
                      }}
                    >
                      夜班
                    </button>
                  </div>
                ) : (
                  <>
                    {reason ? <div className="lite-holiday-tag">{reason}</div> : null}
                    <div className="lite-cal-summary">
                      <span>班次模式</span>
                      <strong>{DATE_SHIFT_MODE_LABEL[effectiveMode] || "白班"}</strong>
                    </div>
                    <div className="lite-cal-summary">
                      <span>任务/订单</span>
                      <strong>
                        {daySchedule?.taskCount || 0} / {daySchedule?.orderCount || 0}
                      </strong>
                    </div>
                    <div className="lite-cal-mode-actions">
                      <button
                        type="button"
                        data-testid={`schedule-calendar-set-rest-${date}`}
                        className={`lite-cal-mode-btn ${effectiveMode === DATE_SHIFT_MODE.REST ? "schedule-calendar-mode-btn-active" : ""}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          onSetDateMode(date, DATE_SHIFT_MODE.REST);
                        }}
                      >
                        休息
                      </button>
                      <button
                        type="button"
                        data-testid={`schedule-calendar-set-day-${date}`}
                        className={`lite-cal-mode-btn ${dayModeButtonClass(effectiveMode)}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          onSetDateMode(date, DATE_SHIFT_MODE.DAY);
                        }}
                      >
                        上班
                      </button>
                      <button
                        type="button"
                        data-testid={`schedule-calendar-set-night-${date}`}
                        className={`lite-cal-mode-btn ${nightModeButtonClass(effectiveMode)}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          onSetDateMode(date, DATE_SHIFT_MODE.NIGHT);
                        }}
                      >
                        夜班
                      </button>
                    </div>
                  </>
                )}
              </article>
            );
          })
        )}
      </div>
    </div>
  );
}

