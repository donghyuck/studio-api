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
 *      @file I18nKeys.java
 *      @date 2025
 *
 */

package studio.one.platform.autoconfigure;

import lombok.NoArgsConstructor;

/**
 * 오토컨피그 전용 메시지 키 정의 클래스
 * 
 * @author donghyuck, son
 * @since 2025-08-27
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-27  donghyuck, son: 최초 생성.
 *          </pre>
 */

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class I18nKeys {
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class AutoConfig {

        public static final String INFO  = "info.";
        public static final String DEBUG  = "debug.";
        public static final String WARN  = "warn.";
        public static final String ERROR  = "error.";
        public static final class Feature {

            private static final String P = "autoconfig.feature.";
            public static final String ENABLED = P + "enabled";
            public static final String DISABLED = P + "disabled";

            public static final class Service {
                private static final String P = Feature.P + "service.";
                private static final String ENABLED = P + "enabled";
                public static final String DISABLED = P + "disabled";
                public static final String DETAILS = P + "details";
                public static final String DETAILS_2 = P + "details2";
                public static final String INIT = P + "init";
                public static final String DEPENDS_ON = P + "depends-on";
            }

            public static final class EndPoint {
                private static final String P = Feature.P + "endpoint.";
                public static final String REGISTERED = P + "registered";
            }

            public static final class EntityScan {
                private static final String P = Feature.P + "entity-scan.";
                public static final String PREPARING = P + "preparing";
                public static final String CONFIG = P + "config";
                public static final String START = P + "start";
                public static final String FINISH = P + "finish";
                public static final String NONE = P + "packages.none";
                public static final String PACKAGES = P + "packages";
            }
        }
    }

}
