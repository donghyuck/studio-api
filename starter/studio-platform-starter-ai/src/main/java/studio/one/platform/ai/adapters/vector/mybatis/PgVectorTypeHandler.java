package studio.one.platform.ai.adapters.vector.mybatis;

import com.pgvector.PGvector;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class PgVectorTypeHandler extends BaseTypeHandler<PGvector> {

    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int i,
            PGvector parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public PGvector getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toPgVector(rs.getObject(columnName));
    }

    @Override
    public PGvector getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toPgVector(rs.getObject(columnIndex));
    }

    @Override
    public PGvector getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toPgVector(cs.getObject(columnIndex));
    }

    private static PGvector toPgVector(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof PGvector vector) {
            return vector;
        }
        return new PGvector(value.toString());
    }
}
