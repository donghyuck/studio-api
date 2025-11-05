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
 *      @file StudioApplication.java
 *      @date 2025
 *
 */
package studio.server;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.JpaProperties;
import studio.one.platform.service.I18n;
import studio.one.platform.util.jpa.EntityLogger;

/**
 * Main Application.
 * 
 * @author donghyuck, son
 * @since 2025-07-25
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-25  donghyuck, son: 최초 생성.
 *          </pre>
 */

@EnableAsync
@EnableScheduling
@EnableCaching
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableConfigurationProperties({ JpaProperties.class })
@Slf4j
public class StudioApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(StudioApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(StudioApplication.class, args);
    }

    @Bean
    CommandLineRunner userEntityLogger(JpaProperties props, EntityManagerFactory emf, ObjectProvider<I18n> i18n) {
        return args -> {
            if (!props.isPrintEntities())
                return;
            EntityLogger.log(emf, log, "STUDIO", i18n.getIfAvailable());
        };
    }

    @Bean
    AuthenticationManager tracingAuthenticationManager(AuthenticationConfiguration cfg) throws Exception {
        var delegate = cfg.getAuthenticationManager();
        return authentication -> {
            // 스택 로그
            org.slf4j.LoggerFactory.getLogger("AUTH_TRACE")
                    .warn("authenticate() called", new RuntimeException("AUTH_STACK"));
            return delegate.authenticate(authentication);
        };
    }
}
