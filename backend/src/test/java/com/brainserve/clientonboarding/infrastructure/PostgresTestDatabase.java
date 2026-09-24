package com.brainserve.clientonboarding.infrastructure;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Shared real database for tests formerly using H2; PostgreSQL owns migration/trigger semantics. */
public abstract class PostgresTestDatabase {
    private static final EmbeddedPostgres POSTGRES = start();
    private static EmbeddedPostgres start() {
        try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").start(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:"+POSTGRES.getPort()+"/postgres?currentSchema=app");
        p.add("spring.datasource.username", () -> "postgres");
        p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
