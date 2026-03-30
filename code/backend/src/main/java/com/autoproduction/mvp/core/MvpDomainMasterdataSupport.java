package com.autoproduction.mvp.core;

import java.time.LocalDate;

final class MvpDomainMasterdataSupport {
  private MvpDomainMasterdataSupport() {}
}

class MvpDomainProcessConfigBase {
  String processCode;
  double capacityPerShift;
  int requiredWorkers;
  int requiredMachines;

  MvpDomainProcessConfigBase(String processCode, double capacityPerShift, int requiredWorkers, int requiredMachines) {
    this.processCode = processCode;
    this.capacityPerShift = capacityPerShift;
    this.requiredWorkers = requiredWorkers;
    this.requiredMachines = requiredMachines;
  }
}

class MvpDomainProcessStepBase {
  String processCode;
  String dependencyType;

  MvpDomainProcessStepBase(String processCode, String dependencyType) {
    this.processCode = processCode;
    this.dependencyType = dependencyType;
  }
}

class MvpDomainShiftRowBase {
  LocalDate date;
  String shiftCode;
  boolean open;

  MvpDomainShiftRowBase(LocalDate date, String shiftCode, boolean open) {
    this.date = date;
    this.shiftCode = shiftCode;
    this.open = open;
  }
}

class MvpDomainResourceRowBase {
  LocalDate date;
  String shiftCode;
  String processCode;
  int available;

  MvpDomainResourceRowBase(LocalDate date, String shiftCode, String processCode, int available) {
    this.date = date;
    this.shiftCode = shiftCode;
    this.processCode = processCode;
    this.available = available;
  }
}

class MvpDomainMaterialRowBase {
  LocalDate date;
  String shiftCode;
  String productCode;
  String processCode;
  double availableQty;

  MvpDomainMaterialRowBase(LocalDate date, String shiftCode, String productCode, String processCode, double availableQty) {
    this.date = date;
    this.shiftCode = shiftCode;
    this.productCode = productCode;
    this.processCode = processCode;
    this.availableQty = availableQty;
  }
}

class MvpDomainLineProcessBindingBase {
  String companyCode;
  String workshopCode;
  String lineCode;
  String lineName;
  String processCode;
  boolean enabled;
  double capacityPerShift;
  int requiredWorkers;
  int requiredMachines;

  MvpDomainLineProcessBindingBase(
    String companyCode,
    String workshopCode,
    String lineCode,
    String lineName,
    String processCode,
    boolean enabled,
    double capacityPerShift,
    int requiredWorkers,
    int requiredMachines
  ) {
    this.companyCode = companyCode;
    this.workshopCode = workshopCode;
    this.lineCode = lineCode;
    this.lineName = lineName;
    this.processCode = processCode;
    this.enabled = enabled;
    this.capacityPerShift = capacityPerShift;
    this.requiredWorkers = requiredWorkers;
    this.requiredMachines = requiredMachines;
  }
}

