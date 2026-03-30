export function LiteSchedulerFinishModal({
  show,
  closeFinishModal,
  finishModalForm,
  setFinishModalForm,
  horizonStart,
  hasSavedFinish,
  clearManualFinish,
  submitManualFinish,
}) {
  if (!show) {
    return null;
  }

  return (
    <div className="lite-modal-backdrop" onClick={closeFinishModal}>
      <div
        className="lite-modal"
        role="dialog"
        aria-modal="true"
        data-testid="finish-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel-head">
          <h3>手动报结束</h3>
          <button onClick={closeFinishModal}>关闭</button>
        </div>

        <div className="toolbar">
          <span>
            产线：{finishModalForm.lineName || "-"} | 订单：
            {finishModalForm.orderLabel || "-"}
          </span>
        </div>

        <div className="toolbar">
          <label>
            结束日期
            <input
              type="date"
              data-testid="finish-date-input"
              value={finishModalForm.finishDate}
              min={finishModalForm.startDate || undefined}
              max={horizonStart || undefined}
              onChange={(e) =>
                setFinishModalForm((prev) => ({
                  ...prev,
                  finishDate: e.target.value,
                }))
              }
            />
          </label>
        </div>

        <p className="hint">
          该订单在此产线起始于 {finishModalForm.startDate || "-"}
          ，可补录历史结束，后续订单会自动前移。
        </p>

        <div className="row-actions">
          <button onClick={closeFinishModal}>取消</button>
          {hasSavedFinish ? (
            <button className="btn-danger-text" onClick={clearManualFinish}>
              清除报结束
            </button>
          ) : null}
          <button
            className="btn-success-text"
            data-testid="submit-finish-btn"
            onClick={submitManualFinish}
          >
            保存报结束
          </button>
        </div>
      </div>
    </div>
  );
}
