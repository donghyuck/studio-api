package studio.one.platform.data.sqlquery.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks an interface as a SQL mapper to be proxied. Statement ids will resolve
 * from {@link SqlStatement} on each method, optionally prefixed by the provided
 * namespace.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface SqlMapper {

    /**
     * Optional namespace prefix for statement ids.
     */
    String namespace() default "";
}
