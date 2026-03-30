package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.List;
import java.util.Map;

public record MaterialIssueCacheEntry(
  List<Map<String, Object>> rows,
  long cachedAtMs
) {}

