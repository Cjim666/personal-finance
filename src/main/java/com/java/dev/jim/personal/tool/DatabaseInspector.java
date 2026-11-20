package com.java.dev.jim.personal.tool;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * 本地 H2 的只读检查工具。
 *
 * <p>该工具只允许预定义的读取命令，避免将临时排查脚本散落在项目外。默认脱敏展示交易敏感字段；
 * 仅 {@code unclassified-expenses} 命令会输出交易对方或摘要，用于用户已授权的分类规则分析。</p>
 */
public final class DatabaseInspector {
    private static final Set<String> SENSITIVE_COLUMNS = Set.of(
            "account_name", "counterparty", "summary", "raw_data", "external_id", "file_hash", "file_name", "error_message");

    private DatabaseInspector() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            printUsage();
            return;
        }

        try (Connection connection = openReadOnlyConnection()) {
            switch (args[0]) {
                case "tables" -> printTables(connection);
                case "columns" -> printColumns(connection, required(args, 1, "请提供表名"));
                case "rows" -> printRows(connection, required(args, 1, "请提供表名"), limit(args, 2), contains(args, "--include-sensitive"));
                case "batches" -> printBatches(connection);
                case "unclassified-expenses" -> printUnclassifiedExpenses(connection, limit(args, 1));
                case "classification-summary" -> printClassificationSummary(connection);
                case "neutral-repayments" -> printNeutralRepayments(connection);
                default -> throw new IllegalArgumentException("不支持的命令：" + args[0]);
            }
        }
    }

    private static Connection openReadOnlyConnection() throws IOException, SQLException {
        Properties properties = loadProperties();
        Connection connection = DriverManager.getConnection(
                properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                resolve(properties.getProperty("spring.datasource.password")));
        connection.setReadOnly(true);
        return connection;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        load(properties, Path.of("src/main/resources/application.properties"));
        return properties;
    }

    private static void load(Properties properties, Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
    }

    private static String resolve(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) return value;
        String expression = value.substring(2, value.length() - 1);
        int separator = expression.indexOf(':');
        String variable = separator < 0 ? expression : expression.substring(0, separator);
        String fallback = separator < 0 ? "" : expression.substring(separator + 1);
        return System.getenv().getOrDefault(variable, fallback);
    }

    private static void printTables(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet result = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            System.out.println("表名\t说明");
            while (result.next()) System.out.printf("%s\t%s%n", result.getString("TABLE_NAME"), safe(result.getString("REMARKS")));
        }
    }

    private static void printColumns(Connection connection, String table) throws SQLException {
        requireTable(connection, table);
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet result = metaData.getColumns(connection.getCatalog(), null, table, "%")) {
            System.out.println("字段\t类型\t可为空\t默认值\t说明");
            while (result.next()) {
                System.out.printf("%s\t%s\t%s\t%s\t%s%n", result.getString("COLUMN_NAME"), result.getString("TYPE_NAME"),
                        "YES".equals(result.getString("IS_NULLABLE")) ? "是" : "否", safe(result.getString("COLUMN_DEF")), safe(result.getString("REMARKS")));
            }
        }
    }

    private static void printRows(Connection connection, String table, int limit, boolean includeSensitive) throws SQLException {
        requireTable(connection, table);
        String sql = "SELECT * FROM `" + table + "` LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                printHeader(metadata);
                while (result.next()) {
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        if (column > 1) System.out.print('\t');
                        String name = metadata.getColumnLabel(column).toLowerCase(Locale.ROOT);
                        Object value = includeSensitive || !SENSITIVE_COLUMNS.contains(name) ? result.getObject(column) : "[已脱敏]";
                        System.out.print(printable(value));
                    }
                    System.out.println();
                }
            }
        }
    }

    private static void printBatches(Connection connection) throws SQLException {
        String sql = """
                SELECT status AS "状态", COUNT(*) AS "批次数", SUM(total_count) AS "解析交易数", SUM(duplicate_count) AS "去重数"
                FROM import_batch
                GROUP BY status
                ORDER BY status
                """;
        printQuery(connection, sql);
    }

    private static void printUnclassifiedExpenses(Connection connection, int limit) throws SQLException {
        String sql = """
                SELECT t.source AS "来源",
                       COALESCE(NULLIF(TRIM(t.counterparty), ''), NULLIF(TRIM(t.summary), ''), '(空)') AS "分类文本",
                       COUNT(*) AS "交易数",
                       SUM(t.amount) AS "金额合计"
                FROM finance_transaction t
                JOIN import_batch b ON b.id = t.import_batch_id
                WHERE b.status = 'CONFIRMED'
                  AND t.direction = 'EXPENSE'
                  AND t.category_origin = 'UNCLASSIFIED'
                GROUP BY t.source, COALESCE(NULLIF(TRIM(t.counterparty), ''), NULLIF(TRIM(t.summary), ''), '(空)')
                ORDER BY COUNT(*) DESC, SUM(t.amount) DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                printResult(result);
            }
        }
    }

    private static void printClassificationSummary(Connection connection) throws SQLException {
        String sql = """
                SELECT t.direction AS "方向", t.category_origin AS "分类来源", COALESCE(t.category, '未分类') AS "分类",
                       COUNT(*) AS "交易数", SUM(t.amount) AS "金额合计"
                FROM finance_transaction t
                JOIN import_batch b ON b.id = t.import_batch_id
                WHERE b.status = 'CONFIRMED'
                GROUP BY t.direction, t.category_origin, COALESCE(t.category, '未分类')
                ORDER BY t.direction, t.category_origin, SUM(t.amount) DESC
                """;
        printQuery(connection, sql);
    }

    private static void printNeutralRepayments(Connection connection) throws SQLException {
        String sql = """
                SELECT source AS "来源", COUNT(*) AS "交易数", SUM(amount) AS "金额合计"
                FROM finance_transaction
                WHERE direction = 'NEUTRAL'
                  AND (
                      LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%信用卡自动还款%'
                      OR LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%信用卡还款%'
                      OR LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%支付宝信贷业务待还款账户%'
                      OR (source = 'ALIPAY'
                          AND LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%花呗%'
                          AND LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%还款%')
                  )
                GROUP BY source
                ORDER BY source
                """;
        printQuery(connection, sql);
    }

    private static void printQuery(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            printResult(result);
        }
    }

    private static void printResult(ResultSet result) throws SQLException {
        ResultSetMetaData metadata = result.getMetaData();
        printHeader(metadata);
        while (result.next()) {
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if (column > 1) System.out.print('\t');
                System.out.print(printable(result.getObject(column)));
            }
            System.out.println();
        }
    }

    private static void printHeader(ResultSetMetaData metadata) throws SQLException {
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            if (column > 1) System.out.print('\t');
            System.out.print(metadata.getColumnLabel(column));
        }
        System.out.println();
    }

    private static void requireTable(Connection connection, String table) throws SQLException {
        try (ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            if (!result.next()) throw new IllegalArgumentException("不存在的业务表：" + table);
        }
    }

    private static String required(String[] args, int index, String message) {
        if (args.length <= index || args[index].isBlank()) throw new IllegalArgumentException(message);
        return args[index];
    }

    private static int limit(String[] args, int index) {
        if (args.length <= index || args[index].startsWith("--")) return 50;
        int value = Integer.parseInt(args[index]);
        if (value < 1 || value > 500) throw new IllegalArgumentException("条数必须在 1 到 500 之间");
        return value;
    }

    private static boolean contains(String[] args, String option) {
        return Arrays.asList(args).contains(option);
    }

    private static String printable(Object value) {
        if (value == null) return "-";
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return String.valueOf(value).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String safe(String value) {
        return value == null ? "-" : printable(value);
    }

    private static void printUsage() {
        System.out.println("用法：DatabaseInspector <tables|columns|rows|batches|unclassified-expenses|classification-summary|neutral-repayments> [参数]");
        System.out.println("  tables");
        System.out.println("  columns <表名>");
        System.out.println("  rows <表名> [1-500] [--include-sensitive]");
        System.out.println("  batches");
        System.out.println("  unclassified-expenses [1-500]");
        System.out.println("  classification-summary");
        System.out.println("  neutral-repayments");
    }
}
