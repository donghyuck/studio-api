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
 *      @file JasyptHttpController.java
 *      @date 2025
 *
 */
package studio.echo.platform.autoconfigure.jasypt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetAddress;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
 
import org.jasypt.encryption.StringEncryptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static java.util.Objects.requireNonNullElse;
 
import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.component.State;
import studio.echo.platform.constant.MessageCodes;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.autoconfigure.jasypt.JasyptProperties.JasyptHttpEndpointProperties;
import studio.echo.platform.util.LogUtils;

/**
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



@RestController
@RequestMapping("${"+ PropertyKeys.Features.Jasypt.HTTP + ".base-path:/internal/jasypt}")
@Validated
@Slf4j
@RequiredArgsConstructor
class JasyptHttpController {

    private final StringEncryptor encryptor;
    private final JasyptHttpEndpointProperties props;
    private final I18n i18n;

    private static final Pattern ENC_WRAP = Pattern.compile("^ENC\\((.*)\\)$", Pattern.DOTALL);


     /** 시작 시 적용 설정을 요약 출력(민감정보 제외) */
    @PostConstruct
    void logHttpEndpointConfig() {
        // 필요 시 토글하고 싶으면 props에 logOnStartup 같은 boolean을 추가해 조건 걸어도 됩니다.
        if (!log.isInfoEnabled()) return;
        
        log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true), LogUtils.red(State.CREATED.toString())));

        final String tokenSource =
            !props.isRequireToken() ? "disabled"
              : (StringUtils.hasText(props.getTokenValue())
                   ? "property(studio.features.jasypt.http.token-value)"
                   : "env(" + props.getTokenEnv() + ")");

        log.info("[{}] enabled={}, basePath='{}', requireToken={}, tokenSource={}, allowLocalOnly={}",
                LogUtils.blue("Jasypt-HTTP"),
                props.isEnabled(),
                requireNonNullElse(props.getBasePath(), "/internal/jasypt"),
                props.isRequireToken(),
                tokenSource,
                true // 컨트롤러 내부 isLocal()로 loopback만 허용
        );
    }

    /** 단건 암호화 */
    @PostMapping(path = "/encrypt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> encrypt(@Valid @RequestBody CryptoRequest body, HttpServletRequest req) {
        if (!isLocal(req)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!isAuthorized(req)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        // 민감값 로깅 금지!
        String cipher = encryptor.encrypt(body.getValue());
        return ResponseEntity.ok("ENC(" + cipher + ")");
    }

    /** 단건 복호화 (ENC(...) 허용) */
    @PostMapping(path = "/decrypt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> decrypt(@Valid @RequestBody CryptoRequest body, HttpServletRequest req) {
        if (!isLocal(req)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!isAuthorized(req)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String text = body.getValue();
        var m = ENC_WRAP.matcher(text);
        if (m.matches()) text = m.group(1);
        String plain = encryptor.decrypt(text);
        // 평문은 응답 본문으로만, 로깅 금지!
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", UTF_8))
                .body(plain);
    }

    @Data
    public static class CryptoRequest {
        @NotBlank
        private String value;
    }

    /** 로컬 접근만 허용 (프록시 헤더 불신) */
    private boolean isLocal(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress();
        } catch (Exception ignored) {
            return "127.0.0.1".equals(ip) || "::1".equals(ip);
        }
    }

    /** 토큰 검증 (Header: X-JASYPT-TOKEN) */
    private boolean isAuthorized(HttpServletRequest req) {
        if (!props.isRequireToken()) return true;
        String provided = req.getHeader("X-JASYPT-TOKEN");
        if (provided == null || provided.isEmpty()) return false;
        String expected = props.getTokenValue();
        if (expected == null || expected.isEmpty()) {
            expected = System.getenv(props.getTokenEnv());
        }
        return expected != null && expected.equals(provided);
    }
}
