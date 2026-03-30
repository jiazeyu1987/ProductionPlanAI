package com.autoproduction.mvp.module.integration.erp;

import com.autoproduction.mvp.core.ErpSqliteOrderLoader;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ErpDataManager {
  private final ErpDataManagerConfig config;
  private final ErpRefreshCoordinator refreshCoordinator;
  private final ErpMaterialIssueCache materialIssueCache;
  private final ErpMaterialSupplyCache materialSupplyCache;
  private final ErpMaterialInventoryCache materialInventoryCache;
  private final boolean includeErpOrdersInOrderPool;

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
    @Value("${mvp.erp.material-issue-cache-ttl-ms:60000}") long materialIssueCacheTtlMs
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
    this.materialIssueCache = new ErpMaterialIssueCache(loader, this.config.materialIssueCacheTtlMs());
    this.materialInventoryCache = new ErpMaterialInventoryCache(loader);
    ErpSnapshotLoader snapshotLoader = new ErpSnapshotLoader(loader);
    this.refreshCoordinator = new ErpRefreshCoordinator(this.config, snapshotLoader);
    this.materialSupplyCache = new ErpMaterialSupplyCache(
      loader,
      refreshCoordinator::ensureCacheReady,
      refreshCoordinator::snapshot
    );
    this.refreshCoordinator.setCacheInvalidator(() -> {
      materialIssueCache.clear();
      materialSupplyCache.clear();
      materialInventoryCache.clear();
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
      60_000L
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
  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

}
