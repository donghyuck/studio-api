package studio.one.platform.storage.application.result;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderInfo {
    private String name;
    private String type;
    private boolean enabled;
    private String status;
    private Health health;
    private String region;
    private String endpointMasked;
    private String ociNamespace;
    private String ociCompartmentMasked;
    private String fsRootMasked;
    private Boolean s3PathStyle;
    private Boolean s3PresignerEnabled;
    private List<Capability> capabilities;
    private Map<String, String> labels;

    public enum Health {
        ok, fail, unknown
    }

    public enum Capability {
        OBJECT_GET, OBJECT_PUT, OBJECT_DELETE,
        PRESIGNED_GET, PRESIGNED_PUT,
        HEAD_OBJECT, LIST_OBJECTS, LIST_BUCKETS
    }
}
