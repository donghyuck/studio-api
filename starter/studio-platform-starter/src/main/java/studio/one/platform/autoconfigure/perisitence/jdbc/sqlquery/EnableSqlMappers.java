package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Enables scanning for {@link studio.one.platform.data.sqlquery.annotation.SqlMapper}
 * interfaces and registers proxies backed by {@link studio.one.platform.data.sqlquery.factory.SqlQueryFactory}.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Import(SqlMapperRegistrar.class)
public @interface EnableSqlMappers {

    /**
     * Base packages to scan. If empty, falls back to Spring Boot
     * auto-configuration packages.
     */
    String[] basePackages() default {};
}
