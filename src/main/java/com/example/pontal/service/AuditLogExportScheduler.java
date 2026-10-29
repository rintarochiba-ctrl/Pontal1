package com.example.pontal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//毎週月曜の朝に、先週分の監査ログをエクスポートする係。何をするかはAuditLogExportServiceに任せる
@Component
public class AuditLogExportScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogExportScheduler.class);
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final AuditLogExportService exportService;

    public AuditLogExportScheduler(AuditLogExportService exportService) {
        this.exportService = exportService;
    }

    //秒 分 時 日 月 曜日。デフォルトは毎週月曜の3時。開発で試すときはpropertiesで書き換えられる
    @Scheduled(cron = "${audit-log.export.cron:0 0 3 * * MON}", zone = "Asia/Tokyo")
    public void exportLastWeek() {
        //今日が属する週の月曜から1週間戻すと、先週の月曜になる
        LocalDate lastMonday = LocalDate.now(JST)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        try {
            exportService.exportWeek(lastMonday);
        } catch (RuntimeException e) {
            //失敗の記録(FAILED)はexportWeekの中でやっているので、ここではログを残して終わる
            log.error("監査ログの週次エクスポートに失敗しました periodStart={}", lastMonday, e);
        }
    }
}
