package com.brainserve.clientonboarding.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class EmbeddedPostgresMigrationTest {

    @Test
    void cleanPostgresDatabaseMigratesWithoutDocker() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setServerConfig("unix_socket_directories", "")
                .start()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getPostgresDatabase())
                    .schemas("app")
                    .defaultSchema("app")
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        }
    }
}
