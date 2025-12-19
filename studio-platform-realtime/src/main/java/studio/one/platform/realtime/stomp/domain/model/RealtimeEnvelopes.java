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
 *      @file RealtimeEnvelopes.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.stomp.domain.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RealtimeEnvelopes {

    public static RealtimeEnvelope toUser(String userId, String destination, RealtimePayload payload) {
        RealtimeEnvelope env = base(RealtimeEnvelope.MessageType.USER, destination, payload);
        env.setUserId(userId);
        return env;
    }

    public static RealtimeEnvelope toTopic(String destination, RealtimePayload payload) {
        return base(RealtimeEnvelope.MessageType.TOPIC, destination, payload);
    }

    private static RealtimeEnvelope base(RealtimeEnvelope.MessageType type, String destination, RealtimePayload payload) {
        String payloadType = payload != null ? payload.getClass().getName() : null;
        return RealtimeEnvelope.builder()
                .type(type)
                .destination(destination)
                .payload(payload)
                .payloadType(payloadType)
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
