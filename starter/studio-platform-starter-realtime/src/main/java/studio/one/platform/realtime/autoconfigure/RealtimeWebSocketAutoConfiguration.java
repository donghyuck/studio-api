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
 *      @file RealtimeWebSocketAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; 
import studio.one.platform.realtime.config.RealtimeStompProperties;
import studio.one.platform.realtime.model.RealtimeEnvelope;
import studio.one.platform.realtime.service.LocalStompMessagingService;
import studio.one.platform.realtime.service.RealtimeMessagingService;
import studio.one.platform.realtime.service.RealtimeRedisSubscriber;
import studio.one.platform.realtime.service.RealtimeHandshakeHandler;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeStompProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".realtime", name = "enabled", havingValue = "true", matchIfMissing = false) 
@RequiredArgsConstructor
@Slf4j
public class RealtimeWebSocketAutoConfiguration implements WebSocketMessageBrokerConfigurer {

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

    @Bean(name = RealtimeMessagingService.SERVICE_NAME )
    public RealtimeMessagingService realtimeMessagingService(
            org.springframework.messaging.simp.SimpMessagingTemplate template,
            ObjectProvider<org.springframework.data.redis.core.RedisTemplate<String, RealtimeEnvelope>> redisTemplate) {
        return new LocalStompMessagingService( template, properties, redisTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "studio.realtime", name = "redis-enabled", havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
    public org.springframework.data.redis.core.RedisTemplate<String, RealtimeEnvelope> realtimeRedisTemplate(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
            org.springframework.data.redis.core.RedisTemplate<String, RealtimeEnvelope> template = new org.springframework.data.redis.core.RedisTemplate<>();
        
        template.setConnectionFactory(connectionFactory);

        org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<RealtimeEnvelope> serializer = new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>( RealtimeEnvelope.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        serializer.setObjectMapper(mapper);
        template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "studio.realtime", name = "redis-enabled", havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
    public org.springframework.data.redis.listener.RedisMessageListenerContainer realtimeRedisListenerContainer(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
            org.springframework.data.redis.core.RedisTemplate<String, RealtimeEnvelope> redisTemplate,
            RealtimeMessagingService messagingService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        org.springframework.data.redis.listener.RedisMessageListenerContainer container = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new RealtimeRedisSubscriber(redisTemplate, messagingService, objectMapper),
                new org.springframework.data.redis.listener.PatternTopic(properties.getRedisChannel()));
        return container;
    }

    @Bean(name = ServiceNames.Featrues.PREFIX + ":realtime:handshake-interceptor" )
    @ConditionalOnMissingBean(RealtimeHandshakeHandler.class)
    public RealtimeHandshakeHandler handshakeHandler( ObjectProvider<studio.one.base.security.jwt.JwtTokenProvider> provider) {
        return new RealtimeHandshakeHandler(properties, provider.getIfAvailable());
    }

    
    @Bean(name = ServiceNames.Featrues.PREFIX + ":realtime:handshake-interceptor")
    @ConditionalOnMissingBean( name = ServiceNames.Featrues.PREFIX + ":realtime:handshake-interceptor")
    public HandshakeInterceptor realtimeHandshakeInterceptor() {
 
        return new HttpSessionHandshakeInterceptor();
    }
}
