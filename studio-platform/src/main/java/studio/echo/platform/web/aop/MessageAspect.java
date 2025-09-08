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
 *      @file MessageAspect.java
 *      @date 2025
 *
 */

package studio.echo.platform.web.aop;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.service.I18n;
import studio.echo.platform.web.annotation.Message;
import studio.echo.platform.web.dto.ApiResponse;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class MessageAspect {

    private final I18n i18n;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(kr.go.greentogether.cmmn.web.annotation.Message)")
    public Object injectMessage(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Message message = method.getAnnotation(Message.class);
        log.trace("[AOP] @Message found on method: {} → key = {}", method.getName(), message.value());

        Object result = joinPoint.proceed();
        Object[] args = resolveSpelArguments(joinPoint, message.args());
        String localized = i18n.get(message.value(), args);
        // case 1: directly returning ApiResponse
        if (result instanceof ApiResponse) {
            @SuppressWarnings("unchecked")
            ApiResponse<Object> resp = (ApiResponse<Object>) result;
            return ApiResponse.ok(localized, resp.getData());
        }

        // case 2: ResponseEntity<ApiResponse>
        if (result instanceof ResponseEntity) { 
            ResponseEntity<?> entity = (ResponseEntity<?>) result;
            Object body = entity.getBody();
            if (body instanceof ApiResponse) {
                @SuppressWarnings("unchecked")
                ApiResponse<Object> resp = (ApiResponse<Object>) body;
                ApiResponse<?> newResp = ApiResponse.ok(localized, resp.getData());
                return ResponseEntity.status(entity.getStatusCode())
                        .headers(entity.getHeaders())
                        .body(newResp);
            }
        }
        log.trace("[AOP] Returned object is not ApiResponse. Skipping message injection.");
        return result;
    }

    private Object[] resolveSpelArguments(ProceedingJoinPoint joinPoint, String[] spelExpressions) {
        if (spelExpressions == null || spelExpressions.length == 0)
            return new Object[0];

        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] paramValues = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], paramValues[i]);
        }

        return Stream.of(spelExpressions)
                .map(expr -> parser.parseExpression(expr).getValue(context))
                .toArray();
    }
}
