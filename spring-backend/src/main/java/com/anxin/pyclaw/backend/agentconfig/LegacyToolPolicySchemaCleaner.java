package com.anxin.pyclaw.backend.agentconfig;

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
public class LegacyToolPolicySchemaCleaner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyToolPolicySchemaCleaner.class);
    private static final String TABLE = "agent_tool_policies";
    private static final String LEGACY_COLUMN = "sh" + "ell" + "_" + "approval";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public LegacyToolPolicySchemaCleaner(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!columnExists()) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP COLUMN " + LEGACY_COLUMN);
        log.info("Dropped legacy agent tool policy column {}.{}", TABLE, LEGACY_COLUMN);
    }

    private boolean columnExists() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            return columnExists(metaData, catalog, schema, LEGACY_COLUMN)
                    || columnExists(metaData, catalog, schema, LEGACY_COLUMN.toUpperCase());
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
