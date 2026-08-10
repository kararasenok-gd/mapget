package space.kararasenok.mapget.utils;

import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.DatabaseColumn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Database {
    private final Mapget plugin;
    private final Connection conn;

    public Database(Mapget plugin, Connection conn) {
        this.plugin = plugin;
        this.conn = conn;
    }

    public void addTable(String name, List<DatabaseColumn> columns) {
        try (Statement statement = conn.createStatement()) {
            statement.execute(createTableSql(name, columns));
        } catch (SQLException e) {
            plugin.logger.error("Failed to add table {} to database: {}", name, e.getMessage());
        }
    }

    public boolean tableExists(String name) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.logger.error("Failed to check if table {} exists in database: {}", name, e.getMessage());
            return false;
        }
    }

    public boolean requiresSynchronization(Map<String, ? extends List<DatabaseColumn>> freshSchema) throws SQLException {
        Objects.requireNonNull(freshSchema, "freshSchema");

        if (!getUserTableNames().equals(freshSchema.keySet())) {
            return true;
        }

        for (Map.Entry<String, ? extends List<DatabaseColumn>> entry : freshSchema.entrySet()) {
            Map<String, DatabaseColumn> actualColumns = getActualColumns(entry.getKey());
            Set<String> freshColumnNames = entry.getValue().stream()
                    .map(DatabaseColumn::name)
                    .collect(Collectors.toSet());
            if (!actualColumns.keySet().equals(freshColumnNames)) {
                return true;
            }

            boolean hasDifferentTypes = entry.getValue().stream().anyMatch(freshColumn -> {
                DatabaseColumn actualColumn = actualColumns.get(freshColumn.name());
                return !normalizeType(actualColumn.type()).equals(normalizeType(freshColumn.type()));
            });
            if (hasDifferentTypes) {
                return true;
            }
        }

        return false;
    }

    public boolean synchronizeSchema(Map<String, ? extends List<DatabaseColumn>> freshSchema) {
        Objects.requireNonNull(freshSchema, "freshSchema");

        for (Map.Entry<String, ? extends List<DatabaseColumn>> entry : freshSchema.entrySet()) {
            if (!validateTableSchema(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        try {
            for (String actualTable : getUserTableNames()) {
                if (!freshSchema.containsKey(actualTable)) {
                    try (Statement dropStatement = conn.createStatement()) {
                        dropStatement.execute("DROP TABLE " + quoteIdentifier(actualTable));
                        plugin.logger.info("Removed table {} because it is absent from the current schema", actualTable);
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.logger.error("Failed to remove tables absent from the current schema: {}", e.getMessage());
            return false;
        }
    }

    private Set<String> getUserTableNames() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'" );
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tables.add(result.getString("name"));
            }
        }
        return tables;
    }

    public boolean validateTableSchema(String table, List<DatabaseColumn> columns) {
        if (!tableExists(table)) {
            addTable(table, columns);
            return tableExists(table);
        }

        try {
            Map<String, DatabaseColumn> actualColumns = getActualColumns(table);
            Set<String> freshColumnNames = columns.stream()
                    .map(DatabaseColumn::name)
                    .collect(Collectors.toSet());
            boolean hasDifferentColumns = !actualColumns.keySet().equals(freshColumnNames);
            boolean hasDifferentTypes = columns.stream().anyMatch(freshColumn -> {
                DatabaseColumn actualColumn = actualColumns.get(freshColumn.name());
                return actualColumn != null && !normalizeType(actualColumn.type()).equals(normalizeType(freshColumn.type()));
            });

            if (hasDifferentColumns || hasDifferentTypes) {
                rebuildTable(table, columns, actualColumns);
                plugin.logger.info("Updated schema of table {}", table);
            }
            return true;
        } catch (SQLException e) {
            plugin.logger.error("Failed to validate table {} schema in database: {}", table, e.getMessage());
            return false;
        }
    }

    private Map<String, DatabaseColumn> getActualColumns(String table) throws SQLException {
        Map<String, DatabaseColumn> actualColumns = new HashMap<>();
        try (Statement statement = conn.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + quoteIdentifier(table) + ")")) {
            while (result.next()) {
                String name = result.getString("name");
                actualColumns.put(name, new DatabaseColumn(name, result.getString("type")));
            }
        }
        return actualColumns;
    }

    private void rebuildTable(String table, List<DatabaseColumn> columns,
                              Map<String, DatabaseColumn> actualColumns) throws SQLException {
        List<DatabaseColumn> columnsToCopy = columns.stream()
                .filter(column -> actualColumns.containsKey(column.name()))
                .toList();
        if (columnsToCopy.isEmpty()) {
            throw new SQLException("Cannot migrate table without common columns");
        }

        String temporaryTable = table + "__mapget_new_" + System.nanoTime();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement statement = conn.createStatement()) {
            // SQLite has no ALTER COLUMN, so the replacement table is built atomically.
            statement.execute(createTableSql(temporaryTable, columns));

            String copiedColumns = columnsToCopy.stream()
                    .map(column -> quoteIdentifier(column.name()))
                    .collect(Collectors.joining(", "));
            String values = columnsToCopy.stream()
                    .map(column -> copyValueSql(column, actualColumns.get(column.name())))
                    .collect(Collectors.joining(", "));
            statement.execute("INSERT INTO " + quoteIdentifier(temporaryTable) + " (" + copiedColumns + ") "
                    + "SELECT " + values + " FROM " + quoteIdentifier(table));
            statement.execute("DROP TABLE " + quoteIdentifier(table));
            statement.execute("ALTER TABLE " + quoteIdentifier(temporaryTable) + " RENAME TO " + quoteIdentifier(table));
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private String createTableSql(String name, List<DatabaseColumn> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Table " + name + " must contain at least one column");
        }
        return "CREATE TABLE " + quoteIdentifier(name) + " (" + columns.stream()
                .map(DatabaseColumn::definition)
                .collect(Collectors.joining(", ")) + ")";
    }

    private String copyValueSql(DatabaseColumn freshColumn, DatabaseColumn actualColumn) {
        String name = quoteIdentifier(freshColumn.name());
        if (normalizeType(freshColumn.type()).equals(normalizeType(actualColumn.type()))) {
            return name;
        }
        return "CAST(" + name + " AS " + freshColumn.type() + ")";
    }

    private static String normalizeType(String type) {
        return type.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQLite identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
