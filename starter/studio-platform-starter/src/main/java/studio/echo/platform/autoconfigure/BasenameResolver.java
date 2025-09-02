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
 *      @file BasenameResolver.java
 *      @date 2025
 *
 */


package studio.echo.platform.autoconfigure;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import lombok.NoArgsConstructor;

/**
 * 리소스 패턴 또는 베이스네임으로부터 i18n 메시지 파일의 베이스네임을 추출하는 유틸리티 클래스
 * 
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 * </pre>
 */


@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class BasenameResolver {

/**
     * 예) 
     *  - .../i18n/user/messages.properties
     *  - .../i18n/user/messages_en.properties
     *  - .../i18n/user/messages_en_US.properties
     *  - .../i18n/user/messages_zh_Hant_TW.properties
     *  - .../META-INF/i18n/payment/messages_ko.properties
     *
     * 그룹(1): .../i18n/.../messages (베이스)
     */
    private static final Pattern LOCALE_FILE = Pattern.compile(
        "(.*/i18n/.*/messages)(?:_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*)?\\.properties$"
    );


    protected static List<String> discover(I18nProperties props) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        List<String> patternsOrBases = props.getResources();
        if (CollectionUtils.isEmpty(patternsOrBases)) {
            // 기본: 모듈 네임스페이스 방식
            patternsOrBases = List.of("classpath*:i18n/*/messages*.properties","classpath*:META-INF/i18n/*/messages*.properties" );
        }

        for (String entry : patternsOrBases) {
            if (looksLikePattern(entry)) {
                // 리소스 패턴 → 실제 파일들 → 베이스네임 추출
                for (Resource res : resolver.getResources(entry)) {
                    String base = toBasename(res);
                    if (base != null)
                        names.add(base);
                }
            } else {
                // 이미 베이스네임 형태로 들어온 경우 (e.g. classpath:/i18n/user/messages)
                names.add(entry);
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean looksLikePattern(String s) {
        // *, ? 또는 .properties 로 끝나면 패턴/파일로 간주
        return s.contains("*") || s.contains("?") || s.endsWith(".properties");
    }

   /**
     * jar:file:...!/i18n/user/messages_ko.properties → classpath:/i18n/user/messages
     * file:/.../build/resources/main/i18n/user/messages.properties → classpath:/i18n/user/messages
     */
    private static String toBasename(Resource res) throws IOException {
        URL url = res.getURL();
        String raw = url.toString().replace('\\', '/');
        String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8.name());

        Matcher m = LOCALE_FILE.matcher(decoded);
        if (!m.find()) return null;

        String prefix = m.group(1); // .../i18n/.../messages

        // jar URL → !/ 이후 경로만 취해 classpath:/ 접두
        int bang = prefix.indexOf("!/");
        if (bang >= 0) {
            String inside = prefix.substring(bang + 2); // i18n/.../messages
            return "classpath:/" + inside;
        }

        // 일반 file URL → /i18n/부터 잘라 classpath:/ 접두
        int i18nIdx = prefix.indexOf("/i18n/");
        if (i18nIdx >= 0) {
            return "classpath:" + prefix.substring(i18nIdx); // classpath:/i18n/.../messages
        }

        // 혹시 META-INF/i18n 만 보이는 경우도 방어
        int metaI18nIdx = prefix.indexOf("META-INF/i18n/");
        if (metaI18nIdx >= 0) {
            return "classpath:/" + prefix.substring(metaI18nIdx); // classpath:/META-INF/i18n/.../messages
        }

        // 최후의 보정: 마지막 /i18n/ 기준 시도 (없다면 null로)
        int last = prefix.lastIndexOf("/i18n/");
        if (last >= 0) {
            return "classpath:" + prefix.substring(last);
        }
        return null;
    }
}
