package com.autoproduction.mvp.core.erp.loader;

import java.nio.charset.StandardCharsets;

public final class ErpLoaderTextUtils {
  private ErpLoaderTextUtils() {}

  public static String fixPotentialUtf8Mojibake(String text) {
    String normalized = ErpLoaderValueUtils.firstText(text);
    if (normalized == null) {
      return null;
    }
    if (containsCjk(normalized) || !containsLatin1Supplement(normalized)) {
      return normalized;
    }
    try {
      String repaired = new String(normalized.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
      if (repaired.contains("\uFFFD")) {
        return normalized;
      }
      if (containsCjk(repaired)) {
        return repaired;
      }
    } catch (RuntimeException ignored) {
      return normalized;
    }
    return normalized;
  }

  private static boolean containsLatin1Supplement(String text) {
    for (int i = 0; i < text.length(); i += 1) {
      char ch = text.charAt(i);
      if (ch >= '\u00C0' && ch <= '\u00FF') {
        return true;
      }
    }
    return false;
  }

  private static boolean containsCjk(String text) {
    for (int i = 0; i < text.length();) {
      int codePoint = text.codePointAt(i);
      Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
      if (
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
          || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
      ) {
        return true;
      }
      i += Character.charCount(codePoint);
    }
    return false;
  }
}

