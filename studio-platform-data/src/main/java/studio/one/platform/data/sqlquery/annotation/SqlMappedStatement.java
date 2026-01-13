package studio.one.platform.data.sqlquery.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares a SQL statement id to inject as {@link studio.one.platform.data.sqlquery.mapping.MappedStatement}.
 */
@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
public @interface SqlMappedStatement {

    /**
     * Statement id defined in an xml sqlset.
     */
    String value();
}
