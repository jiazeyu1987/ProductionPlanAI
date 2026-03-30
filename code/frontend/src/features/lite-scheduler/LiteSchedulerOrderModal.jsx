import { formatNumber } from "./numberFormatUtils";

export function LiteSchedulerOrderModal({
  show,
  onClose,
  editingOrderId,
  isDurationMode,
  orderModalForm,
  setOrderModalForm,
  lines,
  modalTotalWorkload,
  modalTotalPlanQty,
  onSubmit,
}) {
  if (!show) {
    return null;
  }

  function updateOrderField(field, value) {
    setOrderModalForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  }

  function updateLineField(field, lineId, value) {
    setOrderModalForm((prev) => ({
      ...prev,
      [field]: { ...prev[field], [lineId]: value },
    }));
  }

  return (
    <div className="lite-modal-backdrop" onClick={onClose}>
      <div
        className="lite-modal"
        role="dialog"
        aria-modal="true"
        data-testid="order-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel-head">
          <h3>{editingOrderId ? "编辑订单" : "新增订单"}</h3>
          <button onClick={onClose}>关闭</button>
        </div>

        <div className="toolbar">
          <label>
            订单号
            <input
              data-testid="order-no-input"
              placeholder="可选"
              value={orderModalForm.orderNo}
              onChange={(e) => updateOrderField("orderNo", e.target.value)}
            />
          </label>
          <label>
            产品名称
            <input
              data-testid="order-product-name-input"
              placeholder="可选"
              value={orderModalForm.productName}
              onChange={(e) => updateOrderField("productName", e.target.value)}
            />
          </label>
          <label>
            规格
            <input
              data-testid="order-spec-input"
              placeholder="可选"
              value={orderModalForm.spec}
              onChange={(e) => updateOrderField("spec", e.target.value)}
            />
          </label>
          <label>
            批号
            <input
              data-testid="order-batch-no-input"
              placeholder="可选"
              value={orderModalForm.batchNo}
              onChange={(e) => updateOrderField("batchNo", e.target.value)}
            />
          </label>
        </div>

        <p className="hint">
          {isDurationMode
            ? "按天数模式下，结束时间可在日历手动报结束后再自动前后顺延。"
            : "系统默认同优先级，按订单顺序向后排产。"}
        </p>
        <h4 className="lite-sub-title">
          {isDurationMode
            ? "产线计划天数与个数（整数）"
            : "产线工作量分配（订单总量，非日量）"}
        </h4>

        <div className="lite-allocation-list">
          {lines.map((line) => (
            <div className="lite-allocation-row" key={line.id}>
              <span className="lite-line-name">{line.name}</span>
              {isDurationMode ? (
                <>
                  <input
                    data-testid={`order-line-plan-days-${line.id}`}
                    aria-label={`${line.name} 计划天数`}
                    type="number"
                    min="0"
                    step="1"
                    value={orderModalForm.linePlanDays?.[line.id] || "0"}
                    onChange={(e) =>
                      updateLineField("linePlanDays", line.id, e.target.value)
                    }
                  />
                  <span className="hint">天</span>
                  <input
                    data-testid={`order-line-plan-qty-${line.id}`}
                    aria-label={`${line.name} 计划个数`}
                    type="number"
                    min="0"
                    step="1"
                    value={orderModalForm.linePlanQuantities?.[line.id] || "0"}
                    onChange={(e) =>
                      updateLineField(
                        "linePlanQuantities",
                        line.id,
                        e.target.value,
                      )
                    }
                  />
                  <span className="hint">个</span>
                </>
              ) : (
                <>
                  <input
                    data-testid={`order-line-days-${line.id}`}
                    aria-label={`${line.name} 工作量`}
                    type="number"
                    min="0"
                    step="0.1"
                    value={orderModalForm.lineTotals?.[line.id] || "0"}
                    onChange={(e) =>
                      updateLineField("lineTotals", line.id, e.target.value)
                    }
                  />
                  <span className="hint">总量</span>
                </>
              )}
            </div>
          ))}
        </div>

        <div className="toolbar">
          <span className="hint">
            {isDurationMode
              ? `订单计划总天数：${formatNumber(modalTotalWorkload)} 天，计划总个数：${formatNumber(modalTotalPlanQty)} 个`
              : `订单总工作量：${formatNumber(modalTotalWorkload)} 个`}
          </span>
        </div>

        <div className="row-actions">
          <button onClick={onClose}>取消</button>
          <button
            className={editingOrderId ? "" : "btn-success-text"}
            data-testid="submit-order-modal"
            onClick={onSubmit}
          >
            {editingOrderId ? "保存修改" : "创建订单"}
          </button>
        </div>
      </div>
    </div>
  );
}
