package com.zifang.z.dms.core.explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 语句闸的裁决测试：只读白名单 / 写形态识别 / 多语句 / 长度 / 摘要。
 *
 * <p>这是 SQL 控制台的安全边界，用例刻意覆盖了常见的绕过形态
 * （注释藏写动词、CTE 包写语句、INTO OUTFILE、FOR UPDATE）。
 */
class SqlStatementGuardTest {

    private final SqlStatementGuard readOnly = new SqlStatementGuard(true, 10000, 1000);
    private final SqlStatementGuard allowWrite = new SqlStatementGuard(false, 10000, 1000);

    // ---------------------------------------------------------- READ 白名单

    @Test
    void readStatementsPassReadOnlyGate() {
        for (String sql : new String[]{
                "SELECT * FROM t",
                "SELECT 1",
                "SHOW TABLES",
                "SHOW CREATE TABLE `oc`.`t`",
                "DESC t",
                "DESCRIBE t",
                "EXPLAIN SELECT * FROM t",
                "WITH cte AS (SELECT 1) SELECT * FROM cte",
        }) {
            assertEquals(SqlStatementGuard.Kind.READ, readOnly.classify(readOnly.check(sql)), sql);
        }
    }

    @Test
    void writeStatementsRejectedOnReadOnlyGate() {
        for (String sql : new String[]{
                "UPDATE t SET a = 1",
                "DELETE FROM t",
                "INSERT INTO t VALUES (1)",
                "DROP TABLE t",
                "TRUNCATE TABLE t",
                "ALTER TABLE t ADD COLUMN c INT",
                "CREATE TABLE t (id INT)",
                "GRANT ALL ON *.* TO 'u'@'%'",
                "SET GLOBAL max_connections = 1",
                "CALL do_something()",
        }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> readOnly.check(sql), sql);
            assertTrue(e.getMessage().contains("只读"), sql);
        }
    }

    // ---------------------------------------------------------- 绕过形态

    @Test
    void commentHiddenWriteStillRejected() {
        // 注释里藏不了事：剥离注释后首关键字还是 UPDATE
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("/* 注释 */ SELECT 1; -- x\nUPDATE t SET a=1"));
    }

    @Test
    void cteWrappingWriteRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("WITH cte AS (SELECT 1 FROM t) DELETE FROM t WHERE id IN (SELECT * FROM cte)"));
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("WITH cte AS (SELECT 1) UPDATE t SET a=1"));
    }

    @Test
    void intoOutfileRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("SELECT * FROM t INTO OUTFILE '/tmp/x'"));
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("SELECT * FROM t INTO DUMPFILE '/tmp/x'"));
    }

    @Test
    void forUpdateRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("SELECT * FROM t WHERE id = 1 FOR UPDATE"));
    }

    @Test
    void lockTablesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("LOCK TABLES t WRITE"));
        assertThrows(IllegalArgumentException.class,
                () -> readOnly.check("UNLOCK TABLES"));
    }

    // ---------------------------------------------------------- 写通道

    @Test
    void allowWriteChannelPassesWriteStatements() {
        assertEquals(SqlStatementGuard.Kind.WRITE, allowWrite.classify(allowWrite.check(
                "UPDATE t SET a = 1 WHERE id = 2", true)));
        // 写闸不开写时照样拒
        assertThrows(IllegalArgumentException.class,
                () -> allowWrite.check("UPDATE t SET a = 1", false));
    }

    // ---------------------------------------------------------- 多语句 / 长度 / 空语句

    @Test
    void multiStatementRejected() {
        assertThrows(IllegalArgumentException.class, () -> readOnly.check("SELECT 1; SELECT 2"));
    }

    @Test
    void overLengthRejected() {
        SqlStatementGuard small = new SqlStatementGuard(true, 10, 1000);
        assertThrows(IllegalArgumentException.class, () -> small.check("SELECT * FROM some_long_table"));
    }

    @Test
    void blankRejected() {
        assertThrows(IllegalArgumentException.class, () -> readOnly.check("   "));
        assertThrows(IllegalArgumentException.class, () -> readOnly.check(null));
    }

    // ---------------------------------------------------------- 摘要

    @Test
    void digestStripsLiteralsAndCompresses() {
        String d = SqlStatementGuard.digest("SELECT *\n  FROM t  WHERE name = 'secret-value-abc'", 300);
        assertTrue(!d.contains("secret-value-abc"), "摘要里不能有字面量: " + d);
        assertTrue(d.contains("?"), "字面量应被抹成 ?: " + d);
    }

    @Test
    void digestTruncates() {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < 200; i++) {
            sb.append(i).append(", ");
        }
        String d = SqlStatementGuard.digest(sb.toString(), 50);
        assertTrue(d.length() <= 51, "超长要截断: " + d.length());
        assertTrue(d.endsWith("…"));
    }

    // ---------------------------------------------------------- 分类一致性

    @Test
    void classifyAgreesWithCheckGate() {
        // 闸与分类器必须同一个事实来源：分类 READ 的语句在只读闸必过，WRITE 必拒
        for (String sql : new String[]{
                "SELECT * FROM t", "UPDATE t SET a=1", "SHOW DATABASES", "DROP INDEX i ON t",
        }) {
            SqlStatementGuard.Kind kind = readOnly.classify(readOnly.check(sql, true));
            if (kind == SqlStatementGuard.Kind.READ) {
                readOnly.check(sql);
            } else {
                assertThrows(IllegalArgumentException.class, () -> readOnly.check(sql), sql);
            }
        }
    }
}
