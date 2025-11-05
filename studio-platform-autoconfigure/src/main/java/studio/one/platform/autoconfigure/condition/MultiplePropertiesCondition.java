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
 *      @file MultiplePropertiesCondition.java
 *      @date 2025
 *
 */


package studio.one.platform.autoconfigure.condition;
import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author  donghyuck, son
 * @since 2025-07-23
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-23  donghyuck, son: 최초 생성.
 * </pre>
 */


@Slf4j
public class MultiplePropertiesCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!metadata.isAnnotated(ConditionalOnProperties.class.getName())) {
            return true;
        }

        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnProperties.class.getName());
        if (attributes == null) {
            log.warn("@ConditionalOnProperties annotation not found or misconfigured.");
            return true;  
        }

        String prefix = (String) attributes.get("prefix");
        Object[] props = (Object[]) attributes.get("value");

        Environment env = context.getEnvironment();

        for (Object p : props) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) p;

            String name = (String) prop.get("name");
            String expected = (String) prop.get("havingValue");
            boolean matchIfMissing = (Boolean) prop.get("matchIfMissing");

            String fullKey = prefix.isEmpty() ? name : prefix + "." + name;
            String actual = env.getProperty(fullKey);

            if (actual == null && !matchIfMissing)
                return false;
            if (actual != null && !actual.equalsIgnoreCase(expected))
                return false;
        }

        return true;
    }

}
