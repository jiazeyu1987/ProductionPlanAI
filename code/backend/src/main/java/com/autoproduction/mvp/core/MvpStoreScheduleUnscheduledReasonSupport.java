package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleUnscheduledReasonSupport {
  private MvpStoreScheduleUnscheduledReasonSupport() {}

  static int countCapacityBlocked(List<Map<String, Object>> unscheduled) {
    int count = 0;
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      if (reasonCode == null) {
        continue;
      }
      if (
        reasonCode.startsWith("CAPACITY_")
          || "MATERIAL_SHORTAGE".equals(reasonCode)
          || "CAPACITY_LIMIT".equals(reasonCode)
      ) {
        count += 1;
      }
    }
    return count;
  }

  static int countByReasonCodes(List<Map<String, Object>> unscheduled, Set<String> reasonCodes) {
    if (unscheduled == null || unscheduled.isEmpty() || reasonCodes == null || reasonCodes.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      if (reasonCode != null && reasonCodes.contains(normalizeReasonCode(reasonCode))) {
        count += 1;
      }
    }
    return count;
  }

  static Map<String, Integer> buildUnscheduledReasonDistribution(List<Map<String, Object>> unscheduled) {
    Map<String, Integer> distribution = new LinkedHashMap<>();
    if (unscheduled == null) {
      return distribution;
    }
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      String normalized = normalizeReasonCode(reasonCode == null ? "UNKNOWN" : reasonCode);
      if (normalized.isBlank()) {
        normalized = "UNKNOWN";
      }
      distribution.merge(normalized, 1, Integer::sum);
    }
    return distribution;
  }

  static String scheduleReasonNameCn(String reasonCode) {
    String normalized = MvpStoreCoreNormalizationSupport.normalizeCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_MANPOWER" -> "瑜版挸澧犻悵顓燁偧娴滃搫濮忄稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "CAPACITY_MACHINE" -> "瑜版挸澧犻悵顓燁偧鐠佄儳顦稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "MATERIAL_SHORTAGE" -> "瑜版挸澧犻悵顓燁偧閻椻晜鏋￿稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "COMPONENT_SHORTAGE" -> "BOM缂佸嫪娆㈤張顏堢秷婵傛鍨ㄩ張顏勫煂閺傛瑱绱濊ぐ鎾충閻濐厽顐奸弮鐘崇《閹烘帊楠";
      case "TRANSFER_CONSTRAINT" -> "閸欐娓剁亸蹇氭祮鏉╂劖澹掗柌蹇嬧偓浣瑰闂傖鐡戝鍛灗閺堚偓鐏忄繑澹掑▎锟犳閸掅绱濊ぐ鎾충閻濐厽顐奸弳鍌欑瑝閸欘垱甯";
      case "BEFORE_EXPECTED_START" -> "閺堫亜鍩岀拋銏犲礋妫板嫯顓稿鈧慨瀣闂傖揪绱濊ぐ鎾충閻濐厽顐奸弳鍌欑瑝閹烘帊楠";
      case "DEPENDENCY_BLOCKED", "DEPENDENCY_LIMIT" -> "閸欐澧犳惔蹇撴紣鎼村繒瀹抽弶鐕傜礉閸氬骸绨弳鍌欄娑撳秴褰茬紒褏鐢婚幒鎺嶉獓";
      case "FROZEN_BY_POLICY" -> "鐠併垹宕熛径鍕艾閸愯崵绮ㄧ粵鏍殣娑擃叏绱濇稉宥呭棘娑撳孩婀版潪顔藉笓娴";
      case "LOCKED_PRESERVED" -> "闁夿礁宕熛幐澶婄唨缁惧じ绻氓悾娆欑礉閺堫剝鐤嗘稉宥夊櫢閹烘帟顕氓柈銊ュ瀻娴犅濮";
      case "URGENT_GUARANTEE" -> "閸旂姵鈧儴顓归崡鏇⌒曢崣鎴滅箽鎼存洝绁┃鎰灗閺堚偓娴ｅ孩妫╂禍褍鍤穱婵囧Б";
      case "LOCK_PREEMPTED_BY_URGENT" -> "娑撅桨绻氓梾婊冨閹儴顓归崡鏇礉闁夿礁宕熛挧鍕爱鐞氼偊鍎撮崚鍡氼唨娴";
      case "CAPACITY_LIMIT", "CAPACITY_UNKNOWN" -> "瑜版挸澧犻悵顓燁偧閸欘垳鏁ゆ禍褑鍏樄稉宥堝喕閿涘牅姹夐崝?鐠佄儳顦?閻椻晜鏋￠崣妤呄閿";
      case "UNKNOWN", "" -> "閺堫亝鐖ｇ拋鏉垮斧閸";
      default -> normalized;
    };
  }

  static String resolveUnscheduledReasonCode(Map<String, Object> unscheduledRow) {
    if (unscheduledRow == null) {
      return null;
    }
    String reasonCode = MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(
      unscheduledRow,
      "reason_code",
      "reasonCode",
      "last_block_reason",
      "lastBlockReason"
    ));
    if (!reasonCode.isBlank()) {
      return normalizeReasonCode(reasonCode);
    }
    Object reasonsObj = unscheduledRow.get("reasons");
    if (reasonsObj instanceof List<?> reasons) {
      for (Object reason : reasons) {
        String code = MvpStoreCoreNormalizationSupport.normalizeCode(String.valueOf(reason));
        if (!code.isBlank()) {
          return normalizeReasonCode(code);
        }
      }
    }
    return null;
  }

  static String normalizeReasonCode(String reasonCode) {
    String normalized = MvpStoreCoreNormalizationSupport.normalizeCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_LIMIT" -> "CAPACITY_UNKNOWN";
      case "DEPENDENCY_LIMIT" -> "DEPENDENCY_BLOCKED";
      default -> normalized;
    };
  }

  static String toLegacyReasonCode(String reasonCode) {
    String normalized = normalizeReasonCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_MANPOWER", "CAPACITY_MACHINE", "MATERIAL_SHORTAGE", "COMPONENT_SHORTAGE", "CAPACITY_UNKNOWN" -> "CAPACITY_LIMIT";
      case "DEPENDENCY_BLOCKED", "TRANSFER_CONSTRAINT", "BEFORE_EXPECTED_START" -> "DEPENDENCY_LIMIT";
      default -> normalized;
    };
  }

  static String topReasonCode(Map<String, Integer> reasonCountByCode) {
    if (reasonCountByCode == null || reasonCountByCode.isEmpty()) {
      return null;
    }
    return reasonCountByCode.entrySet().stream()
      .sorted((a, b) -> {
        int byCount = Integer.compare(b.getValue(), a.getValue());
        if (byCount != 0) {
          return byCount;
        }
        return a.getKey().compareTo(b.getKey());
      })
      .map(Map.Entry::getKey)
      .findFirst()
      .orElse(null);
  }
}

