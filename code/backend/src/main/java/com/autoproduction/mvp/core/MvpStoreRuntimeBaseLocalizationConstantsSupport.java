package com.autoproduction.mvp.core;

import java.util.Map;

final class MvpStoreRuntimeBaseLocalizationConstantsSupport {
  private MvpStoreRuntimeBaseLocalizationConstantsSupport() {}

  static final String STRATEGY_KEY_ORDER_FIRST = "KEY_ORDER_FIRST";
  static final String STRATEGY_MAX_CAPACITY_FIRST = "MAX_CAPACITY_FIRST";
  static final String STRATEGY_MIN_DELAY_FIRST = "MIN_DELAY_FIRST";

  static final Map<String, String> SCHEDULE_STRATEGY_NAME_CN = Map.ofEntries(
    Map.entry(STRATEGY_KEY_ORDER_FIRST, "关键订单优先"),
    Map.entry(STRATEGY_MAX_CAPACITY_FIRST, "最大产能优先"),
    Map.entry(STRATEGY_MIN_DELAY_FIRST, "交期最小延期优先")
  );

  static final Map<String, String> PRODUCT_NAME_CN = Map.ofEntries(
    Map.entry("PROD_CATH", "导管"),
    Map.entry("PROD_BALLOON", "球囊"),
    Map.entry("PROD_STENT", "支架"),
    Map.entry("PROD_ANGIO_CATH", "造影导管"),
    Map.entry("PROD_UNKNOWN", "未知产品")
  );

  static final Map<String, String> PROCESS_NAME_CN = Map.ofEntries(
    Map.entry("PROC_TUBE", "制管"),
    Map.entry("PROC_ASSEMBLY", "装配"),
    Map.entry("PROC_BALLOON", "球囊成型"),
    Map.entry("PROC_STENT", "支架成型"),
    Map.entry("PROC_STERILE", "灭菌"),
    Map.entry("Z470", "造影导管切导管"),
    Map.entry("Z3910", "造影导管扩孔"),
    Map.entry("Z3920", "造影导管磨刺"),
    Map.entry("Z390", "造影导管搭接"),
    Map.entry("Z340", "造影导管全检导管"),
    Map.entry("Z410", "造影导管粘贴片"),
    Map.entry("Z460", "造影导管穿白片"),
    Map.entry("Z420", "造影导管焊接"),
    Map.entry("Z320", "造影导管修片片"),
    Map.entry("Z310", "造影导管热片片"),
    Map.entry("Z350", "造影导管检验白色端"),
    Map.entry("Z480", "造影导管毛化"),
    Map.entry("Z370", "造影导管手柄点胶"),
    Map.entry("Z380", "造影导管手柄上护套"),
    Map.entry("Z290", "造影导管清洗、吹水"),
    Map.entry("Z450", "造影导管穿衬芯"),
    Map.entry("Z360", "造影导管塑型"),
    Map.entry("Z270", "造影导管检渗"),
    Map.entry("Z4830", "造影导管包装"),
    Map.entry("Z500", "造影导管全检（包装）"),
    Map.entry("W130", "W贴产品标签（大标）"),
    Map.entry("W140", "W贴产品标签（小标）"),
    Map.entry("W160", "W全检导管"),
    Map.entry("W150", "W导管中盒(说明书)"),
    Map.entry("W030", "W包装打包")
  );

  static final Map<String, String> STATUS_NAME_CN = Map.ofEntries(
    Map.entry("OPEN", "待处理"),
    Map.entry("IN_PROGRESS", "进行中"),
    Map.entry("DONE", "已完成"),
    Map.entry("DELAY", "延期"),
    Map.entry("DRAFT", "草稿"),
    Map.entry("PUBLISHED", "已发布"),
    Map.entry("SUPERSEDED", "已替代"),
    Map.entry("ROLLED_BACK", "已回滚"),
    Map.entry("RUNNING", "运行中"),
    Map.entry("ACKED", "已确认"),
    Map.entry("CLOSED", "已关闭"),
    Map.entry("SUCCESS", "成功"),
    Map.entry("FAILED", "失败"),
    Map.entry("PARTIAL", "部分成功"),
    Map.entry("AVAILABLE", "可用"),
    Map.entry("RELEASED", "已下发")
  );

