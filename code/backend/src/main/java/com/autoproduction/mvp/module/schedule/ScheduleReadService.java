package com.autoproduction.mvp.module.schedule;

import com.autoproduction.mvp.core.MvpStoreService;
import com.autoproduction.mvp.module.job.JobEntity;
import com.autoproduction.mvp.module.job.JobQueryService;
import com.autoproduction.mvp.module.job.JobType;
import com.autoproduction.mvp.module.schedule.persistence.ScheduleProjectionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ScheduleReadService {
  private final MvpStoreService store;
  private final JobQueryService jobQueryService;
  private final ScheduleProjectionService scheduleProjectionService;

  public ScheduleReadService(
    MvpStoreService store,
    JobQueryService jobQueryService,
    ScheduleProjectionService scheduleProjectionService
  ) {
    this.store = store;
    this.jobQueryService = jobQueryService;
    this.scheduleProjectionService = scheduleProjectionService;
  }

  public List<Map<String, Object>> listSchedules() {
    return scheduleProjectionService.listSchedules();
  }

  public Map<String, Object> getLatestSchedule() {
    return scheduleProjectionService.latestSchedule();
  }

  public Map<String, Object> getSchedule(String versionNo) {
    return scheduleProjectionService.getScheduleSnapshot(versionNo);
  }

  public Map<String, Object> validateSchedule(String versionNo) {
    return store.validateSchedule(versionNo);
  }

  public Map<String, Object> getGenerateTaskStatus(String requestId) {
    JobEntity job = jobQueryService.findLatestByTypes(Set.of(JobType.SCHEDULE_GENERATE));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("background_task", ScheduleJobViewSupport.toBackgroundTask(job));
    return out;
  }
}
