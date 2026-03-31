package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import com.autoproduction.mvp.module.job.JobEntity;
import com.autoproduction.mvp.module.job.JobQueryService;
import com.autoproduction.mvp.module.job.JobType;
import com.autoproduction.mvp.module.orderexecution.persistence.OrderProjectionService;
import com.autoproduction.mvp.module.orderexecution.persistence.ReportingProjectionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionReadService {
  private final MvpStoreService store;
  private final JobQueryService jobQueryService;
  private final OrderProjectionService orderProjectionService;
  private final ReportingProjectionService reportingProjectionService;

  public OrderExecutionReadService(
    MvpStoreService store,
    JobQueryService jobQueryService,
    OrderProjectionService orderProjectionService,
    ReportingProjectionService reportingProjectionService
  ) {
    this.store = store;
    this.jobQueryService = jobQueryService;
    this.orderProjectionService = orderProjectionService;
    this.reportingProjectionService = reportingProjectionService;
  }

  public List<Map<String, Object>> listOrders() {
    return orderProjectionService.listOrders();
  }

  public List<Map<String, Object>> listReportings() {
    return reportingProjectionService.listReportings();
  }

  public Map<String, Object> getImportTaskStatus(String requestId) {
    JobEntity job = jobQueryService.findLatestByTypes(Set.of(JobType.ERP_IMPORT));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("background_task", ErpImportJobViewSupport.toBackgroundTask(job));
    return out;
  }
}
