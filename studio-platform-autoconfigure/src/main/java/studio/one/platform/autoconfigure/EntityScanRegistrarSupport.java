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
 *      @file EntityScanRegistrarSupport.java
 *      @date 2025
 *
 */


package studio.one.platform.autoconfigure;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * EntityScanRegistrarSupport는 EntityScanPackages를 사용하여
 * 엔티티 스캔 패키지를 등록하는 BeanDefinitionRegistryPostProcessor입니다.
 * 
 * @author  donghyuck, son
 * @since 2025-08-14
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-14  donghyuck, son: 최초 생성.
 * </pre>
 */


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityScanRegistrarSupport {
     
    public static BeanDefinitionRegistryPostProcessor entityScanRegistrar(String propertyKey, String... defaults) {
        return new Registrar(propertyKey, defaults, null);
    }

    public static BeanDefinitionRegistryPostProcessor entityScanRegistrar(
            String includeKey,
            String[] defaults,
            String excludeKey) {
        return new Registrar(includeKey, defaults, excludeKey);
    }

    private static final class Registrar
            implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {

        private final String key;
        private final String[] defaults;
        private final String excludeKey;
        private Environment env;

        Registrar(String key, String[] defaults, String excludeKey) {
            this.key = key;
            this.defaults = defaults != null ? defaults : new String[0];
            this.excludeKey = excludeKey;
        }

        @Override public void setEnvironment(Environment environment) { this.env = environment; }
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            Set<String> pkgs = resolvePackages(env, key, defaults);
            if (excludeKey != null) {
                Set<String> excludes = resolvePackages(env, excludeKey, new String[0]);
                if (!excludes.isEmpty()) {
                    pkgs.removeIf(pkg -> matchesAny(pkg, excludes));
                }
            }
            if (!pkgs.isEmpty()) {
                // 여러 모듈에서 호출돼도 누적 등록됨
                EntityScanPackages.register(registry, pkgs.toArray(new String[0]));
            }
        }
        @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

        private static Set<String> resolvePackages(@Nullable Environment env, String key, String[] defaults) {
            Set<String> out = new LinkedHashSet<>();
            if (env != null) {
                Binder binder = Binder.get(env);
                String[] arr = binder.bind(key, String[].class).orElse(null);
                if (arr != null) Stream.of(arr).forEach(s -> add(out, s));
                else {
                    String csv = binder.bind(key, String.class).orElse(null);
                    if (csv != null) Stream.of(csv.split(",")).forEach(s -> add(out, s));
                }
            }
            if (out.isEmpty() && defaults != null) Stream.of(defaults).forEach(s -> add(out, s));
            return out;
        }
        private static void add(Set<String> set, String s) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }

        private static boolean matchesAny(String value, Set<String> patterns) {
            for (String pattern : patterns) {
                try {
                    if (value.matches(pattern)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // 잘못된 정규식은 무시
                }
            }
            return false;
        }
    }
}
