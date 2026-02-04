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
 *      @file AttachmentEndpointAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.controller.AttachmentController;

import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;
/**
 *
 * @author donghyuck, son
 * @since 2025-11-26
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-26  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Configuration
@AutoConfigureAfter(AttachmentAutoConfiguration.class)
@RequiredArgsConstructor
@EnableConfigurationProperties({ AttachmentFeatureProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
                + ".attachment.web", name = "enabled", havingValue = "true")
@ComponentScan(basePackageClasses = { AttachmentController.class })
@Slf4j
public class AttachmentEndpointAutoConfiguration {

        private final AttachmentFeatureProperties props;
        private final I18n i18n;

        @Bean
        @Lazy
        @ConditionalOnMissingBean(AttachmentController.class)
        AttachmentController attachmentController(AttachmentService attachmentService) {

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED,
                                AttachmentAutoConfiguration.FEATURE_NAME,
                                LogUtils.blue(AttachmentService.class, true),
                                LogUtils.blue(AttachmentController.class, true),
                                props.getWeb().getMgmtBasePath(),
                                "CRUD"));
                return new AttachmentController(attachmentService);
        }

}
