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
 *      @file RealtimeStompProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;
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

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PropertyKeys.Main.PREFIX + ".realtime.stomp")
public class RealtimeStompProperties {

    /**
     * 기능 on/off (기본 true).
     */
    private boolean enabled = true;

    /**
     * STOMP 엔드포인트 경로.
     */
    private String endpoint = "/ws";

    /**
     * APP destination prefix (클라이언트 -> 서버).
     */
    private String appDestinationPrefix = "/app";

    /**
     * Topic prefix (서버 -> 클라이언트 브로드캐스트).
     */
    private String topicPrefix = "/topic";

    /**
     * User destination prefix (1:1).
     */
    private String userPrefix = "/user";

    /**
     * 허용 Origin.
     */
    private List<String> allowedOrigins = List.of("*");

    /**
     * SockJS 사용 여부.
     */
    private boolean sockJs = true;

    /**
     * JWT 토큰으로 Principal을 주입할지 여부 (Authorization Bearer, JwtDecoder 필요).
     */
    private boolean jwtEnabled = false;

    /**
     * Redis Pub/Sub 연동 사용 여부.
     */
    private boolean redisEnabled = false;

    /**
     * Redis Pub/Sub 채널명.
     */
    private String redisChannel = "studio:realtime:events";

    /**
     * JWT 기반 Principal 생성 시 사용자 식별자 누락일 경우 연결 차단할지 여부 (기본: false, 즉 익명 Principal
     * 허용).
     */
    private boolean rejectAnonymous = false;
}
