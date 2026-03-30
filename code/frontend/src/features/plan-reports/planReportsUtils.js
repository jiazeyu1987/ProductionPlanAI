const TAB_WEEKLY = "weekly";
const TAB_MONTHLY = "monthly";

function keyByOrder(row) {
  return String(row?.production_order_no ?? "").trim();
}

function normalizeValue(value) {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "";
  }
  return String(value).trim();
}

function sortByOrderNo(rows) {
  return [...(rows || [])].sort((a, b) =>
    keyByOrder(a).localeCompare(keyByOrder(b), "zh-Hans-CN"),
  );
}

function alignWeeklyRows(weeklyRows, monthlyRows) {
  const monthlyMap = new Map((monthlyRows || []).map((row) => [keyByOrder(row), row]));
  const visited = new Set();
  const aligned = [];

  for (const row of weeklyRows || []) {
    const orderNo = keyByOrder(row);
    const monthly = monthlyMap.get(orderNo);
    visited.add(orderNo);
    if (!monthly) {
      aligned.push(row);
      continue;
    }
    aligned.push({
      ...row,
      customer_remark: monthly.customer_remark,
      product_name: monthly.product_name,
      spec_model: monthly.spec_model,
      production_batch_no: monthly.production_batch_no,
      order_qty: monthly.order_qty,
      packaging_form: monthly.packaging_form,
      sales_order_no: monthly.sales_order_no,
      workshop_outer_packaging_date: monthly.workshop_outer_packaging_date,
      process_schedule_remark: row.process_schedule_remark ?? monthly.weekly_monthly_process_plan,
    });
  }

  for (const row of monthlyRows || []) {
    const orderNo = keyByOrder(row);
    if (!visited.has(orderNo)) {
      aligned.push({
        production_order_no: row.production_order_no,
        customer_remark: row.customer_remark,
        product_name: row.product_name,
        spec_model: row.spec_model,
        production_batch_no: row.production_batch_no,
        order_qty: row.order_qty,
        packaging_form: row.packaging_form,
        sales_order_no: row.sales_order_no,
        workshop_outer_packaging_date: row.workshop_outer_packaging_date,
        process_schedule_remark: row.weekly_monthly_process_plan,
      });
    }
  }
  return sortByOrderNo(aligned);
}

function validateConsistency(weeklyRows, monthlyRows, sharedFieldPairs) {
  const weeklyMap = new Map((weeklyRows || []).map((row) => [keyByOrder(row), row]));
  const monthlyMap = new Map((monthlyRows || []).map((row) => [keyByOrder(row), row]));
  const allOrderNos = new Set([...weeklyMap.keys(), ...monthlyMap.keys()]);
  const issues = [];

  for (const orderNo of allOrderNos) {
    const weekly = weeklyMap.get(orderNo);
    const monthly = monthlyMap.get(orderNo);
    if (!weekly || !monthly) {
      issues.push({
        production_order_no: orderNo,
        field: "订单存在性",
        weekly: weekly ? "存在" : "缺失",
        monthly: monthly ? "存在" : "缺失",
      });
      continue;
    }
    for (const [wKey, mKey, label] of sharedFieldPairs || []) {
      const w = normalizeValue(weekly[wKey]);
      const m = normalizeValue(monthly[mKey]);
      if (w !== m) {
        issues.push({
          production_order_no: orderNo,
          field: label,
          weekly: w || "-",
          monthly: m || "-",
        });
      }
    }
  }

  return {
    consistent: issues.length === 0,
    issueCount: issues.length,
    issues: issues.slice(0, 20),
  };
}

export {
  TAB_MONTHLY,
  TAB_WEEKLY,
  alignWeeklyRows,
  keyByOrder,
  normalizeValue,
  sortByOrderNo,
  validateConsistency,
};

