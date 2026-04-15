package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.model.ObjectTypeRow;
import studio.one.platform.objecttype.error.ObjectTypeErrorCodes;
import studio.one.platform.objecttype.service.DefaultObjectTypeAdminService;
import studio.one.platform.objecttype.service.ObjectTypeEffectivePolicyView;

class DefaultObjectTypeAdminServiceTest {

    @Test
    void effectivePolicyReturnsStoredPolicyWhenPresent() {
        ObjectTypeStore store = mock(ObjectTypeStore.class);
        DefaultObjectTypeAdminService service = new DefaultObjectTypeAdminService(store);
        ObjectTypePolicyRow policy = policyRow(1001);

        when(store.findByType(1001)).thenReturn(Optional.of(typeRow(1001)));
        when(store.findPolicy(1001)).thenReturn(Optional.of(policy));

        ObjectTypeEffectivePolicyView result = service.getEffectivePolicy(1001);

        assertEquals(1001, result.objectType());
        assertEquals(12, result.maxFileMb());
        assertEquals("png", result.allowedExt());
        assertEquals("image/png", result.allowedMime());
        assertEquals("{\"x\":1}", result.policyJson());
        assertEquals(ObjectTypeEffectivePolicyView.Source.STORED, result.source());
    }

    @Test
    void effectivePolicyReturnsDefaultPolicyWhenStoredPolicyMissing() {
        ObjectTypeStore store = mock(ObjectTypeStore.class);
        DefaultObjectTypeAdminService service = new DefaultObjectTypeAdminService(store);

        when(store.findByType(1001)).thenReturn(Optional.of(typeRow(1001)));
        when(store.findPolicy(1001)).thenReturn(Optional.empty());

        ObjectTypeEffectivePolicyView result = service.getEffectivePolicy(1001);

        assertEquals(1001, result.objectType());
        assertNull(result.maxFileMb());
        assertEquals("", result.allowedExt());
        assertEquals("", result.allowedMime());
        assertEquals("{}", result.policyJson());
        assertEquals(ObjectTypeEffectivePolicyView.Source.DEFAULT, result.source());
    }

    @Test
    void effectivePolicyRejectsUnknownObjectType() {
        ObjectTypeStore store = mock(ObjectTypeStore.class);
        DefaultObjectTypeAdminService service = new DefaultObjectTypeAdminService(store);

        when(store.findByType(1001)).thenReturn(Optional.empty());

        PlatformRuntimeException ex = assertThrows(PlatformRuntimeException.class,
                () -> service.getEffectivePolicy(1001));

        assertEquals(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, ex.getType());
    }

    private ObjectTypeRow typeRow(int objectType) {
        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(objectType);
        row.setCode("attachment");
        row.setName("Attachment");
        row.setDomain("media");
        row.setStatus("active");
        row.setCreatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        return row;
    }

    private ObjectTypePolicyRow policyRow(int objectType) {
        ObjectTypePolicyRow row = new ObjectTypePolicyRow();
        row.setObjectType(objectType);
        row.setMaxFileMb(12);
        row.setAllowedExt("png");
        row.setAllowedMime("image/png");
        row.setPolicyJson("{\"x\":1}");
        row.setCreatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        return row;
    }
}
