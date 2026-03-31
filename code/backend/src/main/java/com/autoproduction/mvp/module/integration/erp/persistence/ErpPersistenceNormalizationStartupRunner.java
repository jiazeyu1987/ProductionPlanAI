package com.autoproduction.mvp.module.integration.erp.persistence;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ErpPersistenceNormalizationStartupRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(ErpPersistenceNormalizationStartupRunner.class);

  private final ErpSnapshotPersistenceService persistenceService;
  private final boolean enabled;

  public ErpPersistenceNormalizationStartupRunner(
    ErpSnapshotPersistenceService persistenceService,
    @Value("${mvp.erp.persistence.normalize-json-on-startup:true}") boolean enabled
  ) {
    this.persistenceService = persistenceService;
    this.enabled = enabled;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    Map<String, Integer> updated = persistenceService.normalizeWrappedJsonColumns();
    int total = updated.values().stream().mapToInt(Integer::intValue).sum();
    if (total > 0) {
      log.info("Normalized wrapped ERP JSON payloads on startup: {}", updated);
    }
  }
}
