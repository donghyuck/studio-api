package studio.one.platform.ai.adapters.vector.mybatis;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface PgVectorMapper {

    int upsertChunk(PgVectorChunkParameter parameter);

    List<PgVectorSearchRow> search(PgVectorSearchParameter parameter);

    int deleteByObject(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId);

    List<PgVectorSearchRow> searchByObject(PgVectorSearchParameter parameter);

    List<PgVectorSearchRow> hybridSearch(PgVectorHybridSearchParameter parameter);

    List<PgVectorSearchRow> hybridSearchByObject(PgVectorHybridSearchParameter parameter);

    int exists(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId);

    List<PgVectorSearchRow> listByObject(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("limit") int limit);

    List<PgVectorSearchRow> listByObjectPage(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<PgVectorSearchRow> listByObjectPageFiltered(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("documentId") String documentId,
            @Param("query") String query,
            @Param("offset") int offset,
            @Param("limit") int limit);

    String metadataByObject(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId);
}
