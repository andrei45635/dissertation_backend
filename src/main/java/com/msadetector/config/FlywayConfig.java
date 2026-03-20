package com.msadetector.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * Runs Flyway migrations at the earliest possible moment during
 * Spring context initialisation — before Hibernate attempts to
 * validate the schema.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    private final DataSource dataSource;

    public FlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        log.info("Running Flyway migrations (classpath:db/migration) ...");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var result = flyway.migrate();
        log.info("Flyway: {} migration(s) applied — schema at version {}",
                result.migrationsExecuted, result.targetSchemaVersion);
    }
}




