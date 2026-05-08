package studio.one.platform.objecttype.db.mybatis;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import studio.one.platform.objecttype.db.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.model.ObjectTypeRow;

public interface ObjectTypeMapper {

    ObjectTypeRow selectByType(@Param("objectType") int objectType);

    ObjectTypeRow selectByCode(@Param("code") String code);

    ObjectTypePolicyRow selectPolicyByType(@Param("objectType") int objectType);

    List<ObjectTypeRow> search(
            @Param("domain") String domain,
            @Param("status") String status,
            @Param("q") String q,
            @Param("limit") Integer limit,
            @Param("offset") Long offset);

    long count(
            @Param("domain") String domain,
            @Param("status") String status,
            @Param("q") String q);

    int upsertType(ObjectTypeRow row);

    int patchType(
            @Param("objectType") int objectType,
            @Param("patch") ObjectTypeRow patch,
            @Param("updatedAt") Timestamp updatedAt);

    int upsertPolicy(ObjectTypePolicyRow row);

    int delete(@Param("objectType") int objectType);
}
