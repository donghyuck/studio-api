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
 *      @file AbstractTokenController.java
 *      @date 2025
 *
 */

package studio.one.base.security.web.controller;

import java.time.Duration;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.service.I18n;
/**
 *
 * @author  donghyuck, son
 * @since 2025-12-05
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-05  donghyuck, son: 최초 생성.
 * </pre>
 */

@Slf4j
public abstract class AbstractTokenController {

    protected void addRefreshCookie(I18n i18n, JwtTokenProvider jwtTokenProvider, HttpServletResponse response,
            String raw) {
        String cookieName = "refresh_token";
        Integer maxAgeSec = jwtTokenProvider.getMaxAgeForRefreshTtl(); // -1 면 세션 쿠키
        ResponseCookie.ResponseCookieBuilder rb = ResponseCookie.from(cookieName, raw)
                .httpOnly(Boolean.TRUE)
                .secure(Boolean.TRUE) //
                .path("/")
                .sameSite("None"); // 크로스사이트 허용
        if (maxAgeSec != null && maxAgeSec >= 0) {
            rb.maxAge(Duration.ofSeconds(maxAgeSec));
        } // 세션 쿠키는 maxAge 미설정
        ResponseCookie rc = rb.build();
        response.addHeader("Set-Cookie", rc.toString());
        // 4) 로그 (i18n 인자 전달)
        log.info(i18n.get(
                "info.security.jwt.cookie.set",
                cookieName, Boolean.TRUE, Boolean.TRUE, (maxAgeSec == null ? "-" : maxAgeSec)));

    }

}
