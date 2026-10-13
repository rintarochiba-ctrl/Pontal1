package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

//AuditLogExportSchedulerの単体テスト。エクスポート本体はモックに差し替え、S3にもDBにも繋がない
//「いつ動くか」(cron)は、実際に待たずに、アノテーションの設定値を読んで確認する
@ExtendWith(MockitoExtension.class)
class AuditLogExportSchedulerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    @Mock
    private AuditLogExportService exportService;

    @InjectMocks
    private AuditLogExportScheduler scheduler;

    @Test
    void exportLastWeek_callsExportWeekWithLastMonday() {
        scheduler.exportLastWeek();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(exportService).exportWeek(captor.capture());
        LocalDate periodStart = captor.getValue();

        //渡された日は月曜日で、今日から見て「先週」の範囲に入っている(今日は先週の月曜の7〜13日後)
        assertThat(periodStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        long daysAgo = ChronoUnit.DAYS.between(periodStart, LocalDate.now(JST));
        assertThat(daysAgo).isBetween(7L, 13L);
    }

    @Test
    void exportLastWeek_doesNotThrow_whenExportFails() {
        //exportWeekの失敗はサービス側でFAILEDとして記録済み。スケジューラは例外を外に出さずログだけ残す
        doThrow(new RuntimeException("S3 error")).when(exportService).exportWeek(any(LocalDate.class));

        assertThatCode(() -> scheduler.exportLastWeek()).doesNotThrowAnyException();
    }

    @Test
    void exportLastWeek_isScheduledEveryMondayAt3amInJapanTime() throws NoSuchMethodException {
        Method method = AuditLogExportScheduler.class.getMethod("exportLastWeek");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        //cronの「:」より後ろがデフォルト値(propertiesに書かなければ、毎週月曜の3時)
        assertThat(scheduled.cron()).isEqualTo("${audit-log.export.cron:0 0 3 * * MON}");
        //サーバーがUTCでも、日本時間で動くこと
        assertThat(scheduled.zone()).isEqualTo("Asia/Tokyo");
    }
}
