package studio.one.platform.storage.web.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.storage.service.ObjectInfo;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ObjectInfoDto {
    private String bucket;
    private String key;
    private String name;
    private Long size;
    private String eTag;
    private String contentType;
    private Instant createdDate;
    private Instant modifiedDate;
    private boolean folder;
    private Map<String, String> metadata;

    public static ObjectInfoDto from(ObjectInfo src) {
        return ObjectInfoDto.builder()
                .bucket(src.getBucket())
                .key(src.getKey())
                .name(src.getName())
                .size(src.getSize())
                .eTag(src.getETag())
                .contentType(src.getContentType())
                .metadata(src.getMetadata())
                .createdDate(src.getCreatedDate())
                .modifiedDate(src.getModifiedDate())
                .folder(src.isFolder())
                .build();
    }
}
