package com.example.pontal.service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.pontal.dto.AuditLogExportRow;

//監査ログのエクスポート(CSV)の中身を作るクラス
public final class AuditLogCsvFormatter {

    //先頭に付けるBOM。これが無いと、ExcelでUTF-8のCSVを開いたときに日本語が文字化けする
    private static final String BOM = "\uFEFF";
    private static final String NEWLINE = "\r\n"; //CSVの標準(RFC4180)の改行
    private static final String[] HEADERS = { "日時", "操作者ID", "操作者名", "対象社員ID", "対象社員名", "種別", "リソースID", "操作" };
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuditLogCsvFormatter() {
    }

    //ログの一覧から、ヘッダーつきのCSVの文字列を作る(0件ならヘッダーだけ)
    public static String format(List<AuditLogExportRow> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        appendLine(sb, HEADERS);
        for (AuditLogExportRow row : rows) {
            appendLine(sb, new String[] {
                    row.getOccurredAt().format(DATE_TIME),
                    String.valueOf(row.getActorEmployeeId()),
                    row.getActorName(),
                    String.valueOf(row.getTargetEmployeeId()),
                    row.getTargetName(),
                    row.getResourceType(),
                    String.valueOf(row.getResourceId()),
                    row.getAction() });
        }
        return sb.toString();
    }

    //1行分を、カンマでつないで末尾に追加する
    private static void appendLine(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append(NEWLINE);
    }

    //セルの値をCSV用に整える
    static String escape(String value) {
        String s = value;
        //Excelで開いたときに数式として実行されないよう、=+-@で始まる値は先頭に'を付ける(名前は人が入力した値のため)
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        //カンマ・ダブルクォート・改行を含む値は、全体を"で囲み、中の"は""にする
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
