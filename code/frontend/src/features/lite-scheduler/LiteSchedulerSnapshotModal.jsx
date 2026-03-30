import SimpleTable from "../../components/SimpleTable";
import { formatSnapshotDisplay } from "./snapshotUtils";
import { confirmAction } from "./uiUtils";

export function LiteSchedulerSnapshotModal({
  show,
  snapshotModalMode,
  snapshotName,
  setSnapshotName,
  snapshotRows,
  closeSnapshotModal,
  saveSnapshotToLocal,
  renameSnapshot,
  loadSnapshot,
  deleteSnapshot,
}) {
  if (!show) {
    return null;
  }

  return (
    <div className="lite-modal-backdrop" onClick={closeSnapshotModal}>
      <div
        className="lite-modal"
        role="dialog"
        aria-modal="true"
        data-testid="snapshot-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel-head">
          <h3>{snapshotModalMode === "save" ? "保存场景" : "读取场景"}</h3>
          <button onClick={closeSnapshotModal}>关闭</button>
        </div>

        {snapshotModalMode === "save" ? (
          <div className="toolbar">
            <label>
              场景名称
              <input
                data-testid="snapshot-name-input"
                value={snapshotName}
                onChange={(e) => setSnapshotName(e.target.value)}
              />
            </label>
            <button
              className="btn-success-text"
              data-testid="snapshot-save-confirm-btn"
              onClick={saveSnapshotToLocal}
            >
              保存到本地
            </button>
          </div>
        ) : null}

        <SimpleTable
          columns={[
            {
              key: "name",
              title: "场景名称",
              render: (value, row) => (
                <input
                  defaultValue={String(value || "")}
                  onBlur={(e) => renameSnapshot(row.id, e.target.value)}
                />
              ),
            },
            {
              key: "updated_at",
              title: "更新时间",
              render: (value) => formatSnapshotDisplay(value),
            },
            {
              key: "created_at",
              title: "创建时间",
              render: (value) => formatSnapshotDisplay(value),
            },
            {
              key: "actions",
              title: "操作",
              render: (_, row) => (
                <div className="row-actions">
                  <button onClick={() => loadSnapshot(row.id)}>读取</button>
                  <button
                    className="btn-danger-text"
                    onClick={() => deleteSnapshot(row.id, confirmAction)}
                  >
                    删除
                  </button>
                </div>
              ),
            },
          ]}
          rows={snapshotRows}
        />
      </div>
    </div>
  );
}
