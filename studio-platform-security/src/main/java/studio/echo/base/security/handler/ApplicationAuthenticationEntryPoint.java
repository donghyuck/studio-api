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
 *      @file ApplicationAuthenticationEntryPoint.java
 *      @date 2025
 *
 */


package studio.echo.base.security.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
 
/**
 * ApplicationAuthenticationEntryPoint
 * 
 * @author  donghyuck, son
 * @since 2025-08-25
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-25  donghyuck, son: 최초 생성.
 * </pre>
 */



@Slf4j
@RequiredArgsConstructor
public class ApplicationAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationErrorHandler errorHandler;

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.warn("Unauthorized access to {} - {}", request.getRequestURI(), authException.getMessage()); 
        errorHandler.unauthorized(request, response, (Object[]) null);
    }
}