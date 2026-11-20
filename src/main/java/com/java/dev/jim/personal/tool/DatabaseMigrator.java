package com.java.dev.jim.personal.tool;

import org.flywaydb.core.Flyway;

import java.math.BigDecimal;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 一次性将旧 MySQL 数据迁移到本地 H2 文件数据库。
 *
 * <p>迁移前会先执行 H2 Flyway 迁移，随后保留业务主键、去重字段、金额、分类和批次状态。</p>
 */
public final class DatabaseMigrator {
    private static final String DEFAULT_H2_URL = "jdbc:h2:file:./data/personal;MODE=MySQL";
    private static final String DEFAULT_MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/personal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

    private DatabaseMigrator() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        Properties legacy = loadLegacyProperties();
        String mysqlPasswordEnv = options.getOrDefault("mysql-password-env", "PERSONAL_MYSQL_PASSWORD");
        String mysqlPassword = System.getenv().getOrDefault(mysqlPasswordEnv, legacy.getProperty("spring.datasource.password", ""));
        String mysqlUrl = options.getOrDefault("mysql-url", legacy.getProperty("spring.datasource.url", DEFAULT_MYSQL_URL));
        String mysqlUser = options.getOrDefault("mysql-user", legacy.getProperty("spring.datasource.username", "root"));
        String h2Url = options.getOrDefault("h2-url", DEFAULT_H2_URL);
        String h2User = options.getOrDefault("h2-user", "sa");
        String h2Password = options.getOrDefault("h2-password", "");

