import { normalizeVersionNo, strategyLabel, STRATEGY_OPTIONS } from "../scheduleBoardUtils";

export default function ScheduleBoardToolbar({
  strategyCode,
  onStrategyChange,
  onGenerate,
  onPublish,
  publishing,
  versions,
  selectedVersionNo,
  onSelectedVersionNoChange,
  canPublishSelected,
}) {
  return (
    <div className="toolbar">
      <label>
        策略
        <select
          value={strategyCode}
          onChange={(e) => onStrategyChange(e.target.value)}
        >
          {STRATEGY_OPTIONS.map((item) => (
            <option key={item.code} value={item.code}>
              {item.label}
            </option>
          ))}
        </select>
      </label>
      <button onClick={onGenerate}>生成草稿版本</button>
      <button
        disabled={!selectedVersionNo || !canPublishSelected || publishing}
        onClick={onPublish}
      >
        {publishing ? "发布中..." : "发布草稿"}
      </button>
      <select value={selectedVersionNo} onChange={(e) => onSelectedVersionNoChange(e.target.value)}>
        <option value="">请选择版本</option>
        {(versions || []).map((item) => (
          <option key={normalizeVersionNo(item)} value={normalizeVersionNo(item)}>
            {normalizeVersionNo(item)} / {item.status_name_cn || item.status || "-"} /{" "}
            {item.strategy_name_cn || strategyLabel(item.strategy_code)}
          </option>
        ))}
      </select>
    </div>
  );
}

