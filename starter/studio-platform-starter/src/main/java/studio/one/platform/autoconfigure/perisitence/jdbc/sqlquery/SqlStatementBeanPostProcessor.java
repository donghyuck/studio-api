package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.data.sqlquery.SqlQuery;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.data.sqlquery.mapping.BoundSql;

/**
 * Injects SQL text or {@link BoundSql} into fields/setters annotated with
 * {@link SqlStatement}. Keeps existing beans untouched if the factory is not
 * present.
 */
@Slf4j
public class SqlStatementBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, Ordered {

    private SqlQueryFactory sqlQueryFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        try {
            this.sqlQueryFactory = beanFactory.getBean(SqlQueryFactory.class);
        } catch (Exception ex) {
            log.debug("SqlQueryFactory not available; SqlStatement injection disabled.");
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (sqlQueryFactory == null) {
            return bean;
        }
        injectFields(bean);
        injectMethods(bean);
        return bean;
    }

    private void injectFields(Object bean) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            SqlStatement annotation = AnnotatedElementUtils.findMergedAnnotation(field, SqlStatement.class);
            if (annotation == null || !StringUtils.hasText(annotation.value())) {
                return;
            }
            BoundSql boundSql = resolveBoundSql(annotation.value());
            Object valueToInject = adapt(boundSql, field.getType());
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, valueToInject);
            if (log.isDebugEnabled()) {
                log.debug("Injected SQL statement '{}' into {}.{}", annotation.value(),
                        bean.getClass().getSimpleName(), field.getName());
            }
        });
    }

    private void injectMethods(Object bean) {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            SqlStatement annotation = AnnotatedElementUtils.findMergedAnnotation(method, SqlStatement.class);
            if (annotation == null || method.getParameterCount() != 1 || !StringUtils.hasText(annotation.value())) {
                return;
            }
            BoundSql boundSql = resolveBoundSql(annotation.value());
            Class<?> parameterType = method.getParameterTypes()[0];
            Object valueToInject = adapt(boundSql, parameterType);
            ReflectionUtils.makeAccessible(method);
            ReflectionUtils.invokeMethod(method, bean, valueToInject);
            if (log.isDebugEnabled()) {
                log.debug("Injected SQL statement '{}' via {}.{}()", annotation.value(),
                        bean.getClass().getSimpleName(), method.getName());
            }
        }, method -> AnnotatedElementUtils.hasAnnotation(method, SqlStatement.class));
    }

    private BoundSql resolveBoundSql(String statementId) {
        SqlQuery sqlQuery = sqlQueryFactory.createSqlQuery();
        
        return sqlQuery.getBoundSql(statementId);
    }

    private Object adapt(BoundSql boundSql, Class<?> targetType) {
        if (BoundSql.class.isAssignableFrom(targetType)) {
            return boundSql;
        }
        if (String.class.equals(targetType)) {
            return boundSql.getSql();
        }
        throw new IllegalStateException("Unsupported @SqlStatement target type: " + targetType.getName());
    }
}
