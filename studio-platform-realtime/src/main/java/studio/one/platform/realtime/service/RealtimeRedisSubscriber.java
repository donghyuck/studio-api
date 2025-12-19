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
 *      @file RealtimeRedisSubscriber.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.service;

import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.model.RealtimeEnvelope;
import studio.one.platform.realtime.model.RealtimePayload;

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
public class RealtimeRedisSubscriber implements MessageListener {

    private final RedisTemplate<String, RealtimeEnvelope> redisTemplate;
    private final RealtimeMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeEnvelope envelope = (RealtimeEnvelope) redisTemplate.getValueSerializer().deserialize(message.getBody());
            if (envelope != null && envelope.getDestination() != null) {
                Object payload = envelope.getPayload();
                if (payload instanceof Map && envelope.getPayloadType() != null) {
                    try {
                        Class<?> clazz = Class.forName(envelope.getPayloadType());
                        if (!RealtimePayload.class.isAssignableFrom(clazz)) {
                            log.warn("Skip realtime message: payload type {} does not implement RealtimePayload", clazz.getName());
                            return;
                        }
                        payload = objectMapper.convertValue(payload, clazz);
                        envelope.setPayload(payload);
                    } catch (ClassNotFoundException cnf) {
                        log.debug("Payload type not found: {}", envelope.getPayloadType());
                        return;
                    } catch (IllegalArgumentException iae) {
                        log.warn("Payload conversion failed to {}: {}", envelope.getPayloadType(), iae.getMessage());
                        return;
                    }
                }
                if (!(payload instanceof RealtimePayload typedPayload)) {
                    log.warn("Skip realtime message: payload is not RealtimePayload. destination={}", envelope.getDestination());
                    return;
                }
                if (envelope.getType() == RealtimeEnvelope.MessageType.USER && envelope.getUserId() != null) {
                    messagingService.sendToUser(envelope.getUserId(), envelope.getDestination(), typedPayload);
                } else {
                    messagingService.sendToTopic(envelope.getDestination(), typedPayload);
                }
            }
        } catch (SerializationException | ClassCastException ex) {
            log.warn("Realtime redis subscriber deserialization failed: {}", ex.getMessage());
        } catch (Exception ex) {
            log.debug("Realtime redis subscriber failed: {}", ex.getMessage());
        }
    }
}
