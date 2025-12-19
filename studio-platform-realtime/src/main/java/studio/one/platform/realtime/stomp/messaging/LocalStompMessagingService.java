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
 *      @file LocalStompMessagingService.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.stomp.messaging;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;
import studio.one.platform.realtime.stomp.domain.model.RealtimeEnvelope;
import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;

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

@Slf4j
@RequiredArgsConstructor
@Service
public class LocalStompMessagingService implements RealtimeMessagingService {

    private final SimpMessagingTemplate template;
    private final RealtimeStompProperties properties;
    private final RedisTemplate<String, RealtimeEnvelope> redisTemplate;

    @Override
    public void sendToTopic(String destination, RealtimePayload payload) {
        template.convertAndSend(properties.getTopicPrefix() + destination, payload);
    }

    @Override
    public void sendToUser(String user, String destination, RealtimePayload payload) {
        template.convertAndSendToUser(user, destination, payload);
    }

    @Override
    public void publish(RealtimeEnvelope envelope) {
        Object payloadObject = envelope.getPayload();
        if (!(payloadObject instanceof RealtimePayload payload)) {
            throw new IllegalArgumentException("RealtimeEnvelope payload 는 비어 있을 수 없습니다. DTO를 전달하세요.");
        }
        boolean redisRequested = properties.isRedisEnabled();
        boolean hasRedis = redisTemplate != null;

        // Redis 연계 시: 모든 노드(자기 자신 포함)는 Subscriber 경로로만 전송해 중복을 피한다.
        if (redisRequested && hasRedis) {
            redisTemplate.convertAndSend(properties.getRedisChannel(), envelope);
            return;
        }

        // Redis 설정이 true지만 빈이 없으면 로컬로 fallback
        if (redisRequested && !hasRedis) {
            log.warn("studio.realtime.redis-enabled=true 이지만 RedisTemplate 이 없어 로컬 전송으로 fallback 합니다.");
        }

        // Redis 비활성: 현재 노드로 직접 전송
        dispatchLocal(envelope, payload);
    }

    private void dispatchLocal(RealtimeEnvelope envelope, RealtimePayload payload) {
        if (envelope.getType() == RealtimeEnvelope.MessageType.USER && envelope.getUserId() != null) {
            sendToUser(envelope.getUserId(), envelope.getDestination(), payload);
        } else {
            sendToTopic(envelope.getDestination(), payload);
        }
    }
}
