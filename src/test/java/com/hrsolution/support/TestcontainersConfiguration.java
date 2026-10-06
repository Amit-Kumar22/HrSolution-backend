package com.hrsolution.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real MySQL in Docker for integration tests.
 *
 * <p>{@code @ServiceConnection} registers the container's JDBC details as a
 * {@code ConnectionDetails} bean, which takes precedence over
 * {@code spring.datasource.*} - so the profile properties pointing at a local
 * MySQL are ignored here and no test can accidentally write to a development
 * database.
 *
 * <p>Running against real MySQL rather than H2 is the whole point: it is what
 * makes {@code ddl-auto=validate} a genuine check that each Flyway migration
 * matches its entity, and it exercises MySQL's own collation, {@code DATETIME(6)}
 * precision and {@code CHECK} constraint behaviour.
 *
 * <p><strong>Docker must be running</strong> for any test using this class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Pinned rather than {@code mysql:latest}. The production target is MySQL 8,
     * and an unpinned tag means the version under test changes without warning
     * the first time a CI runner pulls a fresh image.
     */
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(MYSQL_IMAGE)
                .withDatabaseName("hrsolution_test")
                .withCommand("--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci")
                // Reused across every test class in a run, so MySQL starts once
                // rather than once per class.
                .withReuse(false);
    }
}
