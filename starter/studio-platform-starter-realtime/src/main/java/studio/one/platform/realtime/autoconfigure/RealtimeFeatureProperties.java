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
 *      @file RealtimeFeatureProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.autoconfigure;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;

/**
 *
 * @author  donghyuck, son
 * @since 2025-12-19
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-19  donghyuck, son: 최초 생성.
 * </pre>
 */

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".realtime") 
@Getter
@Setter
@Validated
@EqualsAndHashCode(callSuper = true)
public class RealtimeFeatureProperties extends FeatureToggle {

}
