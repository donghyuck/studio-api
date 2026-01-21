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
 *      @file JwtTokenProvider.java
 *      @date 2025
 *
 */

package studio.one.base.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.exception.JwtTokenException;
import studio.one.base.security.userdetails.ApplicationUserDetails;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.service.I18n;

/**
 * JWT(Json Web Token) 기반 인증 및 토큰 발급/검증을 담당하는 컴포넌트입니다.
 * <p>
 * <ul>
 * <li>액세스 토큰/리프레시 토큰 생성, 파싱, 검증 기능을 제공합니다.</li>
 * <li>토큰에서 사용자명, 권한 정보를 추출하고, Spring Security Authentication 객체로 변환합니다.</li>
 * <li>토큰 유효성 검사 및 예외 처리, HTTP 요청에서 토큰 추출 기능을 포함합니다.</li>
 * <li>시크릿 키, 만료시간 등은 {@link SecurityProperties}에서 주입받아 사용합니다.</li>
 * </ul>
 * </p>
 * 
 * @author donghyuck, son
 * @since 2025-08-25
 * @version 1.0
 *
 * <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-25  donghyuck, son: 최초 생성.
 * 2025-09-02  donghyuck, son: Bearer 토큰 파싱 보강
 * </pre>
 */

@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;
    private final String header;
    private final String claimAuthorities; 
    private final JwtParser parser;
    private final I18n i18n;

    /**
     * JWT Token prefix
     */
    public static final String BEARER_PREFIX = "Bearer";

    /**
     * Header key for JWT Token
     */
    public static final String HEADER_STRING = "Authorization";

    private static final String AUTHORITIES_KEY = "authorities";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";

    /**
     * JwtTokenProvider 생성자
     * 
     * @param secret     시크릿 키 문자열
     * @param issuer     발급자
     * @param accessTtl  액세스 토큰 만료시간
     * @param refreshTtl 리프레시 토큰 만료시간
     * @param clock      시간 제공자, null이면 시스템 UTC 시간 사용
     */
    public JwtTokenProvider(
            JwtConfig jwtConfig, 
            Clock clock,
            I18n i18n) {
        this(Keys.hmacShaKeyFor( jwtConfig.getSecret().getBytes() ), 
            jwtConfig.getIssuer(), 
            jwtConfig.getAccessTtl(), 
            jwtConfig.getRefreshTtl(),
            jwtConfig.getHeader(),
            jwtConfig.getClaimAuthorities(),
            clock, 
            i18n);
    }

    /**
     * JwtTokenProvider 생성자
     * 단일 책임 원칙에 따라 SecretKey 객체를 직접 주입받는 것을 권장.
     * 
     * @param secret
     * @param issuer
     * @param accessTtl
     * @param refreshTtl
     * @param clock
     */
    public JwtTokenProvider(
            SecretKey secret,
            String issuer,
            Duration accessTtl,
            Duration refreshTtl,
            String header,
            String claimAuthorities,
            Clock clock,
            I18n i18n) {
        this.i18n = i18n;
        this.secretKey = Objects.requireNonNull(secret);
        this.issuer = Objects.requireNonNull(issuer);
        this.accessTtl = Objects.requireNonNull(accessTtl);
        this.refreshTtl = Objects.requireNonNull(refreshTtl);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.header = StringUtils.defaultString(header, HEADER_STRING); 
        this.claimAuthorities = StringUtils.defaultString(claimAuthorities, AUTHORITIES_KEY); 
        this.parser =  Jwts.parser()
                .verifyWith(secretKey)
                .build(); 
    }

    @PostConstruct
    public void init() {
        log.info(i18n.get("info.security.jwt.initialized", issuer, accessTtl, refreshTtl));
    }

    public int getMaxAgeForRefreshTtl() {
        if (refreshTtl == null)
            return -1; // 세션 쿠키
        if (refreshTtl.isNegative())
            return -1; // 세션 쿠키
        long s = refreshTtl.getSeconds(); // Java 11
        if (s > Integer.MAX_VALUE)
            return Integer.MAX_VALUE; // 상한
        return (int) s;
    }

    public long getRefreshTtlMs(){
        return refreshTtl.toMillis();
    }

    public String generateToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        Long userId = resolveUserId(authentication);
        String username = resolveUsername(authentication);
        Instant now = clock.instant();
        Instant exp = now.plus(accessTtl);
        var builder = Jwts.builder()
                .subject(StringUtils.defaultIfBlank(authentication.getName(), username))
                .claim(claimAuthorities, authorities)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256);
        if (userId != null) {
            builder.claim(CLAIM_USER_ID, userId);
        }
        if (StringUtils.isNotBlank(username)) {
            builder.claim(CLAIM_USERNAME, username);
        }
        return builder.compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        Instant now = clock.instant();
        Instant exp = now.plus(refreshTtl);
        Long userId = resolveUserId(authentication);
        String username = resolveUsername(authentication);
        var builder = Jwts.builder()
                .subject(StringUtils.defaultIfBlank(authentication.getName(), username))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256);
        if (userId != null) {
            builder.claim(CLAIM_USER_ID, userId);
        }
        if (StringUtils.isNotBlank(username)) {
            builder.claim(CLAIM_USERNAME, username);
        }
        return builder.compact();
    }

    public Authentication getAuthentication(String token, UserDetailsService userDetailsService, boolean refresh) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        String subject = StringUtils.defaultIfBlank(claims.getSubject(), extractUsername(claims));
        log.debug("claims subject {} ", subject);
        if (StringUtils.isBlank(subject)) {
            throw JwtTokenException.invalid(token);
        }
        // Jwts.parser().verifyWith(secretKey).build()
        UserDetails details = userDetailsService.loadUserByUsername(subject);
        Collection<? extends GrantedAuthority> authorities = null;
        if (refresh) {
            // 리프레시 토큰은 권한 정보가 없으므로 빈 컬렉션 사용
            authorities = details.getAuthorities();
        } else {
            // 액세스 토큰은 권한 정보 포함
            String raw = String.valueOf(claims.get(claimAuthorities));
            authorities = Arrays.stream(raw.split(","))
                    .filter(s -> !s.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return new UsernamePasswordAuthenticationToken(details, "", authorities);
    }

    public String getUsername(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return StringUtils.defaultIfBlank(claims.getSubject(), extractUsername(claims));
        } catch (JwtException | IllegalArgumentException e) {
            throw JwtTokenException.invalid(token);
        }
    }

    private Long resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApplicationUserDetails<?> aud) {
            return aud.getUserId();
        }
        if (principal instanceof ApplicationPrincipal ap) {
            return ap.getUserId();
        }
        return null;
    }

    private String resolveUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApplicationUserDetails<?> aud) {
            return aud.getUsername();
        }
        if (principal instanceof ApplicationPrincipal ap) {
            return ap.getUsername();
        }
        return authentication.getName();
    }

    private String extractUsername(Claims claims) {
        Object value = claims.get(CLAIM_USERNAME);
        if (value == null) {
            value = claims.get("username");
        }
        return (value != null) ? String.valueOf(value) : null;
    }

    public boolean validateToken(String token) {
        log.debug("validate token<{}>", token);
        try {
            parser.parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw JwtTokenException.invalid(token);
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            throw JwtTokenException.expired(token);
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            throw JwtTokenException.invalid(token);
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            return false;
        }
    }

    public String resolveToken(HttpServletRequest request) {
        String headerVal = request.getHeader(header);
        if (StringUtils.isBlank(headerVal)) return null;
        if (StringUtils.startsWithIgnoreCase(headerVal, BEARER_PREFIX)) {
            String token = headerVal.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.isNotBlank(token) ? token : null;
        }
        return null;
    }
 
}
