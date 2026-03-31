package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import com.autoproduction.mvp.module.job.InMemoryJobRepository;
import com.autoproduction.mvp.module.job.JobCommandService;
import com.autoproduction.mvp.module.job.JobQueryService;
import com.autoproduction.mvp.module.integration.erp.persistence.ErpSnapshotPersistenceService;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ErpDataManager {
  private static final Logger log = LoggerFactory.getLogger(ErpDataManager.class);
  private static final InMemoryJobRepository LEGACY_JOB_REPOSITORY = new InMemoryJobRepository();

  private final ErpDataManagerConfig config;
  private final ErpRefreshCoordinator refreshCoordinator;
  private final ErpMaterialIssueCache materialIssueCache;
  private final ErpMaterialSupplyCache materialSupplyCache;
  private final ErpMaterialInventoryCache materialInventoryCache;
  private final ErpSnapshotPersistenceService snapshotPersistenceService;
  private final boolean includeErpOrdersInOrderPool;
  private final boolean prewarmEnabled;
  private final int prewarmMaxOrders;
  private final int prewarmMaxMaterials;
  private final ExecutorService prewarmExecutor;
  private final AtomicBoolean prewarmInProgress = new AtomicBoolean(false);

  @Autowired
  public ErpDataManager(
    ErpSqliteOrderLoader loader,
    @Value("${mvp.erp.refresh.enabled:true}") boolean refreshEnabled,
    @Value("${mvp.erp.refresh.trigger-min-interval-ms:5000}") long triggerMinIntervalMs,
    @Value("${mvp.erp.refresh.read-through-on-empty:true}") boolean readThroughOnEmpty,
    @Value("${mvp.erp.order-pool.include-erp:false}") boolean includeErpOrdersInOrderPool,
    @Value("${mvp.erp.base-url:${ERP_BASE_URL:}}") String baseUrl,
    @Value("${mvp.erp.acct-id:${ERP_ACCT_ID:}}") String acctId,
    @Value("${mvp.erp.username:${ERP_USERNAME:}}") String username,
    @Value("${mvp.erp.password:${ERP_PASSWORD:}}") String password,
    @Value("${mvp.erp.lcid:${ERP_LCID:2052}}") int lcid,
    @Value("${mvp.erp.timeout:${ERP_TIMEOUT:30}}") int timeoutSeconds,
    @Value("${mvp.erp.verify-ssl:${ERP_VERIFY_SSL:true}}") boolean verifySsl,
    @Value("${mvp.erp.material-issue-cache-ttl-ms:60000}") long materialIssueCacheTtlMs,
    @Value("${mvp.erp.prewarm.enabled:true}") boolean prewarmEnabled,
    @Value("${mvp.erp.prewarm.max-orders:16}") int prewarmMaxOrders,
    @Value("${mvp.erp.prewarm.max-materials:120}") int prewarmMaxMaterials,
    ErpSnapshotPersistenceService snapshotPersistenceService,
    JobCommandService jobCommandService,
    JobQueryService jobQueryService
  ) {
    this.config = new ErpDataManagerConfig(
      refreshEnabled,
      Math.max(0L, triggerMinIntervalMs),
      readThroughOnEmpty,
      trimToEmpty(baseUrl),
      trimToEmpty(acctId),
      trimToEmpty(username),
      password == null ? "" : password,
      lcid,
      timeoutSeconds,
      verifySsl,
      Math.max(0L, materialIssueCacheTtlMs)
    );
    this.includeErpOrdersInOrderPool = includeErpOrdersInOrderPool;
    this.prewarmEnabled = prewarmEnabled;
    this.prewarmMaxOrders = Math.max(0, prewarmMaxOrders);
    this.prewarmMaxMaterials = Math.max(0, prewarmMaxMaterials);
    this.snapshotPersistenceService = snapshotPersistenceService;
    this.prewarmExecutor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "erp-cache-prewarm");
      thread.setDaemon(true);
      return thread;
    });
    this.materialIssueCache = new ErpMaterialIssueCache(loader, snapshotPersistenceService, this::latestSnapshotId, this.config.materialIssueCacheTtlMs());
    this.materialInventoryCache = new ErpMaterialInventoryCache(loader, snapshotPersistenceService, this::latestSnapshotId);
    ErpSnapshotLoader snapshotLoader = new ErpSnapshotLoader(loader);
    this.refreshCoordinator = new ErpRefreshCoordinator(this.config, snapshotLoader, snapshotPersistenceService, jobCommandService, jobQueryService);
    this.materialSupplyCache = new ErpMaterialSupplyCache(
      loader,
      snapshotPersistenceService,
      this::latestSnapshotId
    );
    this.refreshCoordinator.setCacheInvalidator(() -> {
      materialIssueCache.clear();
      materialSupplyCache.clear();
      materialInventoryCache.clear();
      scheduleCachePrewarm();
    });
  }

  ErpDataManager(
    ErpSqliteOrderLoader loader,
    boolean refreshEnabled,
    long triggerMinIntervalMs,
    boolean readThroughOnEmpty
  ) {
    this(
      loader,
      refreshEnabled,
      triggerMinIntervalMs,
      readThroughOnEmpty,
      false,
      "",
      "",
      "",
      "",
      2052,
      30,
      true,
      60_000L,
      true,
      16,
      120,
      null,
      new JobCommandService(LEGACY_JOB_REPOSITORY),
      new JobQueryService(LEGACY_JOB_REPOSITORY)
    );
  }

  public boolean shouldIncludeErpOrdersInOrderPool() {
    return includeErpOrdersInOrderPool;
  }

  public List<Map<String, Object>> getSalesOrderLines() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().salesOrderLines();
  }

  public List<Map<String, Object>> getProductionOrders() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().productionOrders();
  }

  public List<Map<String, Object>> getProductionMaterialIssuesByOrder(String productionOrderNo, String materialListNo) {
    return getProductionMaterialIssuesByOrder(productionOrderNo, materialListNo, false);
  }

  public List<Map<String, Object>> getProductionMaterialIssuesByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    return materialIssueCache.getProductionMaterialIssuesByOrder(productionOrderNo, materialListNo, forceRefresh);
  }

  public List<Map<String, Object>> getPurchaseOrders() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().purchaseOrders();
  }

  public Map<String, Object> getMaterialSupplyInfo(String materialCode) {
    return materialSupplyCache.getMaterialSupplyInfo(materialCode);
  }

  public Map<String, Map<String, Object>> getMaterialInventoryByCodes(List<String> materialCodes, boolean forceRefresh) {
    return materialInventoryCache.getMaterialInventoryByCodes(materialCodes, forceRefresh);
  }

  public List<Map<String, Object>> getSalesOrderHeadersRaw() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().salesOrderHeadersRaw();
  }

  public List<Map<String, Object>> getSalesOrderLinesRaw() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().salesOrderLinesRaw();
  }

  public List<Map<String, Object>> getProductionOrderHeadersRaw() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().productionOrderHeadersRaw();
  }

  public List<Map<String, Object>> getProductionOrderLinesRaw() {
    refreshCoordinator.ensureCacheReady();
    return refreshCoordinator.snapshot().productionOrderLinesRaw();
  }

  public Map<String, Object> getRefreshStatus(String requestId) {
    return refreshCoordinator.getRefreshStatus(requestId);
  }

  public Map<String, Object> refreshScheduled() {
    return refreshCoordinator.refreshScheduled();
  }

  public Map<String, Object> refreshManual(String requestId, String operator, String reason) {
    return refreshCoordinator.refreshManual(requestId, operator, reason);
  }

  public Map<String, Object> refreshTriggered(String triggerType, String requestId, String reason) {
    return refreshCoordinator.refreshTriggered(triggerType, requestId, reason);
  }

  public long getRefreshVersionToken() {
    return refreshCoordinator.lastSuccessEpochMs();
  }

  public void prewarmProductionMaterialIssuesAsync(List<String> productionOrderNos, int maxOrders) {
    if (!prewarmEnabled || productionOrderNos == null || productionOrderNos.isEmpty() || maxOrders <= 0) {
      return;
    }
    List<String> normalizedOrderNos = productionOrderNos.stream()
      .filter(orderNo -> orderNo != null && !orderNo.isBlank())
      .map(String::trim)
      .distinct()
      .limit(maxOrders)
      .toList();
    if (normalizedOrderNos.isEmpty()) {
      return;
    }
    prewarmExecutor.execute(() -> {
      for (String orderNo : normalizedOrderNos) {
        try {
          materialIssueCache.getProductionMaterialIssuesByOrder(orderNo, null, false);
        } catch (RuntimeException ex) {
          log.debug("Skip ERP material issue prewarm for order {}", orderNo, ex);
        }
      }
    });
  }

  @PreDestroy
  void shutdown() {
    refreshCoordinator.shutdown();
    prewarmExecutor.shutdownNow();
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private void scheduleCachePrewarm() {
    if (!prewarmEnabled || prewarmMaxOrders <= 0 || prewarmMaxMaterials <= 0) {
      return;
    }
    if (!prewarmInProgress.compareAndSet(false, true)) {
      return;
    }
    prewarmExecutor.execute(() -> {
      try {
        prewarmCaches();
      } catch (RuntimeException ex) {
        log.warn("ERP cache prewarm failed.", ex);
      } finally {
        prewarmInProgress.set(false);
      }
    });
  }

  private void prewarmCaches() {
    List<Map<String, Object>> productionOrders = refreshCoordinator.snapshot().productionOrders();
    if (productionOrders == null || productionOrders.isEmpty()) {
      return;
    }

    Set<String> materialCodes = new LinkedHashSet<>();
    int warmedOrders = 0;
    for (Map<String, Object> orderRow : productionOrders) {
      if (orderRow == null) {
        continue;
      }
      String orderNo = firstText(orderRow, "production_order_no", "order_no", "orderNo");
      String materialListNo = firstText(orderRow, "material_list_no", "materialListNo");
      if (isBlank(orderNo) && isBlank(materialListNo)) {
        continue;
      }

      List<Map<String, Object>> issueRows = materialIssueCache.getProductionMaterialIssuesByOrder(orderNo, materialListNo, false);
      for (Map<String, Object> issueRow : issueRows) {
        String materialCode = firstText(issueRow, "child_material_code", "material_code", "materialCode");
        if (isBlank(materialCode)) {
          continue;
        }
        materialCodes.add(materialCode.trim());
        if (materialCodes.size() >= prewarmMaxMaterials) {
          break;
        }
      }
      warmedOrders += 1;
      if (warmedOrders >= prewarmMaxOrders || materialCodes.size() >= prewarmMaxMaterials) {
        break;
      }
    }

    if (materialCodes.isEmpty()) {
      return;
    }
    List<String> codes = new ArrayList<>(materialCodes);
    materialInventoryCache.getMaterialInventoryByCodes(codes, false);
    for (String code : codes) {
      materialSupplyCache.getMaterialSupplyInfo(code);
    }
  }

  private static String firstText(Map<String, Object> row, String... keys) {
    if (row == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Object value = row.get(key);
      if (value == null) {
        continue;
      }
      String text = String.valueOf(value).trim();
      if (!text.isEmpty()) {
        return text;
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String latestSnapshotId() {
    return snapshotPersistenceService == null ? null : snapshotPersistenceService.latestSnapshotId();
  }

}
