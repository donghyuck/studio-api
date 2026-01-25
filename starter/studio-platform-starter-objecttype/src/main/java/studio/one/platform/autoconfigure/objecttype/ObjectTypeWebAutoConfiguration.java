/**
 *
 *      Copyright 2026
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ObjectTypeWebAutoConfiguration.java
 *      @date 2026
 *
 */

package studio.one.platform.autoconfigure.objecttype;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.platform.objecttype.service.ObjectTypeAdminService;
import studio.one.platform.objecttype.service.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.web.controller.ObjectTypeController;
import studio.one.platform.objecttype.web.controller.ObjectTypeMgmtController;
import lombok.extern.slf4j.Slf4j;

@AutoConfiguration(after = ObjectTypeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "studio.features.objecttype.web", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class ObjectTypeWebAutoConfiguration {

    @Bean
    public ApplicationRunner objectTypeWebConfigDebugger(ApplicationContext context) {
        return args -> {
            String parentId = context.getParent() != null ? context.getParent().getId() : "none";
            String[] runtimeBeans = context.getBeanNamesForType(ObjectTypeRuntimeService.class, false, false);
            String[] adminBeans = context.getBeanNamesForType(ObjectTypeAdminService.class, false, false);
            log.info("ObjectTypeWebAutoConfiguration contextId={}, parentId={}, runtimeServiceBeans={}, adminServiceBeans={}",
                    context.getId(), parentId, java.util.Arrays.toString(runtimeBeans),
                    java.util.Arrays.toString(adminBeans));
            log.info("ObjectTypeWebAutoConfiguration contains runtimeServiceName={}, adminServiceName={}",
                    context.containsBean(ObjectTypeRuntimeService.SERVICE_NAME),
                    context.containsBean(ObjectTypeAdminService.SERVICE_NAME));
        };
    }

    @Configuration
    @ConditionalOnBean(name = ObjectTypeRuntimeService.SERVICE_NAME)
    @Import(ObjectTypeController.class)
    static class ObjectTypeRuntimeWebConfig {

    }

    @Configuration
    @ConditionalOnBean(name = ObjectTypeAdminService.SERVICE_NAME)
    @Import(ObjectTypeMgmtController.class)
    static class ObjectTypeAdminWebConfig {

    }
}
