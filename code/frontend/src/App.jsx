import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import AlertsPage from "./pages/AlertsPage";
import AuditLogsPage from "./pages/AuditLogsPage";
import DashboardPage from "./pages/DashboardPage";
import DispatchCommandsPage from "./pages/DispatchCommandsPage";
import ExecutionWipPage from "./pages/ExecutionWipPage";
import GuidePage from "./pages/GuidePage";
import MasterdataPage from "./pages/MasterdataPage";
import OpsIntegrationPage from "./pages/OpsIntegrationPage";
import OrdersPoolPage from "./pages/OrdersPoolPage";
import PlanReportsPage from "./pages/PlanReportsPage";
import ScheduleBoardPage from "./pages/ScheduleBoardPage";
import ScheduleVersionsPage from "./pages/ScheduleVersionsPage";
import SimulationPage from "./pages/SimulationPage";
import LiteSchedulerPage from "./pages/LiteSchedulerPage";

const links = [
  ["/dashboard", "看板"],
  ["/orders/pool", "生产订单"],
  ["/schedule/board", "调度台"],
  ["/reports/plans", "计划报表"],
  ["/schedule/versions", "排产历史"],
  ["/dispatch/commands", "指令审批"],
  ["/execution/wip", "报工"],
  ["/alerts", "预警"],
  ["/audit/logs", "审计"],
  ["/masterdata", "主数据"],
  ["/ops/integration", "同步监控"],
  ["/simulation", "仿真"],
  ["/guide", "说明页"],
  ["/schedule/lite", "璞慧排产"]
];

export default function App() {
  return (
    <div className="layout">
      <aside className="sidebar">
        <h1>自动排产平台</h1>
        <p>P0 最小可行版本控制台</p>
        <nav>
          {links.map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => (isActive ? "active" : "")}>
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/orders/pool" element={<OrdersPoolPage />} />
          <Route path="/schedule/board" element={<ScheduleBoardPage />} />
          <Route path="/schedule/lite" element={<LiteSchedulerPage />} />
          <Route path="/reports/plans" element={<PlanReportsPage />} />
          <Route path="/schedule/versions" element={<ScheduleVersionsPage />} />
          <Route path="/dispatch/commands" element={<DispatchCommandsPage />} />
          <Route path="/execution/wip" element={<ExecutionWipPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/audit/logs" element={<AuditLogsPage />} />
          <Route path="/masterdata" element={<MasterdataPage />} />
          <Route path="/ops/integration" element={<OpsIntegrationPage />} />
          <Route path="/simulation" element={<SimulationPage />} />
          <Route path="/guide" element={<GuidePage />} />
        </Routes>
      </main>
    </div>
  );
}
