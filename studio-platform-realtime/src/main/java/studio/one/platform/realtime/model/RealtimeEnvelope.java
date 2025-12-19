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
 *      @file RealtimeEnvelope.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 표준 실시간 메시지 래퍼.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeEnvelope {
    /**
     * 브로커 전송 대상 (예: /mail/sync).
     */
    private String destination;

    /**
     * 사용자 ID (USER 타입일 때 사용).
     */
    private String userId;

    /**
     * 메시지 타입 (TOPIC 브로드캐스트 또는 USER 지정 전송).
     */
    private MessageType type;

    /**
     * 실제 페이로드.
     */
    private Object payload;

    /**
     * 페이로드 타입(정규 클래스명). 역직렬화 시 타입 안정성을 위해 사용.
     */
    private String payloadType;

    /**
     * 생성 시각.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 추적 ID(선택).
     */
    private String traceId;

    public enum MessageType {
        TOPIC, USER
    }
}
