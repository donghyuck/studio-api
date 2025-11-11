package studio.one.application.avatar.persistence.jdbc;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImageData;
import studio.one.application.avatar.persistence.AvatarImageDataRepository;

@Repository
public class AvatarImageDataJdbcRepository implements AvatarImageDataRepository {

    private static final String TABLE = "TB_APPLICATION_AVATAR_IMAGE_DATA";

    private final NamedParameterJdbcTemplate template;

    public AvatarImageDataJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public AvatarImageData save(AvatarImageData data) {
        if (data == null || data.getId() == null) {
            throw new IllegalArgumentException("AvatarImageData id must not be null");
        }
        Map<String, Object> params = Map.of(
                "id", data.getId(),
                "payload", data.getData());
        int updated = template.update(
                "update %s set AVATAR_IMAGE_DATA = :payload where AVATAR_IMAGE_ID = :id".formatted(TABLE),
                params);
        if (updated == 0) {
            template.update(
                    "insert into %s (AVATAR_IMAGE_ID, AVATAR_IMAGE_DATA) values (:id, :payload)".formatted(TABLE),
                    params);
        }
        return data;
    }
}