  static final Map<String, String> ACTION_NAME_CN = Map.ofEntries(
    Map.entry("CREATE_ORDER", "创建订单"),
    Map.entry("UPDATE_ORDER", "更新订单"),
    Map.entry("PATCH_ORDER", "修改订单"),
    Map.entry("GENERATE_SCHEDULE", "生成排产"),
    Map.entry("AUTO_REPLAN_SCHEDULE", "自动重排"),
    Map.entry("PUBLISH_VERSION", "发布版本"),
    Map.entry("ROLLBACK_VERSION", "回滚版本"),
    Map.entry("CREATE_REPORTING", "创建报工"),
    Map.entry("WRITE_SCHEDULE_RESULTS", "回写排产结果"),
    Map.entry("WRITE_SCHEDULE_STATUS", "回写排产状态"),
    Map.entry("INGEST_WIP_EVENT", "写入在制品事件"),
    Map.entry("TRIGGER_REPLAN", "触发重排"),
    Map.entry("RESET_SIMULATION", "重置仿真"),
    Map.entry("RUN_SIMULATION", "运行仿真"),
    Map.entry("RETRY_OUTBOX", "重试出站消息"),
    Map.entry("CREATE_DISPATCH_COMMAND", "创建调度指令"),
    Map.entry("APPROVE_DISPATCH_COMMAND", "批准调度指令"),
    Map.entry("REJECT_DISPATCH_COMMAND", "驳回调度指令"),
    Map.entry("ACK_ALERT", "确认预警"),
    Map.entry("CLOSE_ALERT", "关闭预警"),
    Map.entry("APPROVED", "已批准"),
    Map.entry("REJECTED", "已驳回"),
    Map.entry("INSERT", "插单"),
    Map.entry("LOCK", "锁单"),
    Map.entry("UNLOCK", "解锁"),
    Map.entry("PRIORITY", "提优先级"),
    Map.entry("FREEZE", "冻结"),
    Map.entry("UNFREEZE", "解冻")
  );

  static final Map<String, String> EVENT_TYPE_NAME_CN = Map.ofEntries(
    Map.entry("SALES_RECEIVED", "接收销售订单"),
    Map.entry("ORDER_CONVERTED", "转换生产订单"),
    Map.entry("CAPACITY_CHANGED", "产能变化"),
    Map.entry("SCHEDULE_GENERATED", "生成排产版本"),
    Map.entry("SCHEDULE_PUBLISHED", "发布排产版本"),
    Map.entry("EXECUTION_PROGRESS", "执行进度同步"),
    Map.entry("SIM_RESET", "重置仿真"),
    Map.entry("SIM_RUN_DONE", "仿真运行完成")
  );

  static final Map<String, String> TOPIC_NAME_CN = Map.ofEntries(
    Map.entry("MES_REPORTING", "MES报工"),
    Map.entry("MES_WIP_EVENT", "MES在制品事件"),
    Map.entry("ERP_SCHEDULE_RESULTS", "ERP排产结果"),
    Map.entry("ERP_SCHEDULE_STATUS", "ERP排产状态")
  );

  static final Map<String, String> SYSTEM_NAME_CN = Map.ofEntries(
    Map.entry("MES", "MES系统"),
    Map.entry("ERP", "ERP系统"),
    Map.entry("SCHEDULER", "排产系统")
  );

  static final Map<String, String> SCENARIO_NAME_CN = Map.ofEntries(
    Map.entry("STABLE", "稳定"),
    Map.entry("TIGHT", "紧张"),
    Map.entry("BREAKDOWN", "故障")
  );

  static final Map<String, String> SHIFT_NAME_CN = Map.ofEntries(
    Map.entry("DAY", "白班"),
    Map.entry("NIGHT", "夜班"),
    Map.entry("D", "白班"),
    Map.entry("N", "夜班")
  );

  static final Map<String, String> DEPENDENCY_NAME_CN = Map.ofEntries(
    Map.entry("FS", "完成-开始"),
    Map.entry("SS", "开始-开始")
  );

  static final Map<String, String> ALERT_TYPE_NAME_CN = Map.ofEntries(
    Map.entry("PROGRESS_GAP", "进度偏差"),
    Map.entry("EQUIPMENT_DOWN", "设备故障")
  );

  static final Map<String, String> SEVERITY_NAME_CN = Map.ofEntries(
    Map.entry("CRITICAL", "严重"),
    Map.entry("WARN", "警告"),
    Map.entry("INFO", "提示")
  );
}

