/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file StorageProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.storage.autoconfigure;
import java.util.Map;
import java.util.HashMap;

import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.URL;

import studio.one.platform.constant.PropertyKeys;

import lombok.Data;

/**
 *
 * @author donghyuck, son
 * @since 2025-11-03
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-03  donghyuck, son: 최초 생성.
 *          </pre>
 */

@ConfigurationProperties(prefix = PropertyKeys.Cloud.PREFIX + ".storage")
@Data
@Validated
public class StorageProperties {

    /**
     * 멀티 프로파이더 providers.<id>.* 형태 지원.
     */
    private Map<String, Provider> providers = new HashMap<>();

    private Web web = new Web();

    @Data
    public static class Web {
        private boolean enabled = false;
        private String endpoint = "/api/mgmt/objectstorage";
    }

    @Data
    public static class Provider {

        private boolean enabled = false;
        
        private String type = "s3"; // s3, fs, oci
        
        private String region;

        @URL(message = "s3.endpoint는 유효한 URL이어야 합니다") 
        private String endpoint; // s3 호환만
        
        private Credentials credentials = new Credentials();
        
        private S3 s3 = new S3();
        
        private Oci oci = new Oci();
        
        private Fs fs = new Fs();

        @Data
        public static class S3 {
            private Boolean pathStyle; // NCP/MinIO/OCI= true 권장
            private Boolean presignerEnabled = true;
            private Integer apiTimeoutMs;
            private Integer connectTimeoutMs;
        }

        @Data
        public static class Oci {
            /** S3-호환은 endpoint에 {namespace} 포함 필요 → 템플릿 치환용 */
            private String namespace; // ex) mytenantns
            /** 네이티브에서 주로 사용(버킷 관리 등) */
            private String compartmentId;
            // (네이티브 인증 옵션들… 필요 시 확장)
        }

        @Data
        public static class Fs {
            private String root; // 로컬 루트 디렉터리
            private String publicBaseUrl; // presigned 대체용 접근 URL(선택)
        }
    }

    @Data
    public static class Credentials {
        private String accessKey;
        private String secretKey;
        private String sessionToken;
    }
}
