package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreMasterdataPatchSupport extends MvpStoreCommandLogicSupport {
  protected MvpStoreMasterdataPatchSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected List<MvpDomain.ResourceRow> filterResourceRowsByProcessCodes(
    List<MvpDomain.ResourceRow> rows,
    Set<String> validProcessCodes
  ) {
    return MvpStoreMasterdataPatchLookupSupport.filterResourceRowsByProcessCodes(this, rows, validProcessCodes);
  }

  protected Set<String> routeProcessCodeSet() {
    return MvpStoreMasterdataPatchLookupSupport.routeProcessCodeSet(this);
  }

  protected MvpDomain.ProcessConfig processConfigByCode(String processCode) {
    return MvpStoreMasterdataPatchLookupSupport.processConfigByCode(this, processCode);
  }

  protected void applyLineTopologyPatch(List<Map<String, Object>> rows) {
    MvpStoreMasterdataPatchLineTopologySupport.applyLineTopologyPatch(this, rows);
  }

  protected void applyInitialCarryoverPatch(List<Map<String, Object>> rows) {
    MvpStoreMasterdataPatchInitialCarryoverSupport.applyInitialCarryoverPatch(this, rows);
  }

  protected void applyMaterialAvailabilityPatch(List<Map<String, Object>> rows) {
    MvpStoreMasterdataPatchMaterialAvailabilitySupport.applyMaterialAvailabilityPatch(this, rows);
  }

  protected MvpDomain.ResourceRow findResourceRow(
    List<MvpDomain.ResourceRow> pool,
    LocalDate date,
    String shiftCode,
    String processCode
  ) {
    return MvpStoreMasterdataPatchLookupSupport.findResourceRow(this, pool, date, shiftCode, processCode);
  }

  protected MvpDomain.ShiftRow findShiftRow(LocalDate date, String shiftCode) {
    return MvpStoreMasterdataPatchLookupSupport.findShiftRow(this, date, shiftCode);
  }

  protected MvpDomain.MaterialRow findMaterialRow(
    LocalDate date,
    String shiftCode,
    String productCode,
    String processCode
  ) {
    return MvpStoreMasterdataPatchLookupSupport.findMaterialRow(this, date, shiftCode, productCode, processCode);
  }

  protected LocalDate parseConfigDate(String dateText) {
    return MvpStoreMasterdataPatchDateSupport.parseConfigDate(this, dateText);
  }
}

