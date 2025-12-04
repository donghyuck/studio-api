package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.core.ResolvableType;
import org.springframework.util.StringUtils;

import studio.one.platform.data.sqlquery.SqlQuery;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;

class SqlMapperMethod {

    private enum CommandType {
        LIST, MAP, SINGLE, UPDATE
    }

    private final Method method;
    private final String statementId;
    private final CommandType commandType;
    private final Class<?> elementType;

    SqlMapperMethod(Method method, String statementId) {
        this.method = Objects.requireNonNull(method, "method");
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.commandType = resolveCommandType(method);
        this.elementType = resolveElementType(method);
    }

    Object execute(SqlQueryFactory factory, DataSource dataSource, Object[] args) {
        SqlQuery sqlQuery = factory.createSqlQuery(dataSource);
        Object[] safeArgs = (args == null) ? new Object[0] : args;
        switch (commandType) {
            case LIST:
                if (elementType == null || Map.class.isAssignableFrom(elementType)) {
                    return sqlQuery.queryForList(statementId, safeArgs);
                }
                return sqlQuery.queryForList(statementId, elementType, safeArgs);
            case MAP:
                return sqlQuery.queryForObject(statementId, safeArgs);
            case SINGLE:
                return sqlQuery.queryForObject(statementId, method.getReturnType(), safeArgs);
            case UPDATE:
                return sqlQuery.executeUpdate(statementId, safeArgs);
            default:
                throw new IllegalStateException("Unsupported command type for " + method.toGenericString());
        }
    }

    private CommandType resolveCommandType(Method method) {
        Class<?> returnType = method.getReturnType();
        if (List.class.isAssignableFrom(returnType)) {
            return CommandType.LIST;
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return CommandType.MAP;
        }
        if (Integer.TYPE.equals(returnType) || Integer.class.equals(returnType)) {
            return CommandType.UPDATE;
        }
        if (Void.TYPE.equals(returnType)) {
            throw new IllegalStateException("@SqlMapper methods must not return void: " + method.toGenericString());
        }
        return CommandType.SINGLE;
    }

    private Class<?> resolveElementType(Method method) {
        ResolvableType resolvableType = ResolvableType.forMethodReturnType(method);
        if (List.class.isAssignableFrom(method.getReturnType())) {
            ResolvableType generic = resolvableType.asCollection().getGeneric(0);
            return generic.resolve();
        }
        return method.getReturnType();
    }

    static String resolveStatementId(Method method, String namespace) {
        SqlStatement statement = method.getAnnotation(SqlStatement.class);
        String id = (statement != null && StringUtils.hasText(statement.value()))
                ? statement.value()
                : method.getName();
        if (StringUtils.hasText(namespace)) {
            return namespace + "." + id;
        }
        return id;
    }

}
