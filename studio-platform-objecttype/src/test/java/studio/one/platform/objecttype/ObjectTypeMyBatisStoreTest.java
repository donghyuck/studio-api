package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import studio.one.platform.objecttype.infrastructure.persistence.model.ObjectTypeRow;
import studio.one.platform.objecttype.infrastructure.persistence.mybatis.ObjectTypeMapper;
import studio.one.platform.objecttype.infrastructure.persistence.mybatis.ObjectTypeMyBatisStore;

class ObjectTypeMyBatisStoreTest {

    @Test
    void searchUsesCountAndPagedMapperQuery() {
        ObjectTypeMapper mapper = mock(ObjectTypeMapper.class);
        ObjectTypeMyBatisStore store = new ObjectTypeMyBatisStore(mapper);
        ObjectTypeRow row = row(1);

        when(mapper.count("attachment", "active", "%file%")).thenReturn(11L);
        when(mapper.search("attachment", "active", "%file%", 10, 10L)).thenReturn(List.of(row));

        Page<ObjectTypeRow> page = store.search("attachment", "active", "File", PageRequest.of(1, 10));

        assertEquals(11, page.getTotalElements());
        assertEquals(List.of(row), page.getContent());
    }

    @Test
    void searchUnpagedSkipsCount() {
        ObjectTypeMapper mapper = mock(ObjectTypeMapper.class);
        ObjectTypeMyBatisStore store = new ObjectTypeMyBatisStore(mapper);
        ObjectTypeRow row = row(1);

        when(mapper.search(null, null, null, null, null)).thenReturn(List.of(row));

        Page<ObjectTypeRow> page = store.search(null, null, null, null);

        assertEquals(1, page.getTotalElements());
        verify(mapper, never()).count(any(), any(), any());
    }

    @Test
    void patchWithoutChangesDoesNotUpdate() {
        ObjectTypeMapper mapper = mock(ObjectTypeMapper.class);
        ObjectTypeMyBatisStore store = new ObjectTypeMyBatisStore(mapper);
        ObjectTypeRow existing = row(1);
        ObjectTypeRow patch = new ObjectTypeRow();

        when(mapper.selectByType(1)).thenReturn(existing);

        ObjectTypeRow result = store.patch(1, patch);

        assertSame(existing, result);
        verify(mapper, never()).patchType(anyInt(), any(ObjectTypeRow.class), any());
    }

    private ObjectTypeRow row(int objectType) {
        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(objectType);
        row.setCode("attachment");
        row.setName("Attachment");
        row.setDomain("attachment");
        row.setStatus("active");
        return row;
    }
}
