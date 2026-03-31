package com.autoproduction.mvp.module.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoproduction.mvp.core.MvpStoreService;
import com.autoproduction.mvp.module.job.JobQueryService;
import com.autoproduction.mvp.module.schedule.persistence.ScheduleProjectionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleReadServiceTest {
  @Test
  void listSchedulesShouldPreferPersistedProjectionHistoryWhenAvailable() {
    MvpStoreService store = mock(MvpStoreService.class);
    JobQueryService jobQueryService = mock(JobQueryService.class);
    ScheduleProjectionService scheduleProjectionService = mock(ScheduleProjectionService.class);
    ScheduleReadService service = new ScheduleReadService(store, jobQueryService, scheduleProjectionService);

    List<Map<String, Object>> projected = List.of(Map.of("version_no", "V001"));
    when(scheduleProjectionService.listSchedules()).thenReturn(projected);

    List<Map<String, Object>> result = service.listSchedules();

    assertSame(projected, result);
    verify(store, never()).listSchedules();
  }

  @Test
  void listSchedulesShouldReturnEmptyWhenNoPersistedVersionExists() {
    MvpStoreService store = mock(MvpStoreService.class);
    JobQueryService jobQueryService = mock(JobQueryService.class);
    ScheduleProjectionService scheduleProjectionService = mock(ScheduleProjectionService.class);
    ScheduleReadService service = new ScheduleReadService(store, jobQueryService, scheduleProjectionService);

    when(scheduleProjectionService.listSchedules()).thenReturn(List.of());

    List<Map<String, Object>> result = service.listSchedules();

    assertEquals(List.of(), result);
    verify(store, never()).listSchedules();
  }
}
