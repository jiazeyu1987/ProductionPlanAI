export function LiteSchedulerInsertOrderModal({
  show,
  insertForm,
  setInsertForm,
  orders,
  closeInsertModal,
  submitInsertOrder,
}) {
  if (!show) {
    return null;
  }

  return (
    <div className="lite-modal-backdrop" onClick={closeInsertModal}>
      <div
        className="lite-modal"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel-head">
          <h3>插入订单</h3>
          <button onClick={closeInsertModal}>关闭</button>
        </div>

        <div className="toolbar">
          <label>
            订单
            <select
              value={insertForm.orderId}
              onChange={(e) =>
                setInsertForm((prev) => ({
                  ...prev,
                  orderId: e.target.value,
                }))
              }
            >
              <option value="">请选择</option>
              {orders.map((order) => (
                <option key={order.id} value={order.id}>
                  {order.orderNo}
                </option>
              ))}
            </select>
          </label>
          <label>
            插入日期
            <input
              type="date"
              value={insertForm.date}
              onChange={(e) =>
                setInsertForm((prev) => ({ ...prev, date: e.target.value }))
              }
            />
          </label>
        </div>

        <p className="hint">
          系统会从所选日期开始优先执行该订单，其余订单自动顺延。
        </p>
        <div className="row-actions">
          <button onClick={closeInsertModal}>取消</button>
          <button className="btn-success-text" onClick={submitInsertOrder}>
            插入并重排
          </button>
        </div>
      </div>
    </div>
  );
}
