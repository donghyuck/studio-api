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
 *      @file RealtimeStompAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.realtime.autoconfigure;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;
import studio.one.platform.realtime.stomp.messaging.LocalStompMessagingService;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;
import studio.one.platform.realtime.stomp.security.RealtimeHandshakeHandler;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(RealtimeStompProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".realtime", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class RealtimeStompAutoConfiguration {

    protected static final String FEATURE_NAME = "Realtime";
    private final ObjectProvider<I18n> i18nProvider;
    private final RealtimeStompProperties properties;

    @Bean(name = RealtimeMessagingService.SERVICE_NAME)
    @ConditionalOnProperty(prefix = "studio.realtime.stomp", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RealtimeMessagingService realtimeMessagingService(
            org.springframework.messaging.simp.SimpMessagingTemplate template,
            ObjectProvider<BeanFactory> beanFactoryProvider) {
        BeanFactory beanFactory = beanFactoryProvider.getIfAvailable();
        Object redisTemplate = null;
        if (beanFactory != null && beanFactory.containsBean("realtimeRedisTemplate")) {
            redisTemplate = beanFactory.getBean("realtimeRedisTemplate");
        }
        return new LocalStompMessagingService(template, properties, redisTemplate);
    }

    @Bean(name = ServiceNames.Featrues.PREFIX + ":realtime:jwt-handshake-handler")
    @ConditionalOnMissingBean(RealtimeHandshakeHandler.class)
    @ConditionalOnProperty(prefix = "studio.realtime.stomp", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RealtimeHandshakeHandler handshakeHandler(
            ObjectProvider<studio.one.base.security.jwt.JwtTokenProvider> provider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                RealtimeStompAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(RealtimeHandshakeHandler.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new RealtimeHandshakeHandler(properties, provider.getIfAvailable());
    }

    @Bean(name = ServiceNames.Featrues.PREFIX + ":realtime:session-handshake-interceptor")
    @ConditionalOnProperty(prefix = "studio.realtime.stomp", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = ServiceNames.Featrues.PREFIX + ":realtime:session-handshake-interceptor")
    public HandshakeInterceptor realtimeHandshakeInterceptor() {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                RealtimeStompAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(HandshakeInterceptor.class, true),
                LogUtils.green(HttpSessionHandshakeInterceptor.class, true),
                LogUtils.red(State.CREATED.toString())));

        return new HttpSessionHandshakeInterceptor();
    }

}
