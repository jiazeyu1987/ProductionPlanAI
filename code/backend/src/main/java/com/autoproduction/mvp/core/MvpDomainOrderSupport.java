package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class MvpDomainOrderSupport {
  private MvpDomainOrderSupport() {}
}

class MvpDomainOrderItemBase {
  String productCode;
  double qty;
  double completedQty;

  MvpDomainOrderItemBase(String productCode, double qty, double completedQty) {
    this.productCode = productCode;
    this.qty = qty;
    this.completedQty = completedQty;
  }
}

class MvpDomainOrderBusinessDataBase {
  LocalDate orderDate;
  String customerRemark;
  String productName;
  String specModel;
  String productionBatchNo;
  String packagingForm;
  String salesOrderNo;
  String productionDateForeignTrade;
  String purchaseDueDate;
  String injectionDueDate;
  String marketRemarkInfo;
  double marketDemand;
  String plannedFinishDate1;
  String plannedFinishDate2;
  String semiFinishedCode;
  double semiFinishedInventory;
  double semiFinishedDemand;
  double semiFinishedWip;
  double needOrderQty;
  double pendingInboundQty;
  String weeklyMonthlyPlanRemark;
  String workshopOuterPackagingDate;
  String note;
  double workshopCompletedQty;
  String workshopCompletedTime;
  double outerCompletedQty;
  String outerCompletedTime;
  String matchStatus;

  MvpDomainOrderBusinessDataBase() {
    this.orderDate = null;
    this.customerRemark = "";
    this.productName = "";
    this.specModel = "";
    this.productionBatchNo = "";
    this.packagingForm = "";
    this.salesOrderNo = "";
    this.productionDateForeignTrade = "";
    this.purchaseDueDate = "";
    this.injectionDueDate = "";
    this.marketRemarkInfo = "";
    this.marketDemand = 0d;
    this.plannedFinishDate1 = "";
    this.plannedFinishDate2 = "";
    this.semiFinishedCode = "";
    this.semiFinishedInventory = 0d;
    this.semiFinishedDemand = 0d;
    this.semiFinishedWip = 0d;
    this.needOrderQty = 0d;
    this.pendingInboundQty = 0d;
    this.weeklyMonthlyPlanRemark = "";
    this.workshopOuterPackagingDate = "";
    this.note = "";
    this.workshopCompletedQty = 0d;
    this.workshopCompletedTime = "";
    this.outerCompletedQty = 0d;
    this.outerCompletedTime = "";
    this.matchStatus = "";
  }

  MvpDomainOrderBusinessDataBase(MvpDomain.OrderBusinessData source) {
    this.orderDate = source.orderDate;
    this.customerRemark = source.customerRemark;
    this.productName = source.productName;
    this.specModel = source.specModel;
    this.productionBatchNo = source.productionBatchNo;
    this.packagingForm = source.packagingForm;
    this.salesOrderNo = source.salesOrderNo;
    this.productionDateForeignTrade = source.productionDateForeignTrade;
    this.purchaseDueDate = source.purchaseDueDate;
    this.injectionDueDate = source.injectionDueDate;
    this.marketRemarkInfo = source.marketRemarkInfo;
    this.marketDemand = source.marketDemand;
    this.plannedFinishDate1 = source.plannedFinishDate1;
    this.plannedFinishDate2 = source.plannedFinishDate2;
    this.semiFinishedCode = source.semiFinishedCode;
    this.semiFinishedInventory = source.semiFinishedInventory;
    this.semiFinishedDemand = source.semiFinishedDemand;
    this.semiFinishedWip = source.semiFinishedWip;
    this.needOrderQty = source.needOrderQty;
    this.pendingInboundQty = source.pendingInboundQty;
    this.weeklyMonthlyPlanRemark = source.weeklyMonthlyPlanRemark;
    this.workshopOuterPackagingDate = source.workshopOuterPackagingDate;
    this.note = source.note;
    this.workshopCompletedQty = source.workshopCompletedQty;
    this.workshopCompletedTime = source.workshopCompletedTime;
    this.outerCompletedQty = source.outerCompletedQty;
    this.outerCompletedTime = source.outerCompletedTime;
    this.matchStatus = source.matchStatus;
  }
}

class MvpDomainOrderBase {
  String orderNo;
  String orderType;
  LocalDate dueDate;
  LocalDate expectedStartDate;
  boolean urgent;
  boolean frozen;
  boolean lockFlag;
  String status;
  List<MvpDomain.OrderItem> items;
  MvpDomain.OrderBusinessData businessData;

  MvpDomainOrderBase(
    String orderNo,
    String orderType,
    LocalDate dueDate,
    LocalDate expectedStartDate,
    boolean urgent,
    boolean frozen,
    boolean lockFlag,
    String status,
    List<MvpDomain.OrderItem> items,
    MvpDomain.OrderBusinessData businessData
  ) {
    this.orderNo = orderNo;
    this.orderType = orderType;
    this.dueDate = dueDate;
    this.expectedStartDate = expectedStartDate;
    this.urgent = urgent;
    this.frozen = frozen;
    this.lockFlag = lockFlag;
    this.status = status;
    this.items = new ArrayList<>(items);
    this.businessData = businessData == null ? new MvpDomain.OrderBusinessData() : new MvpDomain.OrderBusinessData(businessData);
  }
}

