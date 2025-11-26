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
 *      @file OnAttachmentPersistenceCondition.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.PersistenceProperties.Type;
import studio.one.platform.constant.PropertyKeys;

/**
 *
 * @author  donghyuck, son
 * @since 2025-11-26
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-26  donghyuck, son: 최초 생성.
 * </pre>
 */

class OnAttachmentPersistenceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnAttachmentPersistence.class.getName());
        Type expected = (Type) attributes.get("value");
        Type actual = resolve(context.getEnvironment());
        if (actual == expected) {
            return ConditionOutcome.match("Attachment persistence matched " + expected);
        }
        return ConditionOutcome.noMatch("Attachment persistence was " + actual + ", expected " + expected);
    }

    private Type resolve(Environment env) {
        Type configured = parse(env.getProperty(PropertyKeys.Features.PREFIX + ".attachment.persistence"));
        if (configured != null) {
            return configured;
        }
        Type global = parse(env.getProperty(PropertyKeys.Persistence.PREFIX + ".type"));
        return global != null ? global : Type.jpa;
    }

    private Type parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Type.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
