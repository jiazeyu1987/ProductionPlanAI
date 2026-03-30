package com.autoproduction.mvp.core;

final class MvpStoreRuntimeBaseExportTemplateConstantsSupport {
  private MvpStoreRuntimeBaseExportTemplateConstantsSupport() {}

  static final String WEEKLY_PLAN_SHEET_NAME = "周计划（3.16-3.22）";
  static final String WEEKLY_PLAN_TITLE_CN = "周计划（3.16-3.22）";
  static final String[] WEEKLY_PLAN_EXTRA_SHEETS = { "Sheet2", "Sheet3" };

  static final String MONTHLY_PLAN_SHEET_NAME = "生产计划";
  static final String MONTHLY_PLAN_TITLE_CN = "2月订单明细表";

  static final String[] WEEKLY_PLAN_HEADERS_CN = {
    "生产订单号",
    "客户备注",
    "产品名称",
    "规格型号",
    "生产批号",
    "订单数量",
    "包装形式",
    "销售单号",
    "车间外围包装日期",
    "备注(工序排产)"
  };

  static final String[] WEEKLY_PLAN_KEYS = {
    "production_order_no",
    "customer_remark",
    "product_name",
    "spec_model",
    "production_batch_no",
    "order_qty",
    "packaging_form",
    "sales_order_no",
    "workshop_outer_packaging_date",
    "process_schedule_remark"
  };

  static final String[] MONTHLY_PLAN_HEADERS_CN = {
    "下单日期",
    "生产订单号",
    "客户备注",
    "产品名称",
    "规格型号",
    "生产批号",
    "计划完工日期2",
    "生产日期\n（外贸）",
    "订单数量",
    "包装形式",
    "销售单号",
    "采购交期",
    "注塑交期",
    "市场备注信息",
    "市场需求",
    "计划完工日期1",
    "半成品代码",
    "半成品库存",
    "半成品需求",
    "半成品在制",
    "需下单",
    "待入库",
    "周度/月度计划(工序排产)",
    "车间外围包装日期",
    "备注",
    "完工数量(车间)",
    "完工时间（车间）",
    "完工数量（外围）",
    "完工时间（外围）",
    "匹配"
  };

  static final String[] MONTHLY_PLAN_KEYS = {
    "order_date",
    "production_order_no",
    "customer_remark",
    "product_name",
    "spec_model",
    "production_batch_no",
    "planned_finish_date_2",
    "production_date_foreign_trade",
    "order_qty",
    "packaging_form",
    "sales_order_no",
    "purchase_due_date",
    "injection_due_date",
    "market_remark_info",
    "market_demand",
    "planned_finish_date_1",
    "semi_finished_code",
    "semi_finished_inventory",
    "semi_finished_demand",
    "semi_finished_wip",
    "need_order_qty",
    "pending_inbound_qty",
    "weekly_monthly_process_plan",
    "workshop_outer_packaging_date",
    "note",
    "workshop_completed_qty",
    "workshop_completed_time",
    "outer_completed_qty",
    "outer_completed_time",
    "match_status"
  };
}

