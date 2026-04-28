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
 *      @file AttachmentProperties.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
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

@ConfigurationProperties(prefix = AttachmentProperties.PREFIX)
@Getter
@Setter
@Validated
public class AttachmentProperties {

    public static final String PREFIX = PropertyKeys.Main.PREFIX + ".attachment";
    public static final String LEGACY_PREFIX = PropertyKeys.Features.PREFIX + ".attachment";

    public static final String MIGRATION_REASON =
            "Attachment runtime storage and thumbnail properties moved out of studio.features.attachment.";

    @Valid
    private Storage storage = new Storage();

    @Valid
    private Thumbnail thumbnail = new Thumbnail();

    public Storage storage(Environment environment, Logger log) {
        ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                environment,
                PREFIX + ".storage",
                LEGACY_PREFIX + ".storage",
                storage,
                log,
                MIGRATION_REASON);
        return storage;
    }

    public Thumbnail thumbnail(Environment environment, Logger log) {
        ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                environment,
                PREFIX + ".thumbnail",
                LEGACY_PREFIX + ".thumbnail",
                thumbnail,
                log,
                MIGRATION_REASON);
        return thumbnail;
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

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Thumbnail {
        private boolean enabled = true;
        private int defaultSize = 128;
        private String defaultFormat = "png";

        /**
         * Base directory to store thumbnail files. If empty, attachments/thumbnails
         * under the repository or tmp will be used.
         */
        private String baseDir;
        /**
         * Create directories on startup when true.
         */
        private boolean ensureDirs = true;
    }
}
