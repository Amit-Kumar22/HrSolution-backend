package com.hrsolution.support;

import com.hrsolution.HrSolutionApplication;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application against a throwaway MySQL container instead of a real
 * database - handy for poking at endpoints in Swagger UI without creating a
 * schema or setting {@code DB_PASSWORD} first.
 *
 * <p>Run this class's {@code main} from the IDE (not {@code HrSolutionApplication}).
 * Flyway builds the schema inside the container on startup, and everything is
 * discarded when the process stops.
 *
 * <p>Requires Docker to be running.
 */
public class TestHrSolutionApplication {

    public static void main(String[] args) {
        SpringApplication
                .from(HrSolutionApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
