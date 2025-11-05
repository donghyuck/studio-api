package studio.one.platform.storage.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.platform.storage.domain.model.json.ObjectStorageTypeDeserializer;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObjectInfo implements Serializable {

    @JsonDeserialize(using = ObjectStorageTypeDeserializer.class)
    private ObjectStorageType objectStorageType;

    private String namespace;

    private String bucket;

    private String key;

    private String name;

    private Long size;

    private String contentType;
    
    private String eTag;

    private Map<String, String> metadata;
 
    private Date createdDate;
 
    private Date modifiedDate;

    public boolean isFolder() {
        return key.endsWith("/") && size == 0;
    }
}
