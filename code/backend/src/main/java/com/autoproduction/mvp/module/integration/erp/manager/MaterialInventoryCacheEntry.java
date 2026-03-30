package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.Map;

public record MaterialInventoryCacheEntry(
  Map<String, Object> data,
  long cachedAtMs
) {}

