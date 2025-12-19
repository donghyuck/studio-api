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
 *      @file RealtimeMessagingService.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.stomp.messaging;

import studio.one.platform.constant.ServiceNames;
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

public interface RealtimeMessagingService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":realtime:messaging-service";

    /**
     * 현재 노드로 브로드캐스트.
     */
    void sendToTopic(String destination, RealtimePayload payload);

    /**
     * 현재 노드에서 특정 유저로 전송.
     */
    void sendToUser(String user, String destination, RealtimePayload payload);

    /**
     * Redis Pub/Sub(옵션) 으로 전파하고, 현재 노드에도 전송한다.
     */
    void publish(RealtimeEnvelope envelope);
}
