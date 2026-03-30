import SimpleTable from "../../../components/SimpleTable";

export function SimulationLastRunSummaryPanel({ summary }) {
  return (
    <div className="panel">
      <h3>最近一次运行摘要</h3>
      <SimpleTable
        columns={[
          { key: "new_sales_orders", title: "新增销售单" },
          { key: "new_production_orders", title: "新增生产单" },
          { key: "generated_versions", title: "新增版本" },
          { key: "reporting_count", title: "报工条数" },
          { key: "delayed_orders", title: "延期订单" },
          { key: "avg_capacity_factor", title: "平均产能系数" }
        ]}
        rows={summary ? [summary] : []}
      />
    </div>
  );
}

