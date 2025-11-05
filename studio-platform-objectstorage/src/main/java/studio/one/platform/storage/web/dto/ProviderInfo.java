package studio.one.platform.storage.web.dto;

import java.util.List;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderInfo {

    private String name;

    private String type;

    /** 활성화 여부 (properties 기준) */
    private boolean enabled;

    /** 간단 상태: enabled/disabled/unknown */
    private String status;

    /** 헬스상태: ok/fail/unknown */
    private Health health;

    /** 리전(있으면) */
    private String region;

    /** 엔드포인트(마스킹) */
    private String endpointMasked;

    /** OCI namespace, compartment (있으면) — 민감할 수 있어 마스킹/옵션화 권장 */
    private String ociNamespace;

    private String ociCompartmentMasked;

    /** 파일시스템 root(마스킹) */
    private String fsRootMasked;

    /** S3 옵션 */
    private Boolean s3PathStyle;

    private Boolean s3PresignerEnabled;

    /** 이 프로바이더가 제공하는 기능 */
    private List<Capability> capabilities;

    /** 선택: 표시용 메타 (운영 화면에서 설명/태그용) */
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
