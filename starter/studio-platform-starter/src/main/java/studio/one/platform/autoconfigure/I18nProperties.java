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
 *      @file I18nProperties.java
 *      @date 2025
 *
 */


package studio.one.platform.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

/**
 *
 * @author  donghyuck, son
 * @since 2025-08-11
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-11  donghyuck, son: 최초 생성.
 * </pre>
 */


@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PropertyKeys.I18n.PREFIX)
public class I18nProperties {

    private List<String> resources = new ArrayList<>();

    @NotBlank
    private String encoding = "UTF-8";

    @Min(-1)
    private int cacheSeconds= 0;

    /** 키가 없을 때 메시지 키 그대로 반환할지 */
    private boolean useCodeAsDefaultMessage = true;

    private boolean fallbackToSystemLocale = false;
}
