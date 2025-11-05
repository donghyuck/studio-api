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
 *      @file AuthenticationErrorHandler.java
 *      @date 2025
 *
 */

package studio.one.base.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.error.ErrorType;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.web.dto.ProblemDetails;

/**
 * AuthenticationErrorHandler
 * 
 * @author donghyuck, son
 * @since 2025-08-25
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-25  donghyuck, son: 최초 생성.
 *          </pre>
 */

@RequiredArgsConstructor
@Slf4j
public class AuthenticationErrorHandler {

    private static final String PROBLEM_JSON = "application/problem+json;charset=UTF-8";

    private static final ErrorType BY_UNAUTHORIZED = ErrorType.of("error.security.unauthorized",
            HttpStatus.UNAUTHORIZED);
    private static final ErrorType BY_ACCESS_DENIED = ErrorType.of("error.security.access.denied",
            HttpStatus.FORBIDDEN);

    private final ObjectMapper objectMapper;
    private final I18n i18n;

    /** 401 응답 */
    public void unauthorized(HttpServletRequest request, HttpServletResponse response, Object... args) throws IOException {
        handle(request, response, BY_UNAUTHORIZED, args);
    }

    /** 403 응답 */
    public void accessDenied(HttpServletRequest request, HttpServletResponse response, Object... args) throws IOException {
        handle(request, response, BY_ACCESS_DENIED, args);
    }

    /** 공통 처리 */
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       ErrorType type, Object... args) throws IOException {

        HttpStatus status = Optional.ofNullable(type.getStatus()).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        String code = type.getId(); // e.g., "error.security.unauthorized"
        String detail = I18nUtils.safe(i18n).get(code, args);  
        String traceId = Optional.ofNullable(MDC.get("traceId")).orElse(null);
        ProblemDetails body = ProblemDetails.builder()
            .type("urn:error:" + code)                   // RFC 7807 'type' URI 관례
            .title(status.getReasonPhrase())
            .status(status.value())
            .detail(detail != null ? detail : code)      
            .instance(request.getRequestURI())
            .code(code)
            .traceId(traceId)
            .timestamp(OffsetDateTime.now())
            .build();
 
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(PROBLEM_JSON);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache"); 
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
