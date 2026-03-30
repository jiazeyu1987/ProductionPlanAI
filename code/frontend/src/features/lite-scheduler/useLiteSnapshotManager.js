import { useCallback, useState } from "react";

function sortByUpdatedAtDesc(rows) {
  return rows.slice().sort((a, b) => b.updatedAt - a.updatedAt);
}

export function useLiteSnapshotManager({
  scenario,
  normalizeScenario,
  makeId,
  formatSnapshotName,
  loadSnapshots,
  saveSnapshots,
  onLoadSnapshot,
  setMessage,
  setError,
}) {
  const [showSnapshotModal, setShowSnapshotModal] = useState(false);
  const [snapshotModalMode, setSnapshotModalMode] = useState("save");
  const [snapshotName, setSnapshotName] = useState(() =>
    formatSnapshotName(new Date()),
  );
  const [snapshots, setSnapshots] = useState(loadSnapshots);

  const refreshSnapshots = useCallback(() => {
    setSnapshots(loadSnapshots());
  }, [loadSnapshots]);

  const openSnapshotModal = useCallback(
    (mode) => {
      setSnapshotModalMode(mode === "load" ? "load" : "save");
      setSnapshotName(formatSnapshotName(new Date()));
      refreshSnapshots();
      setShowSnapshotModal(true);
      setError("");
      setMessage("");
    },
    [formatSnapshotName, refreshSnapshots, setError, setMessage],
  );

  const closeSnapshotModal = useCallback(() => {
    setShowSnapshotModal(false);
  }, []);

  const saveSnapshotToLocal = useCallback(() => {
    const name =
      String(snapshotName || "").trim() || formatSnapshotName(new Date());
    const now = Date.now();
    const row = {
      id: makeId("snapshot"),
      name,
      createdAt: now,
      updatedAt: now,
      scenario: normalizeScenario(scenario),
    };
    const nextRows = sortByUpdatedAtDesc([row, ...loadSnapshots()]);
    saveSnapshots(nextRows);
    setSnapshots(nextRows);
    setSnapshotName(formatSnapshotName(new Date()));
    setMessage(`鍦烘櫙宸蹭繚瀛橈細${name}`);
  }, [
    formatSnapshotName,
    loadSnapshots,
    makeId,
    normalizeScenario,
    saveSnapshots,
    scenario,
    setMessage,
    snapshotName,
  ]);

  const renameSnapshot = useCallback(
    (snapshotId, nextName) => {
      const safeName = String(nextName || "").trim();
      if (!safeName) {
        setError("场景名称不能为空。");
        refreshSnapshots();
        return;
      }
      const rows = loadSnapshots();
      const nextRows = sortByUpdatedAtDesc(
        rows.map((row) => {
          if (row.id !== snapshotId) {
            return row;
          }
          return { ...row, name: safeName, updatedAt: Date.now() };
        }),
      );
      saveSnapshots(nextRows);
      setSnapshots(nextRows);
      setMessage(`鍦烘櫙宸叉敼鍚嶏細${safeName}`);
    },
    [loadSnapshots, refreshSnapshots, saveSnapshots, setError, setMessage],
  );

  const deleteSnapshot = useCallback(
    (snapshotId, confirmAction) => {
      const rows = loadSnapshots();
      const target = rows.find((row) => row.id === snapshotId);
      if (!target) {
        return;
      }
      if (!confirmAction(`纭鍒犻櫎鍦烘櫙 "${target.name}" 鍚楋紵`)) {
        return;
      }
      const nextRows = rows.filter((row) => row.id !== snapshotId);
      saveSnapshots(nextRows);
      setSnapshots(nextRows);
      setMessage(`鍦烘櫙宸插垹闄わ細${target.name}`);
    },
    [loadSnapshots, saveSnapshots, setMessage],
  );

  const loadSnapshot = useCallback(
    (snapshotId) => {
      const rows = loadSnapshots();
      const target = rows.find((row) => row.id === snapshotId);
      if (!target) {
        setError("未找到要读取的场景。");
        return;
      }
      onLoadSnapshot(target);
      setShowSnapshotModal(false);
    },
    [loadSnapshots, onLoadSnapshot, setError],
  );

  return {
    showSnapshotModal,
    snapshotModalMode,
    snapshotName,
    setSnapshotName,
    snapshots,
    openSnapshotModal,
    closeSnapshotModal,
    saveSnapshotToLocal,
    renameSnapshot,
    deleteSnapshot,
    loadSnapshot,
  };
}
