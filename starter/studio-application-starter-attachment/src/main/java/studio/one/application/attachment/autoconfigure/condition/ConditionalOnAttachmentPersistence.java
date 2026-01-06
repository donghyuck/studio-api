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
 *      @file ConditionalOnAttachmentPersistence.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.features.condition.ConditionalOnFeaturePersistence;

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

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnFeaturePersistence(feature = "attachment")
public @interface ConditionalOnAttachmentPersistence {

    @AliasFor(annotation = ConditionalOnFeaturePersistence.class, attribute = "value")
    PersistenceProperties.Type value();
}
