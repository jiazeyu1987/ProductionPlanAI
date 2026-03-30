package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.Map;

public record MaterialSupplyCacheEntry(
  Map<String, Object> data,
  long cachedAtMs
) {}

