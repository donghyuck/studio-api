package studio.one.platform.objecttype;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.objecttype.db.jdbc.ObjectTypeJdbcRepository;
import studio.one.platform.objecttype.db.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.model.ObjectTypeRow;

public class ObjectTypeJdbcRepositoryTest {

    @Test
    void upsertUsesOnConflict() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        ObjectTypeJdbcRepository repo = new ObjectTypeJdbcRepository(template);

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
                anyString(),
                anyMap(),
                any(org.springframework.jdbc.core.RowMapper.class));

        repo.upsert(row);

        verify(template).update(contains("ON CONFLICT"), anyMap());
    }

    @Test
    void upsertPolicyUsesOnConflict() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        ObjectTypeJdbcRepository repo = new ObjectTypeJdbcRepository(template);
        ObjectTypePolicyRow row = new ObjectTypePolicyRow();
        row.setObjectType(1);
        row.setMaxFileMb(50);
        row.setCreatedBy("system");
        row.setCreatedById(1);
        row.setUpdatedBy("system");
        row.setUpdatedById(1);

        doReturn(List.of(row)).when(template).query(
                contains("tb_application_object_type_policy"),
                anyMap(),
                any(org.springframework.jdbc.core.RowMapper.class));

        repo.upsertPolicy(row);

        verify(template).update(contains("ON CONFLICT"), anyMap());
    }

    @Test
    void searchAppliesFiltersAndPaging() {
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        ObjectTypeJdbcRepository repo = new ObjectTypeJdbcRepository(template);

        when(template.queryForObject(anyString(), anyMap(), eq(Long.class))).thenReturn(1L);
        doReturn(List.of(new ObjectTypeRow())).when(template).query(
                anyString(),
                anyMap(),
                any(org.springframework.jdbc.core.RowMapper.class));

        repo.search("system", "active", "Attach", PageRequest.of(2, 5));

        verify(template).queryForObject(contains("lower(code) like :q"), anyMap(), eq(Long.class));
        verify(template).query(contains("limit :limit offset :offset"), anyMap(),
                any(org.springframework.jdbc.core.RowMapper.class));
    }
}
