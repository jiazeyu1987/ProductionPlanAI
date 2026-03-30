package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreCommandLogicSupport extends MvpStoreCommandOrderPatchSupport {
  protected MvpStoreCommandLogicSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }
}

