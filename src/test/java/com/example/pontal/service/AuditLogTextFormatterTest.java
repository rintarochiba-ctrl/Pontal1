package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.pontal.dto.AuditLogExportRow;

//AuditLogTextFormatterのテスト。文字列を作るだけの処理なので、モックは使わず、結果の文字列をそのまま確認する
class AuditLogTextFormatterTest {

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

    @Test
    void format_withNoRows_returnsOnlyHeaderAndSeparator() {
        String text = AuditLogTextFormatter.format(List.of());

        String[] lines = text.split("\n");
        assertThat(lines).hasSize(2);//ヘッダーと、その下の線だけ
        assertThat(lines[0]).startsWith("日時").contains("操作者").contains("対象社員").endsWith("操作");
        assertThat(lines[1]).matches("^-+( {2}-+)*$");
        assertThat(text).endsWith("\n");
    }

    @Test
    void format_putsOneRowPerLine_afterHeaderAndSeparator() {
        List<AuditLogExportRow> rows = List.of(
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 22), 2L, "佐藤花子", 4L, "権限なしテスト", "SALARY_SLIP", 8L, "DOWNLOAD"),
                row(LocalDateTime.of(2026, 10, 6, 9, 0, 1), 1L, "山田太郎", 4L, "権限なしテスト", "SALARY_SLIP", 9L, "CREATE"));

        String[] lines = AuditLogTextFormatter.format(rows).split("\n");

        assertThat(lines).hasSize(4);//ヘッダー + 線 + 2行
        assertThat(lines[2]).contains("2026-10-05 14:15:22", "2 佐藤花子", "4 権限なしテスト", "SALARY_SLIP", "8", "DOWNLOAD");
        assertThat(lines[3]).contains("2026-10-06 09:00:01", "1 山田太郎", "CREATE");
    }

    @Test
    void format_alignsColumns_evenWhenNamesContainJapanese() {
        //名前の長さ・半角全角が違っても、列の始まる位置が、そろう
        List<AuditLogExportRow> rows = List.of(
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 22), 2L, "佐藤花子", 4L, "A", "SALARY_SLIP", 8L, "DOWNLOAD"),
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 23), 11L, "Bob", 4L, "権限なしテスト", "SALARY_SLIP", 9L, "CREATE"));

        String[] lines = AuditLogTextFormatter.format(rows).split("\n");

        //「種別」の列(SALARY_SLIP)が始まる位置を、表示幅で数えて比べる
        int widthBeforeType1 = AuditLogTextFormatter.displayWidth(lines[2].substring(0, lines[2].indexOf("SALARY_SLIP")));
        int widthBeforeType2 = AuditLogTextFormatter.displayWidth(lines[3].substring(0, lines[3].indexOf("SALARY_SLIP")));
        assertThat(widthBeforeType1).isEqualTo(widthBeforeType2);
        //ヘッダーの「種別」も、同じ位置から始まる
        int widthBeforeHeaderType = AuditLogTextFormatter.displayWidth(lines[0].substring(0, lines[0].indexOf("種別")));
        assertThat(widthBeforeHeaderType).isEqualTo(widthBeforeType1);
    }

    @Test
    void format_hasNoTrailingSpaces() {
        List<AuditLogExportRow> rows = List.of(
                row(LocalDateTime.of(2026, 10, 5, 14, 15, 22), 2L, "佐藤花子", 4L, "A", "SALARY_SLIP", 8L, "DOWNLOAD"));

        for (String line : AuditLogTextFormatter.format(rows).split("\n")) {
            assertThat(line).isEqualTo(line.stripTrailing());//行末に余計な空白が付かない
        }
    }

    @Test
    void displayWidth_countsJapaneseAsTwoAndAsciiAsOne() {
        assertThat(AuditLogTextFormatter.displayWidth("abc")).isEqualTo(3);
        assertThat(AuditLogTextFormatter.displayWidth("佐藤花子")).isEqualTo(8);
        assertThat(AuditLogTextFormatter.displayWidth("A佐藤")).isEqualTo(5);
        assertThat(AuditLogTextFormatter.displayWidth("ｱｲｳ")).isEqualTo(3);//半角カナは1
        assertThat(AuditLogTextFormatter.displayWidth("")).isEqualTo(0);
    }
}
