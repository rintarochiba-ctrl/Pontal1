package com.example.pontal.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.example.pontal.dto.AuditLogExportRow;

//監査ログのエクスポート(txt)の中身を作るクラス
public final class AuditLogTextFormatter {

    private static final String[] HEADERS = { "日時", "操作者", "対象社員", "種別", "ID", "操作" };
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = "  "; //列と列の間

    private AuditLogTextFormatter() {
    }

    //ログの一覧から、ヘッダーと区切り線つきの、幅のそろった文字列を作る(0件ならヘッダーだけ)
    public static String format(List<AuditLogExportRow> rows) {
        //表のセルを全部、先に文字列にしておく(1行目はヘッダー)
        List<String[]> table = new ArrayList<>();
        table.add(HEADERS);
        for (AuditLogExportRow row : rows) {
            table.add(new String[] {
                    row.getOccurredAt().format(DATE_TIME),
                    row.getActorEmployeeId() + " " + row.getActorName(),
                    row.getTargetEmployeeId() + " " + row.getTargetName(),
                    row.getResourceType(),
                    String.valueOf(row.getResourceId()),
                    row.getAction() });
        }

        //列ごとに、一番幅の広いセルに合わせる
        int[] widths = new int[HEADERS.length];
        for (String[] cells : table) {
            for (int i = 0; i < cells.length; i++) {
                widths[i] = Math.max(widths[i], displayWidth(cells[i]));
            }
        }

        StringBuilder sb = new StringBuilder();
        appendLine(sb, table.get(0), widths);

        //ヘッダーの下の線
        String[] lines = new String[widths.length];
        for (int i = 0; i < widths.length; i++) {
            lines[i] = "-".repeat(widths[i]);
        }
        appendLine(sb, lines, widths);

        for (int r = 1; r < table.size(); r++) {
            appendLine(sb, table.get(r), widths);
        }
        return sb.toString();
    }

    //1行分を、列の幅に合わせて空白で埋めて、末尾に追加する
    private static void appendLine(StringBuilder sb, String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            line.append(cells[i]);
            line.append(" ".repeat(widths[i] - displayWidth(cells[i])));
        }
        sb.append(line.toString().stripTrailing()).append('\n');
    }

    //表示したときの幅。日本語(全角)は半角の2倍の幅で表示されるので、2と数える
    static int displayWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int codePoint = s.codePointAt(i);
            width += isWide(codePoint) ? 2 : 1;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    //全角として表示される文字か(ひらがな・カタカナ・漢字・全角記号など。半角カナは除く)
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x2E80 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || codePoint >= 0x20000;
    }
}
