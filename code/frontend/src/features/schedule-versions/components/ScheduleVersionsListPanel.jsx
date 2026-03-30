import SimpleTable from "../../../components/SimpleTable";
import { RULE_VERSION_CN, STATUS_CN } from "../scheduleVersionsConstantsUtils";

export default function ScheduleVersionsListPanel({
  visibleRows,
  showDraft,
  compareWith,
  message,
  onShowDraftChange,
  onCompareWithChange,
  onCompare,
  onShowAlgorithm,
  onPublish,
  onRollback,
}) {
  return (
    <>
      <div className="toolbar">
        <label>
          <input type="checkbox" checked={showDraft} onChange={(e) => onShowDraftChange(e.target.checked)} />
          显示草稿
        </label>
        <select value={compareWith} onChange={(e) => onCompareWithChange(e.target.value)}>
          <option value="">选择对比基线</option>
          {visibleRows.map((row) => (
            <option key={row.version_no} value={row.version_no}>
              {row.version_no}
            </option>
          ))}
        </select>
      </div>
      {message ? <p className="notice">{message}</p> : null}
      <SimpleTable
        columns={[
          { key: "version_no", title: "版本号" },
          {
            key: "status",
            title: "状态",
            render: (value, row) => row.status_name_cn || STATUS_CN[value] || value || "-",
          },
          {
            key: "rule_version_no",
            title: "规则版本",
            render: (value, row) => row.rule_version_name_cn || RULE_VERSION_CN[value] || value || "-",
          },
          { key: "created_by", title: "创建人" },
          { key: "created_at", title: "创建时间" },
          {
            key: "actions",
            title: "操作",
            render: (_, row) => (
              <div className="row-actions">
                <button onClick={() => onCompare(row.version_no).catch(() => {})}>差异</button>
                <button onClick={() => onShowAlgorithm(row.version_no).catch(() => {})}>算法</button>
                <button onClick={() => onPublish(row.version_no).catch(() => {})}>发布</button>
                <button onClick={() => onRollback(row.version_no).catch(() => {})}>回滚</button>
              </div>
            ),
          },
        ]}
        rows={visibleRows}
      />
    </>
  );
}

