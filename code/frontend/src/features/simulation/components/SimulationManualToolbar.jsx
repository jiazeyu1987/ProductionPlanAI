export function SimulationManualToolbar({
  loading,
  onAddProductionOrder,
  onAdvanceOneDay,
  onReset,
  onRefresh
}) {
  return (
    <div className="toolbar">
      <button disabled={loading} onClick={onAddProductionOrder}>
        {loading ? "执行中..." : "虚增生产订单"}
      </button>
      <button disabled={loading} onClick={onAdvanceOneDay}>
        {loading ? "执行中..." : "推进一天"}
      </button>
      <button disabled={loading} onClick={onReset}>
        {loading ? "执行中..." : "重置模拟"}
      </button>
      <button disabled={loading} onClick={onRefresh}>
        刷新
      </button>
    </div>
  );
}

