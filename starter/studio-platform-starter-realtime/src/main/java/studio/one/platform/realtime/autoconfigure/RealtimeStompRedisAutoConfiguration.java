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
 *      @file RealtimeStompRedisAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;
import studio.one.platform.realtime.stomp.domain.model.RealtimeEnvelope;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;
import studio.one.platform.realtime.stomp.redis.RealtimeRedisSubscriber;

@AutoConfiguration
@EnableConfigurationProperties(RealtimeStompProperties.class)
@ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
//@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".realtime",  name = "enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(prefix = "studio.realtime.stomp", name = "redis-enabled", havingValue = "true")
@RequiredArgsConstructor
public class RealtimeStompRedisAutoConfiguration {

    private final RealtimeStompProperties properties;

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, RealtimeEnvelope> realtimeRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, RealtimeEnvelope> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        Jackson2JsonRedisSerializer<RealtimeEnvelope> serializer =
                new Jackson2JsonRedisSerializer<>(RealtimeEnvelope.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        serializer.setObjectMapper(mapper);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer realtimeRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisTemplate<String, RealtimeEnvelope> redisTemplate,
            RealtimeMessagingService messagingService,
            ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new RealtimeRedisSubscriber(redisTemplate, messagingService, objectMapper),
                new PatternTopic(properties.getRedisChannel()));

        return container;
    }
}
