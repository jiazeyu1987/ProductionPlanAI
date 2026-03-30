import { DATE_SHIFT_MODE } from "../../schedule/calendarModeUtils";
import {
  buildMasterdataTopologyHref,
  formatCompactQty,
  resolveTaskBorderClass,
  shiftName
} from "../scheduleCalendarPageUtils";

export function ScheduleCalendarDetailPanel({
  selectedDate,
  selectedDateMode,
  selectedDaySchedule,
  workshopTabs,
  activeWorkshop,
  activeWorkshopCode,
  onSelectWorkshop
}) {
  return (
    <div className="panel schedule-calendar-detail-panel">
      <div className="panel-head">
        <h3>{selectedDate || "-"} 排产明细</h3>
      </div>
      {selectedDateMode === DATE_SHIFT_MODE.REST && selectedDaySchedule?.taskCount > 0 ? (
        <p className="schedule-calendar-warning">
          当前日期设为休息，但该版本仍有排产任务。若要同步规则，请重新生成排产版本。
        </p>
      ) : null}
      <div className="schedule-calendar-tabs" role="tablist" aria-label="车间标签">
        {workshopTabs.map((workshop) => (
          <button
            key={`tab-${workshop.workshopCode}`}
            type="button"
            role="tab"
            aria-selected={activeWorkshop?.workshopCode === workshop.workshopCode}
            data-testid={`schedule-calendar-tab-${workshop.workshopCode}`}
            className={`schedule-calendar-tab-btn ${
              activeWorkshop?.workshopCode === workshop.workshopCode ? "schedule-calendar-tab-btn-active" : ""
            }`}
            onClick={() => onSelectWorkshop(workshop.workshopCode)}
          >
            {workshop.workshopName}
          </button>
        ))}
      </div>
      <div className="schedule-calendar-workshop-grid">
        {activeWorkshop ? (
          <article
            key={`detail-${selectedDate}-${activeWorkshop.key}`}
            className="schedule-calendar-workshop-card"
            data-testid={`schedule-calendar-workshop-${activeWorkshop.workshopCode}`}
          >
            <h4>{activeWorkshop.workshopName}</h4>
            <p className="hint">
              忙线 {activeWorkshop.busyLineCount}/{activeWorkshop.lines.length} · 任务 {activeWorkshop.taskCount} · 订单{" "}
              {activeWorkshop.orderCount}
            </p>
            <div className="schedule-calendar-line-list">
              {activeWorkshop.lines.map((line) => (
                <div key={`${selectedDate}-${line.key}`} className="schedule-calendar-line-row">
                  <a
                    className="schedule-calendar-line-name-btn"
                    data-testid={`schedule-calendar-line-select-${line.workshopCode}-${line.lineCode}`}
                    href={buildMasterdataTopologyHref(line)}
                    title="跳转到主数据页编辑该产线工艺"
                  >
                    {line.lineName}
                  </a>
                  <div className="schedule-calendar-line-orders">
                    {line.items.length === 0 ? (
                      <span className="hint">空闲</span>
                    ) : (
                      line.items.map((item, index) => (
                        <span
                          key={`${line.key}-${item.id}-${index}`}
                          className={`lite-cal-order-chip ${resolveTaskBorderClass(item.orderNo)}`}
                        >
                          <span className="lite-cal-order-main">{item.orderNo}</span>
                          <span className="lite-cal-order-meta">
                            {item.processName} · {shiftName(item.shiftCode)} · 数量:{formatCompactQty(item.planQty)}
                          </span>
                        </span>
                      ))
                    )}
                  </div>
                </div>
              ))}
            </div>
            <p className="hint">点击产线名称将跳转到主数据-产线拓扑进行查看与编辑。</p>
          </article>
        ) : (
          <p className="hint">暂无车间排产信息。</p>
        )}
      </div>
    </div>
  );
}

