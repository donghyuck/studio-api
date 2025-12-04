package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import javax.sql.DataSource;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import studio.one.platform.data.sqlquery.annotation.SqlMapper;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;

/**
 * FactoryBean that produces mapper proxies backed by {@link SqlQueryFactory}.
 */
public class SqlMapperFactoryBean<T> implements FactoryBean<T>, InitializingBean {

    private final Class<T> mapperInterface;

    @Autowired(required = false)
    private SqlQueryFactory sqlQueryFactory;

    @Autowired(required = false)
    private DataSource dataSource;

    private SqlMapperProxyFactory<T> proxyFactory;

    public SqlMapperFactoryBean(Class<T> mapperInterface) {
        Assert.notNull(mapperInterface, "Mapper interface must not be null");
        Assert.isTrue(mapperInterface.isInterface(), "@SqlMapper can only be placed on interfaces");
        Assert.notNull(mapperInterface.getAnnotation(SqlMapper.class),
                "Mapper interface must be annotated with @SqlMapper");
        this.mapperInterface = mapperInterface;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(sqlQueryFactory, "SqlQueryFactory bean is required for mapper proxies");
        Assert.notNull(dataSource, "DataSource bean is required for mapper proxies");
        this.proxyFactory = new SqlMapperProxyFactory<>(mapperInterface, sqlQueryFactory, dataSource);
    }

    @Override
    public T getObject() {
        return proxyFactory.newInstance();
    }

    @Override
    public Class<?> getObjectType() {
        return mapperInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
