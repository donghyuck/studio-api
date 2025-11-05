package studio.one.platform.storage.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date; 

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import studio.one.platform.storage.domain.model.json.ObjectStorageTypeDeserializer;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketInfo implements Serializable
{
    @JsonDeserialize(using = ObjectStorageTypeDeserializer.class)
    private ObjectStorageType objectStorageType;
 
    private String providerId;
    
    private String bucket;

    private String namespace;

    private String compartmentId;

    private Instant createdDate;

}