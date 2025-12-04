package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.util.Assert;

import studio.one.platform.data.sqlquery.annotation.SqlMapper;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;

class SqlMapperProxyFactory<T> implements InvocationHandler {

    private final Class<T> mapperInterface;
    private final SqlQueryFactory sqlQueryFactory;
    private final DataSource dataSource;
    private final String namespace;
    private final Map<Method, SqlMapperMethod> methodCache = new ConcurrentHashMap<>();

    SqlMapperProxyFactory(Class<T> mapperInterface, SqlQueryFactory sqlQueryFactory, DataSource dataSource) {
        this.mapperInterface = mapperInterface;
        this.sqlQueryFactory = sqlQueryFactory;
        this.dataSource = dataSource;
        SqlMapper mapper = mapperInterface.getAnnotation(SqlMapper.class);
        this.namespace = (mapper != null) ? mapper.namespace() : "";
    }

    @SuppressWarnings("unchecked")
    T newInstance() {
        Assert.notNull(sqlQueryFactory, "SqlQueryFactory is required");
        Assert.notNull(dataSource, "DataSource is required");
        return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class<?>[] { mapperInterface }, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class.equals(method.getDeclaringClass())) {
            return handleObjectMethod(proxy, method, args);
        }
        if (method.isDefault()) {
            throw new UnsupportedOperationException("Default methods are not supported on @SqlMapper interfaces");
        }
        SqlMapperMethod mapperMethod = methodCache.computeIfAbsent(method,
                m -> new SqlMapperMethod(m, SqlMapperMethod.resolveStatementId(m, namespace)));
        return mapperMethod.execute(sqlQueryFactory, dataSource, args);
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("toString".equals(name)) {
            return mapperInterface.getName() + " proxy";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        try {
            return method.invoke(this, args);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke Object method on proxy", ex);
        }
    }
}
