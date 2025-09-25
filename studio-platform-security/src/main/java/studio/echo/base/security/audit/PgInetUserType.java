/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file PgInetUserType.java
 *      @date 2025
 *
 */


package studio.echo.base.security.audit;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.*;
import java.util.Objects;
/**
 * PostgreSQL의 inet 타입을 매핑하기 위한 UserType 구현체입니다.
 * @author  donghyuck, son
 * @since 2025-09-24
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-24  donghyuck, son: 최초 생성.
 * </pre>
 */


public class PgInetUserType implements UserType {

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.OTHER };
    }

    @Override
    public Class<?> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(Object x, Object y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Object x) {
        return Objects.hashCode(x);
    }

    @Override
    public Object deepCopy(Object value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) {
        return original;
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        Object obj = rs.getObject(names[0]);
        return obj != null ? obj.toString() : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null || value.toString().isBlank()) {
            st.setNull(index, Types.OTHER); // ★ NULL 바인딩 타입을 명확히
        } else {
            PGobject o = new PGobject();
            o.setType("inet");
            o.setValue(value.toString());
            st.setObject(index, o);
        }
    }
}
