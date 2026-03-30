export default function OrdersPoolToolbar({
  submitting,
  onRefresh,
  productKeyword,
  onProductKeywordChange,
  showUnfinishedOnly,
  onShowUnfinishedOnlyChange,
  orderNoFilter,
  onClearOrderNoFilter,
  totalCount = 0,
  filteredCount = 0,
}) {
  return (
    <div className="toolbar">
      <button disabled={submitting} onClick={onRefresh}>
        刷新
      </button>
      <span className="hint">显示 {filteredCount} / {totalCount}</span>
      <input
        type="search"
        value={productKeyword}
        onChange={(e) => onProductKeywordChange(e.target.value)}
        placeholder="按产品编码/名称过滤"
        aria-label="产品关键词过滤"
      />
      <label className="toolbar-check">
        <input
          type="checkbox"
          checked={showUnfinishedOnly}
          onChange={(e) => onShowUnfinishedOnlyChange(e.target.checked)}
        />
        <span>仅显示未完成订单</span>
      </label>
      {orderNoFilter ? (
        <button onClick={onClearOrderNoFilter}>返回订单列表</button>
      ) : null}
    </div>
  );
}
