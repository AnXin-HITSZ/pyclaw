package com.anxin.pyclaw.backend.approval;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ToolApprovalSchemaMigrator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(ToolApprovalSchemaMigrator.class);
    private static final String TABLE = "tool_approval_requests";
    private static final String DECISION_COLUMN = "decision";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ToolApprovalSchemaMigrator(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!tableExists()) {
            return;
        }
        if (!columnExists(DECISION_COLUMN)) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + DECISION_COLUMN + " VARCHAR(32)");
            log.info("Added {}.{} for approval decision tracking", TABLE, DECISION_COLUMN);
        }
        jdbcTemplate.update("UPDATE " + TABLE + " SET " + DECISION_COLUMN + " = 'APPROVED', status = 'CONSUMED' WHERE status = 'APPROVED'");
        jdbcTemplate.update("UPDATE " + TABLE + " SET " + DECISION_COLUMN + " = 'REJECTED', status = 'CONSUMED' WHERE status = 'REJECTED'");
    }

    private boolean tableExists() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            return tableExists(metaData, catalog, schema, TABLE)
                    || tableExists(metaData, catalog, schema, TABLE.toUpperCase());
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String schema, String table) throws Exception {
        try (ResultSet tables = metaData.getTables(catalog, schema, table, null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metaData.getTables(catalog, null, table, null)) {
            return tables.next();
        }
    }

    private boolean columnExists(String column) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            return columnExists(metaData, catalog, schema, column)
                    || columnExists(metaData, catalog, schema, column.toUpperCase());
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String schema, String column) throws Exception {
        try (ResultSet columns = metaData.getColumns(catalog, schema, TABLE, column)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metaData.getColumns(catalog, null, TABLE, column)) {
            return columns.next();
        }
    }
}
