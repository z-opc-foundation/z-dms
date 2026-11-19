package com.zifang.z.dms.core.explorer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 语句闸（Bytebase「访问控制」口径的落点）。
 *
 * <p><b>只读模式（默认）</b>下语句首关键字必须在白名单里（SELECT / SHOW / DESCRIBE /
 * DESC / EXPLAIN / WITH），并且拒绝任何写形态的尾巴（{@code INTO OUTFILE} /
 * {@code INTO DUMPFILE} / {@code FOR UPDATE} / {@code LOCK TABLES}）；
 * {@code WITH} 里带 CTE 写（INSERT/UPDATE/DELETE）也拒绝。
 *
 * <p><b>所有模式</b>都拒绝：
 * <ul>
 *   <li><b>多语句</b>（注释与字符串字面量剥掉后还剩第二个 {@code ;}）——
 *       这是 {@code SELECT 1; DROP TABLE x} 的唯一入口；</li>
 *   <li>超长语句（防止一次贴进来的 SQL 把网关和管理端打爆）。</li>
 * </ul>
 *
 * <p>判断基于「剥掉注释与字符串字面量」后的文本 ——
 * {@code SELECT * FROM t WHERE note = 'drop table x'} 不会被误伤，
 * {@code SELECT 1; -- 注释里也不算} 也不会漏放。
 */
public final class SqlStatementGuard {

    /** 语句类别：READ = 查询类；WRITE = 写形态（首关键字或尾巴暴露）。 */
    public enum Kind { READ, WRITE }

    /** 只读模式允许的语句首关键字。 */
    private static final java.util.Set<String> READ_ONLY_KEYWORDS = new java.util.HashSet<>(
            java.util.Arrays.asList("SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "WITH"));

    /** 只读模式下任何位置都不允许出现的写形态（剥掉字面量后按词匹配）。 */
    private static final Pattern[] READ_ONLY_BLOCKED = {
            Pattern.compile("\\bINTO\\s+OUTFILE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bINTO\\s+DUMPFILE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bFOR\\s+UPDATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bLOCK\\s+TABLES\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bUNLOCK\\s+TABLES\\b", Pattern.CASE_INSENSITIVE),
    };

    /** CTE（WITH ... ）里出现的写动词。 */
    private static final Pattern CTE_WRITE = Pattern.compile(
            "\\bINSERT\\b|\\bUPDATE\\b|\\bDELETE\\b", Pattern.CASE_INSENSITIVE);

    private final boolean readOnly;
    private final int maxSqlLength;
    private final int maxRows;

    public SqlStatementGuard(boolean readOnly, int maxSqlLength, int maxRows) {
        this.readOnly = readOnly;
        this.maxSqlLength = maxSqlLength;
        this.maxRows = maxRows;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public int getMaxRows() {
        return maxRows;
    }

    /**
     * 校验一条语句。不合法 ⇒ {@link IllegalArgumentException}（消息可读、可直接给前端）。
     *
     * @return 剥掉注释与字面量后的文本（调用方可拿去做摘要）
     */
    public String check(String sql) {
        return check(sql, !readOnly);
    }

    /**
     * 校验一条语句（调用方决定是否放行写语句）。
     *
     * <p>SQL 控制台用这个重载实现权限分离：只读权限点（{@code DMS_SQL_EXECUTE}）只放行
     * {@link Kind#READ}；写语句必须同时满足「服务端开写开关」且用户持有写权限点
     * （{@code DMS_SQL_WRITE}）—— 两道都在调用方判断，本闸只做语句形态裁决。
     *
     * @return 剥掉注释与字面量后的文本（调用方可拿去做摘要）
     */
    public String check(String sql, boolean allowWrite) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (sql.length() > maxSqlLength) {
            throw new IllegalArgumentException("SQL 长度 " + sql.length()
                    + " 超过上限 " + maxSqlLength + " 字符");
        }
        String stripped = stripCommentsAndLiterals(sql);
        assertNoMultiStatement(stripped);
        if (!allowWrite && classify(stripped) == Kind.WRITE) {
            throw new IllegalArgumentException(
                    "只读模式下不允许执行写语句（只允许 SELECT / SHOW / DESC / DESCRIBE / EXPLAIN / WITH）");
        }
        return stripped;
    }

    /**
     * 语句类别裁决：首关键字不在查询白名单、WITH 里带写动词、或尾巴是写形态 ⇒ WRITE。
     */
    public Kind classify(String stripped) {
        String keyword = leadingKeyword(stripped);
        if (!keyword.isEmpty() && !READ_ONLY_KEYWORDS.contains(keyword)) {
            return Kind.WRITE;
        }
        if ("WITH".equals(keyword) && CTE_WRITE.matcher(stripped).find()) {
            return Kind.WRITE;
        }
        for (Pattern p : READ_ONLY_BLOCKED) {
            if (p.matcher(stripped).find()) {
                return Kind.WRITE;
            }
        }
        return Kind.READ;
    }

    /** 语句摘要（进审计流水用）：剥字面量、压空白、截断。 */
    public static String digest(String sql, int maxChars) {
        if (sql == null) {
            return null;
        }
        String s = stripCommentsAndLiterals(sql).replaceAll("\\s+", " ").trim();
        return s.length() > maxChars ? s.substring(0, maxChars) + "…" : s;
    }

    // ------------------------------------------------------------ 内部工具

    private void assertNoMultiStatement(String stripped) {
        int cut = stripped.length();
        while (cut > 0 && (stripped.charAt(cut - 1) == ';' || Character.isWhitespace(stripped.charAt(cut - 1)))) {
            cut--;
        }
        String body = stripped.substring(0, cut);
        int sep = body.indexOf(';');
        if (sep >= 0 && !body.substring(sep + 1).trim().isEmpty()) {
            throw new IllegalArgumentException("一次只允许执行一条语句（检测到多个分号分隔的语句）");
        }
    }

    /** 剥掉块注释 / 行注释 / 字符串字面量 / 反引号标识符，其余空白压成一个空格。 */
    static String stripCommentsAndLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        boolean lastWs = true;
        while (i < n) {
            char c = sql.charAt(i);
            // 块注释
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            // 行注释（-- 与 #）
            if (c == '#' || (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-')) {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            // 字符串字面量（'' 转义与 \' 转义都处理）
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < n) {
                    char t = sql.charAt(i);
                    if (t == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (t == quote) {
                        if (i + 1 < n && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append('?');
                lastWs = false;
                continue;
            }
            // 反引号标识符保留（identifier 里可能有特殊字符，交给引号语义）
            if (c == '`') {
                int end = sql.indexOf('`', i + 1);
                if (end < 0) {
                    i = n;
                    continue;
                }
                out.append(sql, i, end + 1);
                lastWs = false;
                i = end + 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lastWs) {
                    out.append(' ');
                    lastWs = true;
                }
                i++;
                continue;
            }
            out.append(c);
            lastWs = false;
            i++;
        }
        return out.toString().trim();
    }

    /** 语句首关键字（跳过前导括号后取第一个词）。 */
    static String leadingKeyword(String stripped) {
        int i = 0;
        int n = stripped.length();
        while (i < n && (Character.isWhitespace(stripped.charAt(i)) || stripped.charAt(i) == '(')) {
            i++;
        }
        int start = i;
        while (i < n && Character.isLetterOrDigit(stripped.charAt(i)) || (i < n && stripped.charAt(i) == '_')) {
            i++;
        }
        String word = stripped.substring(start, i).toUpperCase(Locale.ROOT);
        return word;
    }
}
