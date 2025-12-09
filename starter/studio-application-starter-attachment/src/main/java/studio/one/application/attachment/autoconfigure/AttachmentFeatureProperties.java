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
 *      @file AttachmentFeatureProperties.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure;

import javax.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

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

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".attachment")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Validated
public class AttachmentFeatureProperties extends FeatureToggle {

    @Valid
    private Storage storage = new Storage();

    private Web web = new Web();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Web {
        private boolean enabled = false;
        private String basePath = "/api/mgmt/attachments";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Storage {
        public enum Type {
            filesystem,
            database
        }

        /**
         * Base directory to store attachment files. If empty, a default under the app
         * home will be used.
         */
        private String baseDir;
        /**
         * Create directories on startup when true.
         */
        private boolean ensureDirs = true;

        /**
         * Where to store attachment binaries. filesystem keeps files on disk,
         * database stores them in the configured attachment persistence (JPA/JDBC).
         */
        private Type type = Type.filesystem;

        /**
         * When storing in the database, optionally keep a local filesystem cache for
         * faster reads.
         */
        private boolean cacheEnabled = false;
    }
}
