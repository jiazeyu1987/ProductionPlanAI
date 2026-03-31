import { ScheduleCalendarDetailPanel } from "../features/schedule-calendar/components/ScheduleCalendarDetailPanel";
import { ScheduleCalendarGrid } from "../features/schedule-calendar/components/ScheduleCalendarGrid";
import { ScheduleCalendarMonthToolbar } from "../features/schedule-calendar/components/ScheduleCalendarMonthToolbar";
import { ScheduleCalendarPageHeader } from "../features/schedule-calendar/components/ScheduleCalendarPageHeader";
import { ScheduleCalendarSettingsPanel } from "../features/schedule-calendar/components/ScheduleCalendarSettingsPanel";
import { useScheduleCalendarController } from "../features/schedule-calendar/useScheduleCalendarController";

export default function ScheduleCalendarPage() {
  const {
    selectedVersionNo,
    setSelectedVersionNo,
    skipStatutoryHolidays,
    setSkipStatutoryHolidays,
    weekendRestMode,
    setWeekendRestMode,
    dateShiftModeByDate,
    calendarMonth,
    selectedDate,
    setSelectedDate,
    loadingInit,
    loadingTasks,
    savingRules,
    replanning,
    activeWorkshopCode,
    setActiveWorkshopCode,
    error,

    topology,
    dayScheduleByDate,
    calendarWeeks,
    selectedDaySchedule,
    workshopTabs,
    activeWorkshop,
    versionOptions,
    selectedDateMode,

    refreshVersionsAndConfig,
    moveCalendarMonth,
    selectCalendarMonth,
    restReasonForDate,
    setDateMode,
    saveAndReplan
  } = useScheduleCalendarController();

  return (
    <section className="schedule-calendar-page">
      <ScheduleCalendarPageHeader
        savingRules={savingRules}
        replanning={replanning}
        selectedVersionNo={selectedVersionNo}
        onSave={saveAndReplan}
      />

      <div className="schedule-calendar-layout">
        <div className="panel schedule-calendar-left">
          <div className="panel-head">
            <h3>月历视图</h3>
            <ScheduleCalendarMonthToolbar
              calendarMonth={calendarMonth}
              loadingInit={loadingInit}
              loadingTasks={loadingTasks}
              onMoveCalendarMonth={moveCalendarMonth}
              onSelectCalendarMonth={selectCalendarMonth}
            />
          </div>

          <ScheduleCalendarGrid
            calendarWeeks={calendarWeeks}
            dayScheduleByDate={dayScheduleByDate}
            selectedDate={selectedDate}
            skipStatutoryHolidays={skipStatutoryHolidays}
            weekendRestMode={weekendRestMode}
            dateShiftModeByDate={dateShiftModeByDate}
            onSelectDate={setSelectedDate}
            onSetDateMode={setDateMode}
            restReasonForDate={restReasonForDate}
          />
        </div>

        <div className="schedule-calendar-right">
          <ScheduleCalendarSettingsPanel
            loadingInit={loadingInit}
            selectedVersionNo={selectedVersionNo}
            versionOptions={versionOptions}
            onChangeVersion={setSelectedVersionNo}
            onRefreshVersionsAndConfig={() => refreshVersionsAndConfig(true)}
            skipStatutoryHolidays={skipStatutoryHolidays}
            onToggleSkipStatutoryHolidays={setSkipStatutoryHolidays}
            weekendRestMode={weekendRestMode}
            onChangeWeekendRestMode={setWeekendRestMode}
            error={error}
            topology={topology}
          />

          <ScheduleCalendarDetailPanel
            selectedDate={selectedDate}
            selectedDateMode={selectedDateMode}
            selectedDaySchedule={selectedDaySchedule}
            workshopTabs={workshopTabs}
            activeWorkshop={activeWorkshop}
            activeWorkshopCode={activeWorkshopCode}
            onSelectWorkshop={setActiveWorkshopCode}
          />
        </div>
      </div>
    </section>
  );
}
