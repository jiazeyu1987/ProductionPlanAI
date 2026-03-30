import {
  DATE_WORK_MODE,
  PLANNING_MODE_OPTIONS,
  WEEKEND_REST_MODE_OPTIONS,
  formatNumber,
  formatPercent,
  LiteSchedulerCapacityPanel,
  LiteSchedulerFinishModal,
  LiteSchedulerInsertOrderModal,
  LiteSchedulerLinePanel,
  LiteSchedulerOrderModal,
  LiteSchedulerOrdersPanel,
  LiteSchedulerSchedulePanel,
  LiteSchedulerSnapshotModal,
  LiteSchedulerTopToolbar,
  useLiteSchedulerPageController,
} from "../features/lite-scheduler";

const LITE_TAB_ITEMS = [
  { id: "orders", label: "订单录入" },
  { id: "schedule", label: "每日排产" },
  { id: "capacity", label: "产能调整" },
  { id: "lines", label: "产线管理" },
];

export default function LiteSchedulerPage() {
  const {
    scenario,
    calendarMonth,
    message,
    error,
    activeTab,
    setActiveTab,
    isDurationMode,
    holidayYearHintText,
    plan,
    calendarPlan,
    calendarWeeks,
    scheduleDateSet,
    orderMetaMap,
    lineOrderColorClassMap,
    lineForm,
    setLineForm,
    capacityForm,
    setCapacityForm,
    insertForm,
    setInsertForm,
    lineRows,
    lineDailyCapacityRows,
    orderRows,
    snapshotRows,
    modalTotalWorkload,
    modalTotalPlanQty,
    showOrderModal,
    editingOrderId,
    orderModalForm,
    setOrderModalForm,
    showInsertModal,
    showSnapshotModal,
    snapshotModalMode,
    snapshotName,
    setSnapshotName,
    showFinishModal,
    finishModalForm,
    setFinishModalForm,
    hasSavedFinish,
    addLine,
    updateLineName,
    updateLineBaseCapacity,
    removeLine,
    saveDailyCapacity,
    openOrderModal,
    openEditOrderModal,
    closeOrderModal,
    openInsertModal,
    closeInsertModal,
    submitInsertOrder,
    submitOrderFromModal,
    removeOrder,
    switchPlanningMode,
    setDateWorkMode,
    advanceOneDay,
    replanFromToday,
    resetScenario,
    exportScheduledOrdersExcel,
    selectCalendarMonth,
    moveCalendarMonth,
    openSnapshotModal,
    closeSnapshotModal,
    saveSnapshotToLocal,
    renameSnapshot,
    deleteSnapshot,
    loadSnapshot,
    openFinishModal,
    closeFinishModal,
    submitManualFinish,
    clearManualFinish,
    onHorizonStartChange,
    onSkipHolidaysChange,
    onWeekendModeChange,
  } = useLiteSchedulerPageController();

  return (
    <section className="lite-page">
      <h2>璞慧排产</h2>
      <p className="hint">
        {isDurationMode
          ? "订单按产线计划天数排程，支持在日历手动报结束并自动前后顺延。"
          : "订单录入按“个数”填写，系统同优先级并按订单顺序向后排产。"}
      </p>

      <LiteSchedulerTopToolbar
        scenario={scenario}
        planningModeOptions={PLANNING_MODE_OPTIONS}
        weekendRestModeOptions={WEEKEND_REST_MODE_OPTIONS}
        holidayYearHintText={holidayYearHintText}
        onHorizonStartChange={onHorizonStartChange}
        onSwitchPlanningMode={switchPlanningMode}
        onSkipHolidaysChange={onSkipHolidaysChange}
        onWeekendModeChange={onWeekendModeChange}
        onAdvanceOneDay={advanceOneDay}
        onReplanFromToday={replanFromToday}
        onOpenSnapshotSave={() => openSnapshotModal("save")}
        onOpenSnapshotLoad={() => openSnapshotModal("load")}
        onResetScenario={resetScenario}
      />

      {message ? <p className="notice">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      {plan.warnings.length > 0 ? (
        <div className="panel lite-warning">
          <h3>重排提醒</h3>
          <ul className="lite-warning-list">
            {plan.warnings.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="card-grid">
        <article className="metric-card">
          <span>订单数</span>
          <strong>{plan.summary.totalOrders}</strong>
        </article>
        <article className="metric-card">
          <span>{isDurationMode ? "总排线天 / 总产线天" : "总分配 / 总产能"}</span>
          <strong>
            {formatNumber(plan.summary.totalAssigned)} /{" "}
            {formatNumber(plan.summary.totalCapacity)}
          </strong>
        </article>
        <article className="metric-card">
          <span>利用率</span>
          <strong>{formatPercent(plan.summary.utilization)}</strong>
        </article>
        <article className="metric-card">
          <span>延期订单 / 剩余工作量</span>
          <strong>
            {plan.summary.delayedOrders} / {formatNumber(plan.summary.totalRemaining)}
          </strong>
        </article>
      </div>

      <div className="panel">
        <div className="report-tabs">
          {LITE_TAB_ITEMS.map((tab) => (
            <button
              key={tab.id}
              data-testid={`lite-tab-${tab.id}`}
              className={activeTab === tab.id ? "active" : ""}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <LiteSchedulerLinePanel
        active={activeTab === "lines"}
        lineForm={lineForm}
        setLineForm={setLineForm}
        addLine={addLine}
        lineRows={lineRows}
        updateLineName={updateLineName}
        updateLineBaseCapacity={updateLineBaseCapacity}
        removeLine={removeLine}
      />

      <LiteSchedulerCapacityPanel
        active={activeTab === "capacity"}
        isDurationMode={isDurationMode}
        capacityForm={capacityForm}
        setCapacityForm={setCapacityForm}
        lines={scenario.lines}
        saveDailyCapacity={saveDailyCapacity}
        lineDailyCapacityRows={lineDailyCapacityRows}
      />

      <LiteSchedulerOrdersPanel
        active={activeTab === "orders"}
        isDurationMode={isDurationMode}
        orderRows={orderRows}
        openOrderModal={openOrderModal}
        openInsertModal={openInsertModal}
        openEditOrderModal={openEditOrderModal}
        removeOrder={removeOrder}
      />

      <LiteSchedulerSchedulePanel
        active={activeTab === "schedule"}
        isDurationMode={isDurationMode}
        calendarPlan={calendarPlan}
        calendarMonth={calendarMonth}
        selectCalendarMonth={selectCalendarMonth}
        moveCalendarMonth={moveCalendarMonth}
        exportScheduledOrdersExcel={exportScheduledOrdersExcel}
        calendarWeeks={calendarWeeks}
        scheduleDateSet={scheduleDateSet}
        scenario={scenario}
        orderMetaMap={orderMetaMap}
        lineOrderColorClassMap={lineOrderColorClassMap}
        setDateWorkMode={setDateWorkMode}
        dateWorkMode={DATE_WORK_MODE}
        openFinishModal={openFinishModal}
      />

      <LiteSchedulerSnapshotModal
        show={showSnapshotModal}
        snapshotModalMode={snapshotModalMode}
        snapshotName={snapshotName}
        setSnapshotName={setSnapshotName}
        snapshotRows={snapshotRows}
        closeSnapshotModal={closeSnapshotModal}
        saveSnapshotToLocal={saveSnapshotToLocal}
        renameSnapshot={renameSnapshot}
        loadSnapshot={loadSnapshot}
        deleteSnapshot={deleteSnapshot}
      />

      <LiteSchedulerInsertOrderModal
        show={showInsertModal}
        insertForm={insertForm}
        setInsertForm={setInsertForm}
        orders={scenario.orders}
        closeInsertModal={closeInsertModal}
        submitInsertOrder={submitInsertOrder}
      />

      <LiteSchedulerOrderModal
        show={showOrderModal}
        onClose={closeOrderModal}
        editingOrderId={editingOrderId}
        isDurationMode={isDurationMode}
        orderModalForm={orderModalForm}
        setOrderModalForm={setOrderModalForm}
        lines={scenario.lines}
        modalTotalWorkload={modalTotalWorkload}
        modalTotalPlanQty={modalTotalPlanQty}
        onSubmit={submitOrderFromModal}
      />

      <LiteSchedulerFinishModal
        show={showFinishModal}
        closeFinishModal={closeFinishModal}
        finishModalForm={finishModalForm}
        setFinishModalForm={setFinishModalForm}
        horizonStart={scenario.horizonStart}
        hasSavedFinish={hasSavedFinish}
        clearManualFinish={clearManualFinish}
        submitManualFinish={submitManualFinish}
      />
    </section>
  );
}