        Flyway.configure().dataSource(h2Url, h2User, h2Password)
                .locations("classpath:db/migration/h2").baselineOnMigrate(false).load().migrate();
        try (Connection source = DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPassword);
             Connection target = DriverManager.getConnection(h2Url, h2User, h2Password)) {
            source.setReadOnly(true);
            target.setAutoCommit(false);
            ensureTargetIsEmpty(target);
            Map<Long, Long> categoryIds = copyCategories(source, target);
            copyBatches(source, target);
            copyTransactions(source, target, categoryIds);
            copyRules(source, target, categoryIds);
            restartIdentity(target, "category");
            restartIdentity(target, "import_batch");
            restartIdentity(target, "finance_transaction");
            restartIdentity(target, "category_rule");
            target.commit();
            printSummary(target);
        }
    }

    private static Map<Long, Long> copyCategories(Connection source, Connection target) throws SQLException {
        String select = "SELECT id, name, active, sort_order FROM category ORDER BY id";
        Map<Long, Long> ids = new HashMap<>();
        try (Statement query = source.createStatement(); ResultSet rows = query.executeQuery(select)) {
            while (rows.next()) {
                long sourceId = rows.getLong("id");
                String name = rows.getString("name");
                Long targetId = findCategoryId(target, name);
                if (targetId == null) {
                    try (PreparedStatement insert = target.prepareStatement(
                            "INSERT INTO category (name, active, sort_order) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        insert.setString(1, name); insert.setBoolean(2, rows.getBoolean("active")); insert.setInt(3, rows.getInt("sort_order"));
                        insert.executeUpdate();
                        try (ResultSet generated = insert.getGeneratedKeys()) { generated.next(); targetId = generated.getLong(1); }
                    }
                }
                ids.put(sourceId, targetId);
            }
        }
        return ids;
    }

    private static void copyBatches(Connection source, Connection target) throws SQLException {
        String select = "SELECT id, source, file_name, file_hash, status, total_count, duplicate_count, error_message, created_at, confirmed_at FROM import_batch ORDER BY id";
        String insert = "INSERT INTO import_batch (id, source, file_name, file_hash, status, total_count, duplicate_count, error_message, created_at, confirmed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        copy(source, target, select, insert, result -> {
            PreparedStatement statement = result.statement();
            ResultSet rs = result.rs();
            statement.setLong(1, rs.getLong("id")); statement.setString(2, rs.getString("source"));
            statement.setString(3, rs.getString("file_name")); statement.setString(4, rs.getString("file_hash"));
            statement.setString(5, rs.getString("status")); statement.setInt(6, rs.getInt("total_count"));
            statement.setInt(7, rs.getInt("duplicate_count")); statement.setString(8, rs.getString("error_message"));
            statement.setTimestamp(9, rs.getTimestamp("created_at")); statement.setTimestamp(10, rs.getTimestamp("confirmed_at"));
        });
    }

    private static void copyTransactions(Connection source, Connection target, Map<Long, Long> categoryIds) throws SQLException {
        String select = "SELECT id, import_batch_id, source, external_id, business_fingerprint, occurred_at, direction, amount, account_name, counterparty, summary, category, category_id, category_origin, raw_data, created_at FROM finance_transaction ORDER BY id";
        String insert = "INSERT INTO finance_transaction (id, import_batch_id, source, external_id, business_fingerprint, occurred_at, direction, amount, account_name, counterparty, summary, category, category_id, category_origin, raw_data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        copy(source, target, select, insert, result -> {
            PreparedStatement statement = result.statement();
            ResultSet rs = result.rs();
            statement.setLong(1, rs.getLong("id")); statement.setLong(2, rs.getLong("import_batch_id"));
            statement.setString(3, rs.getString("source")); statement.setString(4, rs.getString("external_id"));
            statement.setString(5, rs.getString("business_fingerprint")); statement.setTimestamp(6, rs.getTimestamp("occurred_at"));
            statement.setString(7, rs.getString("direction")); statement.setBigDecimal(8, rs.getBigDecimal("amount"));
            statement.setString(9, rs.getString("account_name")); statement.setString(10, rs.getString("counterparty"));
            statement.setString(11, rs.getString("summary")); statement.setString(12, rs.getString("category"));
            long sourceCategoryId = rs.getLong("category_id");
            Long targetCategoryId = rs.wasNull() ? null : categoryIds.get(sourceCategoryId);
            if (targetCategoryId == null) statement.setNull(13, Types.BIGINT); else statement.setLong(13, targetCategoryId);
            statement.setString(14, rs.getString("category_origin"));
            statement.setString(15, rs.getString("raw_data")); statement.setTimestamp(16, rs.getTimestamp("created_at"));
        });
    }

    private static void copyRules(Connection source, Connection target, Map<Long, Long> categoryIds) throws SQLException {
        String select = "SELECT id, category_id, source, keyword, priority, active FROM category_rule ORDER BY id";
        String insert = "INSERT INTO category_rule (id, category_id, source, keyword, priority, active) VALUES (?, ?, ?, ?, ?, ?)";
        copy(source, target, select, insert, result -> {
            PreparedStatement statement = result.statement(); ResultSet rs = result.rs();
            statement.setLong(1, rs.getLong("id")); statement.setLong(2, categoryIds.get(rs.getLong("category_id")));
            statement.setString(3, rs.getString("source")); statement.setString(4, rs.getString("keyword"));
            statement.setInt(5, rs.getInt("priority")); statement.setBoolean(6, rs.getBoolean("active"));
        });
    }

    private static void ensureTargetIsEmpty(Connection target) throws SQLException {
        try (Statement statement = target.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT (SELECT COUNT(*) FROM import_batch), (SELECT COUNT(*) FROM finance_transaction)")) {
            result.next();
            if (result.getLong(1) > 0 || result.getLong(2) > 0)
                throw new IllegalStateException("目标 H2 已存在导入批次或交易，迁移已停止，不覆盖已有数据");
        }
        try (Statement statement = target.createStatement()) {
            statement.executeUpdate("DELETE FROM category_rule");
        }
    }

    private static void copy(Connection source, Connection target, String select, String insert, Binder binder) throws SQLException {
        try (Statement query = source.createStatement(); ResultSet rows = query.executeQuery(select);
             PreparedStatement statement = target.prepareStatement(insert)) {
            while (rows.next()) {
                binder.bind(new Row(rows, statement));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Long findCategoryId(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM category WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : null; }
        }
    }

    private static void restartIdentity(Connection connection, String table) throws SQLException {
        long nextId;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table)) {
            result.next(); nextId = result.getLong(1);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + nextId);
        }
    }

    private static void printSummary(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount FROM finance_transaction")) {
            result.next();
            System.out.printf("迁移完成：交易 %d 笔，交易金额合计 %s%n", result.getLong("count"), result.getBigDecimal("amount").toPlainString());
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new java.util.HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) throw new IllegalArgumentException("参数格式必须是 --名称=值：" + arg);
            int separator = arg.indexOf('='); result.put(arg.substring(2, separator), arg.substring(separator + 1));
        }
        return result;
    }

    private static Properties loadLegacyProperties() throws Exception {
        Properties properties = new Properties();
        Path file = Path.of("src/main/resources/application-local.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        }
        return properties;
    }

    private record Row(ResultSet rs, PreparedStatement statement) { }
    @FunctionalInterface private interface Binder { void bind(Row row) throws SQLException; }
}
