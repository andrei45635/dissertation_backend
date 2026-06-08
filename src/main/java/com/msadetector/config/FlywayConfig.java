package com.msadetector.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.Locale;

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
    private final boolean repairOnMigrate;

    public FlywayConfig(
            DataSource dataSource,
            @Value("${FLYWAY_REPAIR_ON_MIGRATE:${app.flyway.repair-on-migrate:false}}") boolean repairOnMigrate
    ) {
        this.dataSource = dataSource;
        this.repairOnMigrate = repairOnMigrate;
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

        boolean shouldRepair = shouldRepairOnMigrate();
        log.info("Flyway repair-on-migrate is {}", shouldRepair);

        if (shouldRepair) {
            log.warn("Running Flyway repair before migration because repair-on-migrate is enabled");
            flyway.repair();
        }

        var result = flyway.migrate();
        log.info("Flyway: {} migration(s) applied — schema at version {}",
                result.migrationsExecuted, result.targetSchemaVersion);
    }

    private boolean shouldRepairOnMigrate() {
        return repairOnMigrate
                || isTruthy(System.getenv("FLYWAY_REPAIR_ON_MIGRATE"))
                || isTruthy(System.getProperty("FLYWAY_REPAIR_ON_MIGRATE"))
                || isTruthy(System.getProperty("app.flyway.repair-on-migrate"));
    }

    private boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            default -> false;
        };
    }
}


