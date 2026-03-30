package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jdbc.sqlquery.SqlMapperFactoryBean} instead.
 */
@Deprecated(forRemoval = false)
public class SqlMapperFactoryBean<T>
        extends studio.one.platform.autoconfigure.persistence.jdbc.sqlquery.SqlMapperFactoryBean<T> {

    public SqlMapperFactoryBean(Class<T> mapperInterface) {
        super(mapperInterface);
    }
}
