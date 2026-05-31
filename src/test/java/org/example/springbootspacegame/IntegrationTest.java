package org.example.springbootspacegame;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for integration tests. Bundles:
 *
 * <ul>
 *   <li>{@link SpringBootTest} — full application context.</li>
 *   <li>{@link Import} of {@link TestcontainersConfiguration} — a real Postgres.</li>
 *   <li>{@link Sql} that runs {@code cleanup.sql} before every test method, so
 *       integration tests don't leak DB state into each other (see issue #17).</li>
 * </ul>
 *
 * <p>Use it on any {@code *IT} class instead of repeating the three annotations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface IntegrationTest {
}
