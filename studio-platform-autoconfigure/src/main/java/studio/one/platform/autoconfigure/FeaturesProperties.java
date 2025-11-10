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
 *      @file FeaturesProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.autoconfigure;

import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

/**
 *
 * @author donghyuck, son
 * @since 2025-08-11
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-11  donghyuck, son: 최초 생성.
 *          </pre>
 */

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX)
@Getter
@Setter
@Validated
public class FeaturesProperties {

    @Valid
    @NotNull
    private FeatureToggle applicationProperties = FeatureToggle.disabled();

    @Valid
    @NotNull
    private FeatureToggle imageService = FeatureToggle.disabled();

    @Valid
    @NotNull
    private FeatureToggle fileUpload = FeatureToggle.disabled();

    /** 사전에 정의하지 않은 동적 기능들 (선택) */
    @Valid
    private Map<@NotBlank String, @NotNull GenericFeature> others = Map.of();

    @Data
    @NoArgsConstructor // 기본 생성자
    @AllArgsConstructor // 전체 필드 생성자
    public static class FeatureToggle {

        private boolean enabled;
        private boolean failIfMissing = true;
        private PersistenceProperties persistence = new PersistenceProperties();


        public static FeatureToggle enabled() {
            return new FeatureToggle(true, true, PersistenceProperties.of());
        }

        public static FeatureToggle disabled() {
            return new FeatureToggle(false, true, PersistenceProperties.of());
        }

    }

    /** 사전에 정의하지 않은 동적 기능들 (선택) */
    public static class GenericFeature extends FeatureToggle {
        /** 임의 속성 보관 */
        private Map<@NotBlank String, @NotBlank String> attrs = Map.of();

        public Map<String, String> getAttrs() {
            return attrs;
        }

        public void setAttrs(Map<String, String> attrs) {
            this.attrs = attrs;
        }
    }

}
