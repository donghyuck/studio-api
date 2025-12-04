package studio.one.platform.data.sqlquery.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares a SQL statement id to inject or execute.
 * <p>
 * Supported on fields and single-argument setter methods for injection, and on
 * mapper interface methods for proxy-based execution.
 */
@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
public @interface SqlStatement {

    /**
     * Statement id defined in an xml sqlset.
     */
    String value();
}
