package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreMasterdataRouteSupport extends MvpStoreMasterdataProcessConfigSupport {
  protected MvpStoreMasterdataRouteSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }
}

