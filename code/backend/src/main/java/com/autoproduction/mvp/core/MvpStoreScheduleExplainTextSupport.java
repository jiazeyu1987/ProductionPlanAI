package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MvpStoreScheduleExplainTextSupport {
  private MvpStoreScheduleExplainTextSupport() {}

  static String toDateTime(String date, String shiftCode, boolean start) {
    LocalDate parsed = LocalDate.parse(date);
    int startHour = "N".equals(shiftCode) || "NIGHT".equals(shiftCode) ? 20 : 8;
    OffsetDateTime time = parsed.atTime(startHour, 0).atOffset(ZoneOffset.UTC);
    if (!start) {
      time = time.plusHours(12);
    }
    return time.toString();
  }

  static String buildProcessAllocationExplainCn(
    String processCode,
    double targetQty,
    double scheduledQty,
    double unscheduledQty,
    int orderCount,
    String topReasonCode
  ) {
    String processName = MvpStoreRuntimeBase.processNameCn(processCode);
    if (targetQty <= 1e-9) {
      return processName + "閸︺劍婀板▎鈩冨笓娴溠傝厬濞屸剝婀佸鍛瀻闁板秶娲伴弽鍥櫤閵";
    }

    double scheduleRate = Math.max(0d, Math.min(100d, (scheduledQty / targetQty) * 100d));
    if (unscheduledQty <= 1e-9) {
      return processName +
      "閻╊喗鐖ｉ柌" + formatQtyText(targetQty) +
      "閿涘苯鍑￠崚鍡涘帳" + formatQtyText(scheduledQty) +
      "閿" + formatPercentText(scheduleRate) +
      "閿涘绱濆☉澶婂挤" + orderCount + "娑擃亣顓归崡鏇礉瑜版挸澧犵痪锔芥将娑撳鍑￠崗銊╁劥鐟曞棛娲婇妴";
    }

    return processName +
    "閻╊喗鐖ｉ柌" + formatQtyText(targetQty) +
    "閿涘苯鍑￠崚鍡涘帳" + formatQtyText(scheduledQty) +
    "閿" + formatPercentText(scheduleRate) +
    "閿涘绱濇禒宥嗘箒" + formatQtyText(unscheduledQty) +
    "閺堫亝甯撻敍灞煎瘜鐟更礁褰堥垾" + MvpStoreScheduleUnscheduledReasonSupport.scheduleReasonNameCn(topReasonCode) + "閳ユ繂濂栭崫宥冣偓";
  }

  static String formatQtyText(double value) {
    double rounded = MvpStoreCoreNormalizationSupport.round2(value);
    long intValue = Math.round(rounded);
    if (Math.abs(rounded - intValue) < 1e-9) {
      return String.valueOf(intValue);
    }
    return String.format(Locale.ROOT, "%.2f", rounded);
  }

  static String formatPercentText(double value) {
    double rounded = MvpStoreCoreNormalizationSupport.round2(value);
    long intValue = Math.round(rounded);
    if (Math.abs(rounded - intValue) < 1e-9) {
      return intValue + "%";
    }
    return String.format(Locale.ROOT, "%.2f%%", rounded);
  }

  static double estimateResourceCapacity(
    MvpDomain.ProcessConfig processConfig,
    int workersAvailable,
    int machinesAvailable
  ) {
    if (processConfig == null) {
      return 0d;
    }
    int groupsByWorkers = workersAvailable / Math.max(1, processConfig.requiredWorkers);
    int groupsByMachines = machinesAvailable / Math.max(1, processConfig.requiredMachines);
    int maxGroups = Math.max(0, Math.min(groupsByWorkers, groupsByMachines));
    return maxGroups * processConfig.capacityPerShift;
  }

  static double calcTaskProducedBeforeShift(
    MvpDomain.Allocation current,
    List<MvpDomain.Allocation> taskAllocations,
    Map<String, Integer> shiftIndexByShiftId
  ) {
    if (current == null || taskAllocations == null || taskAllocations.isEmpty()) {
      return 0d;
    }
    int currentShiftIndex = shiftIndexByShiftId.getOrDefault(current.shiftId, Integer.MAX_VALUE);
    double produced = 0d;
    for (MvpDomain.Allocation row : taskAllocations) {
      int rowShiftIndex = shiftIndexByShiftId.getOrDefault(row.shiftId, Integer.MAX_VALUE);
      if (rowShiftIndex < currentShiftIndex) {
        produced += row.scheduledQty;
      }
    }
    return produced;
  }

  static String buildMaxAllocationExplainCn(
    String processCode,
    MvpDomain.Allocation maxAllocation,
    double taskRemainingBeforeShift,
    double resourceCapacity,
    double materialAvailable
  ) {
    if (maxAllocation == null) {
      return MvpStoreRuntimeBase.processNameCn(processCode) + "閺嗗倹妫ら崣顖澬掗柌濠勬畱瀹勬澘鈧厧鍨庨柊宥堫唶瑜版洏鈧";
    }

    double peakQty = MvpStoreCoreNormalizationSupport.round2(maxAllocation.scheduledQty);
    double remaining = MvpStoreCoreNormalizationSupport.round2(Math.max(0d, taskRemainingBeforeShift));
    double resourceCap = MvpStoreCoreNormalizationSupport.round2(Math.max(0d, resourceCapacity));
    double materialCap = MvpStoreCoreNormalizationSupport.round2(Math.max(0d, materialAvailable));

    double minCap = Double.POSITIVE_INFINITY;
    String dominant = "";
    if (remaining > 1e-9 && remaining < minCap) {
      minCap = remaining;
      dominant = "娴犅濮熼崷銊嚉閻濐厽顐奸崜宥囨畱閸撯晙缍戦崣顖涘笓闁";
    }
    if (resourceCap > 1e-9 && resourceCap < minCap) {
      minCap = resourceCap;
      dominant = "鐠囥儳褰▎锛勬畱娴滅搫濮?鐠佄儳顦挧鍕爱閼宠棄濮忄稉濠囨";
    }
    if (materialCap > 1e-9 && materialCap < minCap) {
      minCap = materialCap;
      dominant = "鐠囥儳褰▎鈥冲讲閻劎澧块弬娆庣瑐闂";
    }
    if (dominant.isBlank()) {
      dominant = "娴溿倖婀℃导妯哄帥缁狙佲偓浣稿鎼村繑鏂佺悰宀勫櫤娑撅氦绁┃鎰閺夌喓娈戠紒鐓庢値楠炲疇銆€";
    }

    return MvpStoreRuntimeBase.processNameCn(processCode) +
    "閸" + maxAllocation.date + MvpStoreRuntimeBase.shiftNameCn(maxAllocation.shiftCode) +
    "鐎电顓归崡" + maxAllocation.orderNo +
    "娴溠呮晸閸楁洘顐煎畡鏉库偓鐓庋瀻闁" + formatQtyText(peakQty) +
    "閵嗗倸缍嬮悵顓濄崲閸斺€冲⒖娴ｆ瑥褰查幒鎺楀櫤缁" + formatQtyText(remaining) +
    "閿涘矁绁┃鎰讲閺€顖啦嫼娑撅﹪妾虹痪" + formatQtyText(resourceCap) +
    "閿涘瞼澧块弬娆忓讲閻劑鍣虹痪" + formatQtyText(materialCap) +
    "閿涙稓鐣诲▔鏇熷瘻閸欘垵顢戄稉濠囨閸欄牗娓剁亸蹇撯偓鑹扮箻鐞涘苯鍨庨柊宥忄礉閸ョ姵顒濋張顒侇偧瀹勬澘鈧厧褰堥垾" + dominant + "閳ユ繀瀵岀€电鈧";
  }
}

