package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.platform.objecttype.db.jdbc.ObjectTypeJdbcRepository;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypeRow;

public class ObjectTypeJdbcRepositoryTest {

    @Test
    void upsertUsesOnConflict() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        ObjectTypeJdbcRepository repo = new ObjectTypeJdbcRepository(template);
        ReflectionTestUtils.setField(repo, "upsertTypeSql", "insert into tb_application_object_type ... on conflict");
        ReflectionTestUtils.setField(repo, "selectByTypeSql", "select * from tb_application_object_type where object_type = :objectType");

        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(1);
        row.setCode("attachment");
        row.setName("Attachment");
        row.setDomain("attachment");
        row.setStatus("active");
        row.setCreatedBy("system");
        row.setCreatedById(1);
        row.setCreatedAt(Instant.now());
        row.setUpdatedBy("system");
        row.setUpdatedById(1);
        row.setUpdatedAt(Instant.now());

        doReturn(List.of(row)).when(template).query(
                eq("select * from tb_application_object_type where object_type = :objectType"),
                anyMap(),
                any(org.springframework.jdbc.core.RowMapper.class));

        repo.upsert(row);

        verify(template).update(contains("on conflict"), anyMap());
    }
}
