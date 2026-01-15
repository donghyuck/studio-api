/**
 *
 *      Copyright 2026
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
 *      @file WebAdviceAutoConfiguration.java
 *      @date 2026
 *
 */

package studio.one.platform.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.State;
import studio.one.platform.constant.MessageCodes;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;
import studio.one.platform.web.advice.GlobalExceptionHandler;
/**
 *
 * @author  donghyuck, son
 * @since 2026-01-15
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-01-15  donghyuck, son: 최초 생성.
 * </pre>
 */

@AutoConfiguration
@RequiredArgsConstructor
@Slf4j
public class WebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler( ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);          
        log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(GlobalExceptionHandler.class, true),
				LogUtils.red(State.CREATED.toString()))); 
        return new GlobalExceptionHandler(i18n);  
    }
}
