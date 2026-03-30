import { useState } from "react";
import { postLegacy } from "../services/api";

function newRequestId() {
  return "req-" + Math.random().toString(16).slice(2) + "-" + Date.now().toString(16);
}

export default function TestToolsPage() {
  const [keyword, setKeyword] = useState("造影导管");
  const [count, setCount] = useState(4);
  const [completed, setCompleted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  async function runImport() {
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const response = await postLegacy("/api/test/import-production-orders", {
        request_id: newRequestId(),
        keyword,
        n: Number(count),
        completed
      });
      setResult(response);
    } catch (err) {
      setError(err?.message || String(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section>
      <h2>测试工具</h2>
      <p>从 ERP 导入生产订单到系统订单池（仅用于测试）。</p>

      <div className="panel">
        <h3>ERP 订单导入</h3>

        <div className="panel-row">
          <div className="panel-item">
            <div className="muted">名称包含（product_name_cn）</div>
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="例如：造影导管" />
          </div>
          <div className="panel-item">
            <div className="muted">数量 N</div>
            <input type="number" min={1} max={200} value={count} onChange={(e) => setCount(e.target.value)} />
          </div>
          <div className="panel-item">
            <div className="muted">导入状态</div>
            <select value={completed ? "DONE" : "OPEN"} onChange={(e) => setCompleted(e.target.value === "DONE")}>
              <option value="OPEN">未完成（OPEN）</option>
              <option value="DONE">已完成（DONE）</option>
            </select>
          </div>
          <div className="panel-item">
            <div className="muted">操作</div>
            <button className="primary" onClick={runImport} disabled={loading || !keyword.trim()}>
              {loading ? "导入中..." : "从 ERP 导入"}
            </button>
          </div>
        </div>

        {error ? <pre>{error}</pre> : null}
        {result ? <pre>{JSON.stringify(result, null, 2)}</pre> : null}
      </div>
    </section>
  );
}
