package studio.one.platform.objecttype.db;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.objecttype.db.jdbc.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypeRow;

public interface ObjectTypeStore {

    Optional<ObjectTypeRow> findByType(int objectType);

    Optional<ObjectTypeRow> findByCode(String code);

    Page<ObjectTypeRow> search(String domain, String status, String q, Pageable pageable);

    ObjectTypeRow upsert(ObjectTypeRow row);

    ObjectTypeRow patch(int objectType, ObjectTypeRow patch);

    Optional<ObjectTypePolicyRow> findPolicy(int objectType);

    ObjectTypePolicyRow upsertPolicy(ObjectTypePolicyRow row);
}
