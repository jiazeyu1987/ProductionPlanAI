package com.autoproduction.mvp.core;

final class MvpDomain {

  private MvpDomain() {}

  static final class State extends MvpDomainStateBase {}

  static final class OrderItem extends MvpDomainOrderItemBase {
    OrderItem(String productCode, double qty, double completedQty) {
      super(productCode, qty, completedQty);
    }
  }

  static final class OrderBusinessData extends MvpDomainOrderBusinessDataBase {
    OrderBusinessData() {
      super();
    }

    OrderBusinessData(OrderBusinessData source) {
      super(source);
    }
  }

  static final class Order extends MvpDomainOrderBase {
    Order(
      String orderNo,
      String orderType,
      java.time.LocalDate dueDate,
      java.time.LocalDate expectedStartDate,
      boolean urgent,
      boolean frozen,
      boolean lockFlag,
      String status,
      java.util.List<OrderItem> items,
      OrderBusinessData businessData
    ) {
      super(orderNo, orderType, dueDate, expectedStartDate, urgent, frozen, lockFlag, status, items, businessData);
    }
  }

  static final class ProcessConfig extends MvpDomainProcessConfigBase {
    ProcessConfig(String processCode, double capacityPerShift, int requiredWorkers, int requiredMachines) {
      super(processCode, capacityPerShift, requiredWorkers, requiredMachines);
    }
  }

  static final class ProcessStep extends MvpDomainProcessStepBase {
    ProcessStep(String processCode, String dependencyType) {
      super(processCode, dependencyType);
    }
  }

  static final class ShiftRow extends MvpDomainShiftRowBase {
    ShiftRow(java.time.LocalDate date, String shiftCode, boolean open) {
      super(date, shiftCode, open);
    }
  }

  static final class ResourceRow extends MvpDomainResourceRowBase {
    ResourceRow(java.time.LocalDate date, String shiftCode, String processCode, int available) {
      super(date, shiftCode, processCode, available);
    }
  }

  static final class MaterialRow extends MvpDomainMaterialRowBase {
    MaterialRow(java.time.LocalDate date, String shiftCode, String productCode, String processCode, double availableQty) {
      super(date, shiftCode, productCode, processCode, availableQty);
    }
  }

  static final class LineProcessBinding extends MvpDomainLineProcessBindingBase {
    LineProcessBinding(
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
      super(companyCode, workshopCode, lineCode, lineName, processCode, enabled, capacityPerShift, requiredWorkers, requiredMachines);
    }

    LineProcessBinding(
      String companyCode,
      String workshopCode,
      String lineCode,
      String lineName,
      String processCode,
      boolean enabled
    ) {
      this(companyCode, workshopCode, lineCode, lineName, processCode, enabled, 1d, 1, 1);
    }

    LineProcessBinding(
      String workshopCode,
      String lineCode,
      String lineName,
      String processCode,
      boolean enabled
    ) {
      this("COMPANY-MAIN", workshopCode, lineCode, lineName, processCode, enabled);
    }

    LineProcessBinding(
      String workshopCode,
      String lineCode,
      String lineName,
      String processCode,
      boolean enabled,
      double capacityPerShift,
      int requiredWorkers,
      int requiredMachines
    ) {
      this("COMPANY-MAIN", workshopCode, lineCode, lineName, processCode, enabled, capacityPerShift, requiredWorkers, requiredMachines);
    }
  }

  static final class ScheduleTask extends MvpDomainScheduleTaskBase {}

  static final class Allocation extends MvpDomainAllocationBase {}

  static final class ScheduleVersion extends MvpDomainScheduleVersionBase {}

  static final class Reporting extends MvpDomainReportingBase {}
}
