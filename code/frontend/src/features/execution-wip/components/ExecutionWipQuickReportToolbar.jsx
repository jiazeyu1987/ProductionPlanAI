export function ExecutionWipQuickReportToolbar({
  orderNo,
  onChangeOrderNo,
  qty,
  onChangeQty,
  submitting,
  onSubmit
}) {
  return (
    <div className="toolbar">
      <input value={orderNo} onChange={(e) => onChangeOrderNo(e.target.value)} />
      <input
        type="number"
        step="1"
        min="1"
        value={qty}
        onChange={(e) => onChangeQty(e.target.value)}
      />
      <button disabled={submitting} onClick={onSubmit}>
        {submitting ? "提交中..." : "提交报工"}
      </button>
    </div>
  );
}

