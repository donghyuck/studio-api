package studio.one.application.avatar.persistence.jdbc;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.domain.entity.AvatarImageData;
import studio.one.application.avatar.persistence.AvatarImageRepository;

@Repository
public class AvatarImageJdbcRepository implements AvatarImageRepository {

    private static final String TABLE = "TB_APPLICATION_AVATAR_IMAGE";
    private static final String TABLE_DATA = "TB_APPLICATION_AVATAR_IMAGE_DATA";

    private final NamedParameterJdbcTemplate template;
    private final SimpleJdbcInsert insert;

    public AvatarImageJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
        this.insert = new SimpleJdbcInsert(template.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("AVATAR_IMAGE_ID");
    }

    private static final RowMapper<AvatarImage> ROW_MAPPER = (rs, rowNum) -> AvatarImage.builder()
            .id(rs.getLong("AVATAR_IMAGE_ID"))
            .userId(rs.getLong("USER_ID"))
            .primaryImage(rs.getBoolean("PRIMARY_IMAGE"))
            .fileName(rs.getString("FILE_NAME"))
            .fileSize(rs.getLong("FILE_SIZE"))
            .contentType(rs.getString("CONTENT_TYPE"))
            .creationDate(toOffsetDateTime(rs.getTimestamp("CREATION_DATE")))
            .modifiedDate(toOffsetDateTime(rs.getTimestamp("MODIFIED_DATE")))
            .build();

    @Override
    public List<AvatarImage> findByUserIdOrderByCreationDateDesc(Long userId) {
        String sql = """
                select AVATAR_IMAGE_ID, USER_ID, PRIMARY_IMAGE, FILE_NAME, FILE_SIZE, CONTENT_TYPE,
                       CREATION_DATE, MODIFIED_DATE
                  from %s
                 where USER_ID = :userId
                 order by CREATION_DATE desc
                """.formatted(TABLE);
        return template.query(sql, Map.of("userId", userId), ROW_MAPPER);
    }

    @Override
    public Optional<AvatarImage> findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc(Long userId) {
        String sql = """
                select AVATAR_IMAGE_ID, USER_ID, PRIMARY_IMAGE, FILE_NAME, FILE_SIZE, CONTENT_TYPE,
                       CREATION_DATE, MODIFIED_DATE
                  from %s
                 where USER_ID = :userId and PRIMARY_IMAGE = true
                 order by CREATION_DATE desc
                 limit 1
                """.formatted(TABLE);
        return querySingle(sql, Map.of("userId", userId));
    }

    @Override
    public long countByUserId(Long userId) {
        return template.queryForObject(
                "select count(*) from %s where USER_ID = :userId".formatted(TABLE),
                Map.of("userId", userId),
                Long.class);
    }

    @Override
    public Optional<AvatarImage> findById(Long id) {
        String sql = """
                select i.AVATAR_IMAGE_ID, i.USER_ID, i.PRIMARY_IMAGE, i.FILE_NAME, i.FILE_SIZE,
                       i.CONTENT_TYPE, i.CREATION_DATE, i.MODIFIED_DATE,
                       d.AVATAR_IMAGE_DATA
                  from %s i
                  left join %s d on d.AVATAR_IMAGE_ID = i.AVATAR_IMAGE_ID
                 where i.AVATAR_IMAGE_ID = :id
                """.formatted(TABLE, TABLE_DATA);
        return template.query(sql, Map.of("id", id), (rs, rowNum) -> mapWithData(rs)).stream().findFirst();
    }

    @Override
    public <S extends AvatarImage> S save(S avatarImage) {
        if (avatarImage.getId() == null) {
            return (S) insert(avatarImage);
        }
        return (S) update(avatarImage);
    }

    @Override
    public <S extends AvatarImage> List<S> saveAll(Iterable<S> avatars) {
        List<S> result = new ArrayList<>();
        for (S avatar : avatars) {
            result.add(save(avatar));
        }
        return result;
    }

    @Override
    public void delete(AvatarImage avatarImage) {
        if (avatarImage == null || avatarImage.getId() == null) {
            return;
        }
        Map<String, Object> params = Map.of("id", avatarImage.getId());
        template.update("delete from %s where AVATAR_IMAGE_ID = :id".formatted(TABLE_DATA), params);
        template.update("delete from %s where AVATAR_IMAGE_ID = :id".formatted(TABLE), params);
    }

    private AvatarImage insert(AvatarImage avatar) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (avatar.getCreationDate() == null) {
            avatar.setCreationDate(now);
        }
        if (avatar.getModifiedDate() == null) {
            avatar.setModifiedDate(now);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("USER_ID", avatar.getUserId())
                .addValue("PRIMARY_IMAGE", avatar.isPrimaryImage())
                .addValue("FILE_NAME", avatar.getFileName())
                .addValue("FILE_SIZE", avatar.getFileSize())
                .addValue("CONTENT_TYPE", avatar.getContentType())
                .addValue("CREATION_DATE", Timestamp.from(avatar.getCreationDate().toInstant()))
                .addValue("MODIFIED_DATE", Timestamp.from(avatar.getModifiedDate().toInstant()));
        Number key = insert.executeAndReturnKey(params);
        avatar.setId(key.longValue());
        return avatar;
    }

    private AvatarImage update(AvatarImage avatar) {
        OffsetDateTime modified = avatar.getModifiedDate();
        if (modified == null) {
            modified = OffsetDateTime.now(ZoneOffset.UTC);
            avatar.setModifiedDate(modified);
        }
        String sql = """
                update %s
                   set USER_ID = :userId,
                       PRIMARY_IMAGE = :primaryImage,
                       FILE_NAME = :fileName,
                       FILE_SIZE = :fileSize,
                       CONTENT_TYPE = :contentType,
                       MODIFIED_DATE = :modifiedDate
                 where AVATAR_IMAGE_ID = :id
                """.formatted(TABLE);
        Map<String, Object> params = new HashMap<>();
        params.put("userId", avatar.getUserId());
        params.put("primaryImage", avatar.isPrimaryImage());
        params.put("fileName", avatar.getFileName());
        params.put("fileSize", avatar.getFileSize());
        params.put("contentType", avatar.getContentType());
        params.put("modifiedDate", Timestamp.from(modified.toInstant()));
        params.put("id", avatar.getId());
        template.update(sql, params);
        return avatar;
    }

    private Optional<AvatarImage> querySingle(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(template.queryForObject(sql, params, ROW_MAPPER));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private AvatarImage mapWithData(java.sql.ResultSet rs) throws java.sql.SQLException {
        AvatarImage image = ROW_MAPPER.mapRow(rs, 0);
        byte[] bytes = rs.getBytes("AVATAR_IMAGE_DATA");
        if (bytes != null) {
            AvatarImageData data = AvatarImageData.builder()
                    .id(image.getId())
                    .data(bytes)
                    .avatarImage(image)
                    .build();
            image.setData(data);
        }
        return image;
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
