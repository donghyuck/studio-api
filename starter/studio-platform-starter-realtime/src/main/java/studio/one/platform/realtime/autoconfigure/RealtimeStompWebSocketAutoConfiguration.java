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
 *      @file RealtimeStompWebSocketAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;
import studio.one.platform.realtime.stomp.security.RealtimeHandshakeHandler;

@AutoConfiguration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeStompProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX  + ".realtime", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class RealtimeStompWebSocketAutoConfiguration implements WebSocketMessageBrokerConfigurer {
    private final RealtimeStompProperties properties;
    private final RealtimeHandshakeHandler handshakeHandler;
    private final List<HandshakeInterceptor> handshakeInterceptors;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(properties.getTopicPrefix(), properties.getUserPrefix());
        registry.setApplicationDestinationPrefixes(properties.getAppDestinationPrefix());
        registry.setUserDestinationPrefix(properties.getUserPrefix());
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint(properties.getEndpoint())
                .addInterceptors(handshakeInterceptors.toArray(new HandshakeInterceptor[0]))
                .setHandshakeHandler(handshakeHandler);
        if (properties.getAllowedOrigins() != null) {
            endpoint.setAllowedOrigins(properties.getAllowedOrigins().toArray(new String[0]));
        }
        if (properties.isSockJs()) {
            endpoint.withSockJS();
        }
    }
}
