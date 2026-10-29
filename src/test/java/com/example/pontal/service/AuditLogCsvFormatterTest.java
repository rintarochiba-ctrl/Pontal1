package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.pontal.dto.AuditLogExportRow;

//AuditLogCsvFormatterのテスト。文字列を作るだけの処理なので、モックは使わず、結果の文字列をそのまま確認する
class AuditLogCsvFormatterTest {

    private static final String BOM = "﻿";

    private AuditLogExportRow row(LocalDateTime at, Long actorId, String actorName,
            Long targetId, String targetName, String type, Long resourceId, String action) {
        AuditLogExportRow r = new AuditLogExportRow();
        r.setOccurredAt(at);
        r.setActorEmployeeId(actorId);
        r.setActorName(actorName);
        r.setTargetEmployeeId(targetId);
        r.setTargetName(targetName);
        r.setResourceType(type);
        r.setResourceId(resourceId);
        r.setAction(action);
        return r;
    }

    //BOMを除いて、行ごとに分ける(改行はCRLF)
    private String[] lines(String csv) {
        return csv.substring(BOM.length()).split("\r\n");
    }

    @Test
    void format_startsWithBom_soThatExcelReadsJapaneseCorrectly() {
        assertThat(AuditLogCsvFormatter.format(List.of())).startsWith(BOM);
    }

    @Test
    void format_withNoRows_returnsOnlyHeader() {
        String csv = AuditLogCsvFormatter.format(List.of());

        assertThat(lines(csv)).containsExactly("日時,操作者ID,操作者名,対象社員ID,対象社員名,種別,リソースID,操作");
        assertThat(csv).endsWith("\r\n");
    }

    @Test
    void format_putsOneRowPerLine_withIdAndNameInSeparateColumns() {
        List<AuditLogExportRow> rows = List.of(
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 22), 2L, "佐藤花子", 4L, "権限なしテスト", "SALARY_SLIP", 8L, "DOWNLOAD"),
                row(LocalDateTime.of(2026, 10, 6, 9, 0, 1), 1L, "山田太郎", 4L, "権限なしテスト", "SALARY_SLIP", 9L, "CREATE"));

        String[] lines = lines(AuditLogCsvFormatter.format(rows));

        assertThat(lines).hasSize(3);//ヘッダー + 2行
        assertThat(lines[1]).isEqualTo("2026-10-05 14:15:22,2,佐藤花子,4,権限なしテスト,SALARY_SLIP,8,DOWNLOAD");
        assertThat(lines[2]).isEqualTo("2026-10-06 09:00:01,1,山田太郎,4,権限なしテスト,SALARY_SLIP,9,CREATE");
    }

    // ---------- escape ----------

    @Test
    void escape_leavesPlainValueAsIs() {
        assertThat(AuditLogCsvFormatter.escape("佐藤花子")).isEqualTo("佐藤花子");
        assertThat(AuditLogCsvFormatter.escape("")).isEqualTo("");
    }

    @Test
    void escape_quotesValueContainingComma() {
        assertThat(AuditLogCsvFormatter.escape("佐藤,花子")).isEqualTo("\"佐藤,花子\"");
    }

    @Test
    void escape_doublesQuotesInsideValue() {
        assertThat(AuditLogCsvFormatter.escape("山田\"太郎\"")).isEqualTo("\"山田\"\"太郎\"\"\"");
    }

    @Test
    void escape_quotesValueContainingNewline() {
        assertThat(AuditLogCsvFormatter.escape("山田\n太郎")).isEqualTo("\"山田\n太郎\"");
        assertThat(AuditLogCsvFormatter.escape("山田\r太郎")).isEqualTo("\"山田\r太郎\"");
    }

    @Test
    void escape_prefixesApostrophe_whenValueCouldBeRunAsExcelFormula() {
        //=+-@で始まる値は、Excelで開いたときに数式として実行されることがある
        assertThat(AuditLogCsvFormatter.escape("=1+1")).isEqualTo("'=1+1");
        assertThat(AuditLogCsvFormatter.escape("+1")).isEqualTo("'+1");
        assertThat(AuditLogCsvFormatter.escape("-1")).isEqualTo("'-1");
        assertThat(AuditLogCsvFormatter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        //途中にあるだけなら、そのまま
        assertThat(AuditLogCsvFormatter.escape("a=b")).isEqualTo("a=b");
    }

    @Test
    void format_keepsColumnsAligned_whenNameContainsComma() {
        //名前にカンマが入っていても、列の数がずれない(囲まれた値は1つのセルとして扱われる)
        List<AuditLogExportRow> rows = List.of(
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 22), 2L, "佐藤,花子", 4L, "A", "SALARY_SLIP", 8L, "DOWNLOAD"));

        String[] lines = lines(AuditLogCsvFormatter.format(rows));

        assertThat(lines[1]).isEqualTo("2026-10-05 14:15:22,2,\"佐藤,花子\",4,A,SALARY_SLIP,8,DOWNLOAD");
    }
}
